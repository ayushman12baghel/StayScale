package com.stayscale.core.repository;

import com.stayscale.core.dto.CitySummaryResponse;
import com.stayscale.core.dto.HotelDetailResponse;
import com.stayscale.core.dto.HotelSearchCriteria;
import com.stayscale.core.dto.HotelSearchItemResponse;
import com.stayscale.core.dto.RoomTypeResponse;
import com.stayscale.core.pricing.DynamicPricingEngine;
import com.stayscale.core.pricing.DynamicPricingEngine.NightInventorySnapshot;
import com.stayscale.core.pricing.PriceQuoteResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access layer for hotel search, city summaries, and property details.
 *
 * <p>Why {@link NamedParameterJdbcTemplate} instead of Spring Data JPA entities?
 * Hotel availability search relies on date-partitioned aggregations, PostgreSQL JSONB
 * containment ({@code @>}), and explicit covering-index queries. Writing the SQL directly
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
    private final DynamicPricingEngine pricingEngine;
    private final ObjectMapper objectMapper;

    public HotelSearchRepository(
            NamedParameterJdbcTemplate jdbc,
            DynamicPricingEngine pricingEngine,
            ObjectMapper objectMapper
    ) {
        this.jdbc = jdbc;
        this.pricingEngine = pricingEngine;
        this.objectMapper = objectMapper;
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
     * Finds hotels that have enough rooms available on EVERY night of the requested stay,
     * evaluates live {@link DynamicPricingEngine} rules per qualifying room type, and
     * applies budget filtering, sorting, and pagination on the exact dynamic nightly price
     * so search cards always match the hotel detail page.
     */
    public SearchQueryResult searchHotels(HotelSearchCriteria criteria) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("checkIn", criteria.checkIn())
                .addValue("checkOut", criteria.checkOut())
                .addValue("totalNights", criteria.totalNights())
                .addValue("rooms", criteria.rooms())
                .addValue("guestsPerRoom", criteria.guestsPerRoom());

        // Dynamically append optional hotel-level filters while keeping every value parameterized.
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
        if (criteria.amenities() != null && !criteria.amenities().isEmpty()) {
            // PostgreSQL JSONB containment '@>' checks that h.amenities contains ALL requested tags.
            // It natively uses idx_hotels_amenities_gin and avoids '?' JDBC placeholder conflicts with '?&'.
            whereClause.append(" AND h.amenities @> CAST(:amenitiesJson AS jsonb) ");
            params.addValue("amenitiesJson", objectMapper.writeValueAsString(criteria.amenities()));
        }

        // Note: check_out is exclusive (>= checkIn AND < checkOut) because you don't sleep on checkout night.
        String sql = """
                WITH qualifying_rooms AS (
                    SELECT rt.id AS room_type_id,
                           rt.hotel_id,
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
                    GROUP BY rt.id, rt.hotel_id, rt.base_price_minor, rt.currency,
                             rt.free_cancellation, rt.breakfast_included
                    HAVING COUNT(*) = :totalNights
                )
                SELECT h.id AS hotel_id,
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
                       qr.room_type_id,
                       qr.base_price_minor,
                       qr.currency,
                       qr.free_cancellation,
                       qr.breakfast_included,
                       qr.min_available_rooms,
                       inv.stay_date,
                       inv.total_rooms,
                       inv.available_rooms
                FROM hotels h
                JOIN qualifying_rooms qr ON qr.hotel_id = h.id
                JOIN room_inventory_by_date inv
                  ON inv.room_type_id = qr.room_type_id
                 AND inv.stay_date >= :checkIn
                 AND inv.stay_date < :checkOut
                """ + whereClause + " ORDER BY h.id, qr.room_type_id, inv.stay_date ASC ";

        Map<UUID, HotelAccumulator> byHotel = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            UUID hotelId = rs.getObject("hotel_id", UUID.class);
            HotelAccumulator hotelAcc = byHotel.computeIfAbsent(hotelId, id -> {
                try {
                    return new HotelAccumulator(
                            id,
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
                            readTextArray(rs, "amenities_arr")
                    );
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });

            UUID roomId = rs.getObject("room_type_id", UUID.class);
            RoomAccumulator roomAcc = hotelAcc.rooms.computeIfAbsent(roomId, id -> {
                try {
                    return new RoomAccumulator(
                            rs.getLong("base_price_minor"),
                            rs.getString("currency"),
                            rs.getBoolean("free_cancellation"),
                            rs.getBoolean("breakfast_included"),
                            rs.getInt("min_available_rooms")
                    );
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
            });

            roomAcc.nights.add(new NightInventorySnapshot(
                    rs.getObject("stay_date", LocalDate.class),
                    rs.getInt("total_rooms"),
                    rs.getInt("available_rooms")
            ));
        });

        LocalDate today = LocalDate.now();
        int totalNights = criteria.totalNights();
        int roomsRequested = criteria.rooms();

        List<HotelSearchItemResponse> allMatching = new ArrayList<>();
        for (HotelAccumulator h : byHotel.values()) {
            long minNightly = Long.MAX_VALUE;
            long minTotal = Long.MAX_VALUE;
            String currency = "INR";
            int maxAvail = 0;
            boolean freeCancelAny = false;
            boolean breakfastAny = false;

            for (RoomAccumulator r : h.rooms.values()) {
                PriceQuoteResponse quote = pricingEngine.calculateQuote(
                        r.basePriceMinor, r.currency, roomsRequested, r.nights, today);
                if (quote.averageDynamicNightlyMinor() < minNightly) {
                    minNightly = quote.averageDynamicNightlyMinor();
                    minTotal = quote.totalStayPriceMinor();
                    currency = quote.currency();
                }
                if (r.minAvailableRooms > maxAvail) {
                    maxAvail = r.minAvailableRooms;
                }
                freeCancelAny |= r.freeCancellation;
                breakfastAny |= r.breakfastIncluded;
            }

            if (criteria.maxPriceMinor() != null && minNightly > criteria.maxPriceMinor()) {
                continue;
            }

            allMatching.add(new HotelSearchItemResponse(
                    h.id,
                    h.name,
                    h.city,
                    h.country,
                    h.neighborhood,
                    h.address,
                    h.latitude,
                    h.longitude,
                    h.landmarkName,
                    h.distanceToCenterKm,
                    h.starRating,
                    h.guestScore,
                    h.reviewCount,
                    h.propertyType,
                    h.imageUrl,
                    h.amenities,
                    minNightly,
                    minTotal,
                    currency,
                    totalNights,
                    maxAvail,
                    freeCancelAny,
                    breakfastAny
            ));
        }

        allMatching.sort(buildComparator(criteria.sort()));

        long totalElements = allMatching.size();
        int fromIdx = Math.min(criteria.page() * criteria.size(), allMatching.size());
        int toIdx = Math.min(fromIdx + criteria.size(), allMatching.size());
        return new SearchQueryResult(allMatching.subList(fromIdx, toIdx), totalElements);
    }

    /**
     * Loads full property metadata plus every room type's availability and live dynamic pricing.
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
        int totalNights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);
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

        // Fetch per-night snapshots across all room types of this hotel in one query so
        // DynamicPricingEngine can compute live surge/weekend/early-bird adjustments.
        String nightlySql = """
                SELECT rt.id AS room_type_id,
                       inv.stay_date,
                       inv.total_rooms,
                       inv.available_rooms
                FROM room_types rt
                JOIN room_inventory_by_date inv ON inv.room_type_id = rt.id
                WHERE rt.hotel_id = :hotelId
                  AND inv.stay_date >= :checkIn
                  AND inv.stay_date < :checkOut
                ORDER BY rt.id, inv.stay_date ASC
                """;

        Map<UUID, List<NightInventorySnapshot>> snapshotsByRoom = new HashMap<>();
        jdbc.query(nightlySql, roomParams, rs -> {
            UUID rtId = rs.getObject("room_type_id", UUID.class);
            snapshotsByRoom.computeIfAbsent(rtId, k -> new ArrayList<>())
                    .add(new NightInventorySnapshot(
                            rs.getObject("stay_date", LocalDate.class),
                            rs.getInt("total_rooms"),
                            rs.getInt("available_rooms")
                    ));
        });

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

        LocalDate today = LocalDate.now();
        List<RoomTypeResponse> roomTypes = jdbc.query(roomsSql, roomParams, (rs, rowNum) -> {
            UUID roomId = rs.getObject("id", UUID.class);
            long baseNightly = rs.getLong("base_price_minor");
            String currency = rs.getString("currency");
            List<NightInventorySnapshot> nights = snapshotsByRoom.getOrDefault(roomId, List.of());

            PriceQuoteResponse quote = nights.size() == totalNights
                    ? pricingEngine.calculateQuote(baseNightly, currency, rooms, nights, today)
                    : new PriceQuoteResponse(baseNightly, baseNightly, baseNightly * totalNights * rooms,
                    baseNightly * totalNights * rooms, currency, totalNights, rooms, List.of(), List.of());

            return new RoomTypeResponse(
                    roomId,
                    rs.getString("name"),
                    rs.getString("description"),
                    rs.getString("bed_type"),
                    rs.getInt("room_size_sqm"),
                    rs.getInt("max_guests"),
                    quote.averageDynamicNightlyMinor(),
                    quote.totalStayPriceMinor(),
                    currency,
                    rs.getInt("total_units"),
                    rs.getInt("available_rooms"),
                    rs.getBoolean("free_cancellation"),
                    rs.getBoolean("breakfast_included"),
                    rs.getString("image_url"),
                    quote
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

    private Comparator<HotelSearchItemResponse> buildComparator(String sort) {
        String key = sort == null ? "recommended" : sort.toLowerCase();
        return switch (key) {
            case "price_asc" -> Comparator.comparingLong(HotelSearchItemResponse::startingNightlyPriceMinor)
                    .thenComparing(Comparator.comparingDouble(HotelSearchItemResponse::guestScore).reversed());
            case "price_desc" -> Comparator.comparingLong(HotelSearchItemResponse::startingNightlyPriceMinor).reversed()
                    .thenComparing(Comparator.comparingDouble(HotelSearchItemResponse::guestScore).reversed());
            case "rating_desc" -> Comparator.comparingDouble(HotelSearchItemResponse::guestScore).reversed()
                    .thenComparing(Comparator.comparingInt(HotelSearchItemResponse::reviewCount).reversed());
            default -> Comparator.comparingDouble((HotelSearchItemResponse h) ->
                            h.guestScore() * 10.0 - (h.startingNightlyPriceMinor() / 150_000.0))
                    .reversed()
                    .thenComparing(Comparator.comparingInt(HotelSearchItemResponse::reviewCount).reversed());
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

    private static final class HotelAccumulator {
        final UUID id;
        final String name;
        final String city;
        final String country;
        final String neighborhood;
        final String address;
        final double latitude;
        final double longitude;
        final String landmarkName;
        final double distanceToCenterKm;
        final int starRating;
        final double guestScore;
        final int reviewCount;
        final String propertyType;
        final String imageUrl;
        final List<String> amenities;
        final Map<UUID, RoomAccumulator> rooms = new LinkedHashMap<>();

        HotelAccumulator(UUID id, String name, String city, String country, String neighborhood,
                         String address, double latitude, double longitude, String landmarkName,
                         double distanceToCenterKm, int starRating, double guestScore, int reviewCount,
                         String propertyType, String imageUrl, List<String> amenities) {
            this.id = id;
            this.name = name;
            this.city = city;
            this.country = country;
            this.neighborhood = neighborhood;
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
            this.landmarkName = landmarkName;
            this.distanceToCenterKm = distanceToCenterKm;
            this.starRating = starRating;
            this.guestScore = guestScore;
            this.reviewCount = reviewCount;
            this.propertyType = propertyType;
            this.imageUrl = imageUrl;
            this.amenities = amenities;
        }
    }

    private static final class RoomAccumulator {
        final long basePriceMinor;
        final String currency;
        final boolean freeCancellation;
        final boolean breakfastIncluded;
        final int minAvailableRooms;
        final List<NightInventorySnapshot> nights = new ArrayList<>();

        RoomAccumulator(long basePriceMinor, String currency, boolean freeCancellation,
                        boolean breakfastIncluded, int minAvailableRooms) {
            this.basePriceMinor = basePriceMinor;
            this.currency = currency;
            this.freeCancellation = freeCancellation;
            this.breakfastIncluded = breakfastIncluded;
            this.minAvailableRooms = minAvailableRooms;
        }
    }

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
