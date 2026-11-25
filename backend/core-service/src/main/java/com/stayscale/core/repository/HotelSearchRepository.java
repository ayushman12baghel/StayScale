package com.stayscale.core.repository;

import com.stayscale.core.dto.CitySummaryResponse;
import com.stayscale.core.dto.HotelDetailResponse;
import com.stayscale.core.dto.HotelSearchCriteria;
import com.stayscale.core.dto.HotelSearchItemResponse;
import com.stayscale.core.dto.RoomTypeResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for hotel search, city summaries, and property details.
 *
 * <p>Why {@link NamedParameterJdbcTemplate} instead of Spring Data JPA entities?
 * Hotel availability search relies on date-partitioned aggregations, PostgreSQL JSONB
 * operators ({@code ?&}), and explicit covering-index queries. Writing the SQL directly
 * prevents hidden N+1 queries and lets us verify index usage with {@code EXPLAIN ANALYZE}.
 */
@Repository
public class HotelSearchRepository {

    private static final Map<String, String> CITY_TAGLINES = Map.of(
            "Goa", "Golden beaches, cliffside sunsets & boutique resorts",
            "Mumbai", "Marine Drive skyline, Colaba heritage & coastal luxury",
            "Bengaluru", "Garden city retreats, design hotels & Indiranagar hubs",
            "Jaipur", "Pink sandstone palaces, courtyards & royal hospitality",
            "Udaipur", "Romantic lakefront havelis & Aravalli sunset views",
            "Manali", "Pine-forested alpine chalets & Himalayan peak views",
            "Kochi", "Backwater pool villas, lagoons & historic Fort Kochi",
            "Bangkok", "Chao Phraya sky pools, vibrant streets & luxury towers"
    );

    private final NamedParameterJdbcTemplate jdbc;

    public HotelSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Aggregates destination metadata (hotel count, cheapest starting rate, and map center)
     * for the home page destination cards and search dropdown.
     */
    public List<CitySummaryResponse> findCitySummaries() {
        String sql = """
                SELECT h.city,
                       h.country,
                       COUNT(DISTINCT h.id) AS hotel_count,
                       MIN(rt.base_price_minor) AS min_nightly_price_minor,
                       MIN(rt.currency) AS currency,
                       MIN(h.image_url) AS sample_image_url,
                       AVG(h.latitude) AS center_lat,
                       AVG(h.longitude) AS center_lng
                FROM hotels h
                JOIN room_types rt ON rt.hotel_id = h.id
                GROUP BY h.city, h.country
                ORDER BY hotel_count DESC, h.city ASC
                """;
        return jdbc.query(sql, (rs, rowNum) -> {
            String city = rs.getString("city");
            return new CitySummaryResponse(
                    city,
                    rs.getString("country"),
                    rs.getLong("hotel_count"),
                    rs.getLong("min_nightly_price_minor"),
                    rs.getString("currency"),
                    rs.getString("sample_image_url"),
                    CITY_TAGLINES.getOrDefault(city, "Curated stays & instant availability"),
                    rs.getDouble("center_lat"),
                    rs.getDouble("center_lng")
            );
        });
    }

    /**
     * Finds hotels that have enough rooms available on EVERY night of the requested stay.
     *
     * <p>How the two-stage CTE works:
     * <ol>
     *   <li>{@code available_rooms_agg}: filters daily inventory rows to {@code [checkIn, checkOut)}
     *       using the covering index {@code idx_inventory_date_avail_room}, then keeps only room types
     *       where {@code COUNT(*) = totalNights} (proving every single night has stock >= requested rooms).</li>
     *   <li>{@code hotel_agg}: collapses qualifying room types to one summary row per hotel
     *       (cheapest qualifying nightly rate, best availability, and perk flags).</li>
     * </ol>
     */
    public SearchQueryResult searchHotels(HotelSearchCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("checkIn", criteria.checkIn())
                .addValue("checkOut", criteria.checkOut())
                .addValue("totalNights", criteria.totalNights())
                .addValue("rooms", criteria.rooms())
                .addValue("guestsPerRoom", criteria.guestsPerRoom())
                .addValue("limit", criteria.size())
                .addValue("offset", criteria.page() * criteria.size());

        // Dynamically append optional sidebar filters while keeping every value parameterized (SQL-injection safe).
        StringBuilder whereClause = new StringBuilder(" WHERE 1=1 ");

        if (criteria.city() != null && !criteria.city().isBlank()) {
            // Matches our functional index idx_hotels_city_score on (LOWER(city), guest_score DESC).
            whereClause.append(" AND LOWER(h.city) = LOWER(:city) ");
            params.addValue("city", criteria.city().trim());
        }
        if (criteria.query() != null && !criteria.query().isBlank()) {
            whereClause.append(" AND (LOWER(h.name) LIKE :searchPattern OR LOWER(h.neighborhood) LIKE :searchPattern OR LOWER(h.landmark_name) LIKE :searchPattern OR LOWER(h.city) LIKE :searchPattern) ");
            params.addValue("searchPattern", "%" + criteria.query().trim().toLowerCase() + "%");
        }
        if (criteria.minStars() != null) {
            whereClause.append(" AND h.star_rating >= :minStars ");
            params.addValue("minStars", criteria.minStars());
        }
        if (criteria.minRating() != null) {
            whereClause.append(" AND h.guest_score >= :minRating ");
            params.addValue("minRating", criteria.minRating());
        }
        if (criteria.maxPriceMinor() != null) {
            whereClause.append(" AND ha.starting_nightly_price_minor <= :maxPriceMinor ");
            params.addValue("maxPriceMinor", criteria.maxPriceMinor());
        }
        if (criteria.amenities() != null && !criteria.amenities().isEmpty()) {
            // PostgreSQL '?&' checks that the JSONB array contains ALL requested amenity strings (uses GIN index).
            whereClause.append(" AND h.amenities ?& :amenitiesArr ");
            params.addValue("amenitiesArr", criteria.amenities().toArray(String[]::new));
        }

        // Note: check_out is exclusive (>= checkIn AND < checkOut) because you don't sleep on checkout night.
        // We take MAX(min_available_rooms) across room types so "Only 2 left!" only warns when EVERY eligible room type is scarce.
        String cte = """
                WITH available_rooms_agg AS (
                    SELECT rt.hotel_id,
                           rt.id AS room_type_id,
                           rt.base_price_minor,
                           rt.currency,
                           rt.free_cancellation,
                           rt.breakfast_included,
                           MIN(inv.available_rooms) AS min_available_rooms
                    FROM room_types rt
                    JOIN room_inventory_by_date inv ON inv.room_type_id = rt.id
                    WHERE inv.stay_date >= :checkIn
                      AND inv.stay_date < :checkOut
                      AND inv.available_rooms >= :rooms
                      AND rt.max_guests >= :guestsPerRoom
                    GROUP BY rt.hotel_id, rt.id, rt.base_price_minor, rt.currency,
                             rt.free_cancellation, rt.breakfast_included
                    HAVING COUNT(*) = :totalNights
                ),
                hotel_agg AS (
                    SELECT hotel_id,
                           MIN(base_price_minor) AS starting_nightly_price_minor,
                           MIN(currency) AS currency,
                           MAX(min_available_rooms) AS max_available_rooms,
                           BOOL_OR(free_cancellation) AS free_cancellation_available,
                           BOOL_OR(breakfast_included) AS breakfast_included_available
                    FROM available_rooms_agg
                    GROUP BY hotel_id
                )
                """;

        // Run a separate COUNT(*) with identical WHERE filters so totalElements & pagination remain accurate on any page.
        String countSql = cte + """
                SELECT COUNT(*)
                FROM hotels h
                JOIN hotel_agg ha ON ha.hotel_id = h.id
                """ + whereClause;

        Long total = jdbc.queryForObject(countSql, params, Long.class);
        long totalElements = total != null ? total : 0L;

        // Convert JSONB arrays into PostgreSQL text[] inline via ARRAY(SELECT jsonb_array_elements_text(...))
        // so JDBC maps them directly to String[] without coupling to a specific Jackson version.
        String orderBy = resolveOrderBy(criteria.sort());
        String dataSql = cte + """
                SELECT h.id,
                       h.name,
                       h.city,
                       h.country,
                       h.neighborhood,
                       h.address,
                       h.latitude,
                       h.longitude,
                       h.landmark_name,
                       h.distance_to_center_km,
                       h.star_rating,
                       h.guest_score,
                       h.review_count,
                       h.property_type,
                       h.image_url,
                       ARRAY(SELECT jsonb_array_elements_text(h.amenities)) AS amenities_arr,
                       ha.starting_nightly_price_minor,
                       ha.currency,
                       ha.max_available_rooms,
                       ha.free_cancellation_available,
                       ha.breakfast_included_available
                FROM hotels h
                JOIN hotel_agg ha ON ha.hotel_id = h.id
                """ + whereClause + " ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset ";

        int totalNights = criteria.totalNights();
        int roomsRequested = criteria.rooms();
        List<HotelSearchItemResponse> items = jdbc.query(dataSql, params, (rs, rowNum) -> {
            long nightly = rs.getLong("starting_nightly_price_minor");
            // Integer minor-unit multiplication avoids floating-point rounding bugs on currency totals.
            long totalStay = nightly * totalNights * roomsRequested;
            return new HotelSearchItemResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("city"),
                    rs.getString("country"),
                    rs.getString("neighborhood"),
                    rs.getString("address"),
                    rs.getDouble("latitude"),
                    rs.getDouble("longitude"),
                    rs.getString("landmark_name"),
                    rs.getDouble("distance_to_center_km"),
                    rs.getInt("star_rating"),
                    rs.getDouble("guest_score"),
                    rs.getInt("review_count"),
                    rs.getString("property_type"),
                    rs.getString("image_url"),
                    readTextArray(rs, "amenities_arr"),
                    nightly,
                    totalStay,
                    rs.getString("currency"),
                    totalNights,
                    rs.getInt("max_available_rooms"),
                    rs.getBoolean("free_cancellation_available"),
                    rs.getBoolean("breakfast_included_available")
            );
        });

        return new SearchQueryResult(items, totalElements);
    }

    /**
     * Loads full property metadata plus every room type's availability for the requested dates.
     *
     * <p>Unlike {@link #searchHotels}, this uses a {@code LEFT JOIN} on inventory so room types
     * that are sold out for the chosen dates still appear on the hotel page labeled "Sold out",
     * just like on Agoda.
     */
    public Optional<HotelDetailResponse> findHotelDetail(
            UUID hotelId,
            LocalDate checkIn,
            LocalDate checkOut,
            int guests,
            int rooms
    ) {
        int totalNights = (int) java.time.temporal.ChronoUnit.DAYS.between(checkIn, checkOut);
        MapSqlParameterSource hotelParams = new MapSqlParameterSource("hotelId", hotelId);

        String hotelSql = """
                SELECT id, name, city, country, neighborhood, address,
                       latitude, longitude, landmark_name, distance_to_center_km,
                       star_rating, guest_score, review_count, property_type,
                       description, image_url,
                       ARRAY(SELECT jsonb_array_elements_text(gallery_urls)) AS gallery_arr,
                       ARRAY(SELECT jsonb_array_elements_text(amenities)) AS amenities_arr
                FROM hotels
                WHERE id = :hotelId
                """;

        List<HotelHeaderRow> headers = jdbc.query(hotelSql, hotelParams, (rs, rowNum) -> new HotelHeaderRow(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("city"),
                rs.getString("country"),
                rs.getString("neighborhood"),
                rs.getString("address"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude"),
                rs.getString("landmark_name"),
                rs.getDouble("distance_to_center_km"),
                rs.getInt("star_rating"),
                rs.getDouble("guest_score"),
                rs.getInt("review_count"),
                rs.getString("property_type"),
                rs.getString("description"),
                rs.getString("image_url"),
                readTextArray(rs, "gallery_arr"),
                readTextArray(rs, "amenities_arr")
        ));

        if (headers.isEmpty()) {
            return Optional.empty();
        }
        HotelHeaderRow h = headers.getFirst();

        MapSqlParameterSource roomParams = new MapSqlParameterSource()
                .addValue("hotelId", hotelId)
                .addValue("checkIn", checkIn)
                .addValue("checkOut", checkOut)
                .addValue("totalNights", totalNights);

        // If any night in [checkIn, checkOut) is missing a row (COUNT < totalNights), treat available_rooms as 0.
        // Otherwise take MIN(available_rooms) across all stayed nights (the bottleneck night limits how many rooms can be booked).
        String roomsSql = """
                SELECT rt.id,
                       rt.name,
                       rt.description,
                       rt.bed_type,
                       rt.room_size_sqm,
                       rt.max_guests,
                       rt.base_price_minor,
                       rt.currency,
                       rt.total_units,
                       rt.free_cancellation,
                       rt.breakfast_included,
                       rt.image_url,
                       CASE
                           WHEN COUNT(inv.stay_date) = :totalNights THEN COALESCE(MIN(inv.available_rooms), 0)
                           ELSE 0
                       END AS available_rooms
                FROM room_types rt
                LEFT JOIN room_inventory_by_date inv
                       ON inv.room_type_id = rt.id
                      AND inv.stay_date >= :checkIn
                      AND inv.stay_date < :checkOut
                WHERE rt.hotel_id = :hotelId
                GROUP BY rt.id, rt.name, rt.description, rt.bed_type, rt.room_size_sqm,
                         rt.max_guests, rt.base_price_minor, rt.currency, rt.total_units,
                         rt.free_cancellation, rt.breakfast_included, rt.image_url
                ORDER BY rt.base_price_minor ASC
                """;

        List<RoomTypeResponse> roomTypes = jdbc.query(roomsSql, roomParams, (rs, rowNum) -> {
            long nightly = rs.getLong("base_price_minor");
            return new RoomTypeResponse(
                    rs.getObject("id", UUID.class),
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("bed_type"),
                    rs.getInt("room_size_sqm"),
                    rs.getInt("max_guests"),
                    nightly,
                    nightly * totalNights * rooms,
                    rs.getString("currency"),
                    rs.getInt("total_units"),
                    rs.getInt("available_rooms"),
                    rs.getBoolean("free_cancellation"),
                    rs.getBoolean("breakfast_included"),
                    rs.getString("image_url")
            );
        });

        return Optional.of(new HotelDetailResponse(
                h.id(),
                h.name(),
                h.city(),
                h.country(),
                h.neighborhood(),
                h.address(),
                h.latitude(),
                h.longitude(),
                h.landmarkName(),
                h.distanceToCenterKm(),
                h.starRating(),
                h.guestScore(),
                h.reviewCount(),
                h.propertyType(),
                h.description(),
                h.imageUrl(),
                h.galleryUrls(),
                h.amenities(),
                checkIn,
                checkOut,
                totalNights,
                guests,
                rooms,
                roomTypes
        ));
    }

    /**
     * Whitelist-maps user sort options to safe ORDER BY expressions (never interpolate raw user input).
     * Default "recommended" blends high guest score with lower price as a simple value score.
     */
    private String resolveOrderBy(String sort) {
        if (sort == null) {
            return "(h.guest_score * 10 - (ha.starting_nightly_price_minor / 150000.0)) DESC, h.review_count DESC";
        }
        return switch (sort.toLowerCase()) {
            case "price_asc" -> "ha.starting_nightly_price_minor ASC, h.guest_score DESC";
            case "price_desc" -> "ha.starting_nightly_price_minor DESC, h.guest_score DESC";
            case "rating_desc" -> "h.guest_score DESC, h.review_count DESC";
            default -> "(h.guest_score * 10 - (ha.starting_nightly_price_minor / 150000.0)) DESC, h.review_count DESC";
        };
    }

    private List<String> readTextArray(ResultSet rs, String col) throws SQLException {
        Array sqlArr = rs.getArray(col);
        if (sqlArr == null) {
            return List.of();
        }
        String[] values = (String[]) sqlArr.getArray();
        return values == null ? List.of() : Arrays.asList(values);
    }

    public record SearchQueryResult(List<HotelSearchItemResponse> items, long totalElements) {}

    private record HotelHeaderRow(
            UUID id,
            String name,
            String city,
            String country,
            String neighborhood,
            String address,
            double latitude,
            double longitude,
            String landmarkName,
            double distanceToCenterKm,
            int starRating,
            double guestScore,
            int reviewCount,
            String propertyType,
            String description,
            String imageUrl,
            List<String> galleryUrls,
            List<String> amenities
    ) {}
}
