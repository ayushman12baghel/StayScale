package com.stayscale.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Idempotent startup seeder that populates StayScale with realistic travel inventory.
 *
 * <p>Why a Java runner + SQL {@code generate_series} instead of a huge static SQL file?
 * <ul>
 *   <li>Dates are always relative to {@code CURRENT_DATE}, so searches "tomorrow" never expire.</li>
 *   <li>Re-running the app next week safely tops up new future days via {@code ON CONFLICT DO NOTHING}
 *       without touching existing inventory counts.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "stayscale.seed.enabled", havingValue = "true", matchIfMissing = true)
public class CatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final int daysAhead;

    public CatalogSeeder(
            NamedParameterJdbcTemplate jdbc,
            @Value("${stayscale.seed.days-ahead:90}") int daysAhead
    ) {
        this.jdbc = jdbc;
        this.daysAhead = daysAhead;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long hotelCount = jdbc.getJdbcTemplate().queryForObject("SELECT COUNT(*) FROM hotels", Long.class);
        if (hotelCount != null && hotelCount == 0L) {
            seedHotelsAndRooms();
        }
        topUpInventoryHorizon();
    }

    private void seedHotelsAndRooms() {
        List<CityBlueprint> cities = buildCityBlueprints();
        List<MapSqlParameterSource> hotelBatch = new ArrayList<>(100);
        List<MapSqlParameterSource> roomBatch = new ArrayList<>(280);

        int globalIndex = 0;
        for (CityBlueprint city : cities) {
            for (int i = 0; i < city.count(); i++) {
                globalIndex++;
                // Deterministic UUIDs mean bookmarks and /hotels/{id} links stay valid even after a DB wipe & re-seed.
                UUID hotelId = UUID.nameUUIDFromBytes(("stayscale-hotel-" + city.city() + "-" + i)
                        .getBytes(StandardCharsets.UTF_8));

                String prefix = HOTEL_PREFIXES[(globalIndex + i) % HOTEL_PREFIXES.length];
                String suffix = HOTEL_SUFFIXES[(i * 3 + globalIndex) % HOTEL_SUFFIXES.length];
                String neighborhood = city.neighborhoods().get(i % city.neighborhoods().size());
                String landmark = city.landmarks().get(i % city.landmarks().size());
                String hotelName = prefix + " " + neighborhood + " " + suffix;

                // Spread hotel coordinates gently around the city center so the Map Pin view looks natural.
                double latOffset = ((i % 5) - 2) * 0.0085 + (i * 0.0012);
                double lngOffset = ((i % 4) - 1.5) * 0.0092 - (i * 0.0009);
                double distanceKm = Math.round((0.3 + (i % 7) * 0.65) * 100.0) / 100.0;

                int starRating = switch (i % 5) {
                    case 0, 1 -> 5;
                    case 2, 3 -> 4;
                    default -> 3;
                };
                double guestScore = Math.round((7.4 + (starRating - 3) * 0.55 + ((i * 7) % 13) * 0.1) * 10.0) / 10.0;
                guestScore = Math.min(9.8, guestScore);
                int reviewCount = 140 + ((globalIndex * 83) % 2450);

                String propertyType = city.propertyTypes().get(i % city.propertyTypes().size());
                List<String> amenities = buildAmenities(city.city(), starRating, i);
                List<String> gallery = List.of(
                        city.hotelImage(),
                        city.primaryRoomImage(),
                        city.secondaryRoomImage(),
                        "/images/rooms/executive-city.jpg"
                );

                String description = String.format(
                        "Located in the vibrant %s quarter of %s, %s offers curated %d-star hospitality just %.1f km from %s. Designed for seamless modern stays with spacious suites, curated dining, and instant multi-night availability.",
                        neighborhood, city.city(), hotelName, starRating, distanceKm, landmark
                );

                hotelBatch.add(new MapSqlParameterSource()
                        .addValue("id", hotelId)
                        .addValue("name", hotelName)
                        .addValue("city", city.city())
                        .addValue("country", city.country())
                        .addValue("neighborhood", neighborhood)
                        .addValue("address", (14 + i * 7) + " " + neighborhood + " Avenue, " + city.city())
                        .addValue("latitude", city.baseLat() + latOffset)
                        .addValue("longitude", city.baseLng() + lngOffset)
                        .addValue("landmarkName", landmark)
                        .addValue("distanceToCenterKm", distanceKm)
                        .addValue("starRating", starRating)
                        .addValue("guestScore", guestScore)
                        .addValue("reviewCount", reviewCount)
                        .addValue("propertyType", propertyType)
                        .addValue("description", description)
                        .addValue("imageUrl", city.hotelImage())
                        .addValue("galleryUrls", toJsonArray(gallery))
                        .addValue("amenities", toJsonArray(amenities)));

                // Monetary values are stored in integer paise (340_000 = ₹3,400.00) to avoid float rounding bugs.
                long baseMinor = city.baseNightlyMinor() + (starRating - 3) * 185_000L + (i % 4) * 45_000L;
                addRoomType(roomBatch, hotelId, city.city(), i, 0,
                        "Superior " + neighborhood + " Room",
                        "Thoughtfully designed room with crisp bedding, high-speed workspace, and rain shower.",
                        "Queen Bed", 28, 2, baseMinor, 6 + (i % 5),
                        true, i % 2 == 0, city.primaryRoomImage());

                addRoomType(roomBatch, hotelId, city.city(), i, 1,
                        "Deluxe " + landmark + " View",
                        "Elevated corner accommodation with private balcony views and lounge seating.",
                        "King Bed", 38, 3, Math.round(baseMinor * 1.38), 4 + (i % 4),
                        true, true, city.secondaryRoomImage());

                if (starRating >= 4) {
                    addRoomType(roomBatch, hotelId, city.city(), i, 2,
                            starRating == 5 ? "Signature Suite & Villa" : "Executive Panoramic Suite",
                            "Expansive suite featuring separate living lounge, soaking tub, and priority late checkout.",
                            "King Bed + Sofa Bed", 56, 4, Math.round(baseMinor * 1.88), 3 + (i % 3),
                            i % 3 != 0, true, "/images/rooms/executive-city.jpg");
                }
            }
        }

        String insertHotelSql = """
                INSERT INTO hotels (
                    id, name, city, country, neighborhood, address,
                    latitude, longitude, landmark_name, distance_to_center_km,
                    star_rating, guest_score, review_count, property_type,
                    description, image_url, gallery_urls, amenities
                ) VALUES (
                    :id, :name, :city, :country, :neighborhood, :address,
                    :latitude, :longitude, :landmarkName, :distanceToCenterKm,
                    :starRating, :guestScore, :reviewCount, :propertyType,
                    :description, :imageUrl, CAST(:galleryUrls AS jsonb), CAST(:amenities AS jsonb)
                )
                """;
        jdbc.batchUpdate(insertHotelSql, hotelBatch.toArray(MapSqlParameterSource[]::new));

        String insertRoomSql = """
                INSERT INTO room_types (
                    id, hotel_id, name, description, bed_type, room_size_sqm,
                    max_guests, base_price_minor, currency, total_units,
                    free_cancellation, breakfast_included, image_url
                ) VALUES (
                    :id, :hotelId, :name, :description, :bedType, :roomSizeSqm,
                    :maxGuests, :basePriceMinor, 'INR', :totalUnits,
                    :freeCancellation, :breakfastIncluded, :imageUrl
                )
                """;
        jdbc.batchUpdate(insertRoomSql, roomBatch.toArray(MapSqlParameterSource[]::new));

        log.info("Seeded {} StayScale hotels and {} room types across {} cities.",
                hotelBatch.size(), roomBatch.size(), cities.size());
    }

    /**
     * Generates one inventory row per (room_type_id, date) directly inside PostgreSQL in ~100ms.
     * Uses HASHTEXT so some dates deterministically simulate sold-out (0) or low-stock (<=2) rooms,
     * and tightens Fri/Sat availability (DOW 5, 6) like real weekend demand.
     */
    private void topUpInventoryHorizon() {
        String sql = """
                INSERT INTO room_inventory_by_date (room_type_id, stay_date, total_rooms, available_rooms, version)
                SELECT rt.id,
                       d::date,
                       rt.total_units,
                       CASE
                           WHEN (ABS(HASHTEXT(rt.id::text || d::text)) % 29) = 0 THEN 0
                           WHEN (ABS(HASHTEXT(rt.id::text || d::text)) % 7) = 0 THEN LEAST(2, rt.total_units)
                           WHEN EXTRACT(DOW FROM d) IN (5, 6) THEN GREATEST(1, rt.total_units - 2)
                           ELSE rt.total_units
                       END,
                       0
                FROM room_types rt
                CROSS JOIN generate_series(
                    CURRENT_DATE,
                    CURRENT_DATE + (:daysAhead * interval '1 day'),
                    interval '1 day'
                ) AS d
                ON CONFLICT (room_type_id, stay_date) DO NOTHING
                """;
        int inserted = jdbc.update(sql, new MapSqlParameterSource("daysAhead", daysAhead));
        if (inserted > 0) {
            log.info("Topped up {} daily room inventory rows across {} days ahead.", inserted, daysAhead);
        }
    }

    private void addRoomType(
            List<MapSqlParameterSource> batch,
            UUID hotelId,
            String city,
            int hotelIdx,
            int roomIdx,
            String name,
            String description,
            String bedType,
            int sizeSqm,
            int maxGuests,
            long basePriceMinor,
            int totalUnits,
            boolean freeCancel,
            boolean breakfast,
            String imageUrl
    ) {
        UUID roomId = UUID.nameUUIDFromBytes(("stayscale-room-" + city + "-" + hotelIdx + "-" + roomIdx)
                .getBytes(StandardCharsets.UTF_8));
        batch.add(new MapSqlParameterSource()
                .addValue("id", roomId)
                .addValue("hotelId", hotelId)
                .addValue("name", name)
                .addValue("description", description)
                .addValue("bedType", bedType)
                .addValue("roomSizeSqm", sizeSqm)
                .addValue("maxGuests", maxGuests)
                .addValue("basePriceMinor", basePriceMinor)
                .addValue("totalUnits", totalUnits)
                .addValue("freeCancellation", freeCancel)
                .addValue("breakfastIncluded", breakfast)
                .addValue("imageUrl", imageUrl));
    }

    private List<String> buildAmenities(String city, int stars, int idx) {
        List<String> list = new ArrayList<>();
        list.add("Free WiFi");
        if (stars >= 4 || idx % 2 == 0) list.add("Pool");
        if (city.equals("Goa") || city.equals("Kochi")) list.add("Beachfront");
        if (stars == 5 || idx % 3 == 0) list.add("Spa");
        if (idx % 2 == 0) list.add("Breakfast Included");
        if (stars >= 4) list.add("Gym");
        if (stars == 5 || idx % 4 == 0) list.add("Airport Shuttle");
        if (city.equals("Manali")) list.add("Mountain View");
        return list;
    }

    private String toJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            String escaped = items.get(i).replace("\\", "\\\\").replace("\"", "\\\"");
            sb.append('"').append(escaped).append('"');
        }
        return sb.append(']').toString();
    }

    private List<CityBlueprint> buildCityBlueprints() {
        return List.of(
                new CityBlueprint("Goa", "India", 16, 15.5439, 73.7553, 340_000L,
                        "/images/hotels/goa-resort.jpg", "/images/rooms/deluxe-ocean.jpg", "/images/rooms/pool-villa.jpg",
                        List.of("Calangute", "Candolim", "Baga", "Anjuna", "Palolem", "Vagator"),
                        List.of("Calangute Beach", "Fort Aguada", "Baga Night Market", "Anjuna Cliff", "Palolem Cove"),
                        List.of("Beach Resort", "Boutique Villa", "Luxury Hotel")),
                new CityBlueprint("Mumbai", "India", 14, 18.9438, 72.8234, 460_000L,
                        "/images/hotels/mumbai-luxury.jpg", "/images/rooms/executive-city.jpg", "/images/rooms/deluxe-ocean.jpg",
                        List.of("Marine Drive", "Colaba", "Bandra West", "Juhu Beach", "Worli Sea Face", "Powai"),
                        List.of("Marine Drive Promenade", "Gateway of India", "Bandra Bandstand", "Juhu Tara Beach"),
                        List.of("Luxury Hotel", "Business Hotel", "Seafront Suite")),
                new CityBlueprint("Bengaluru", "India", 14, 12.9716, 77.5946, 310_000L,
                        "/images/hotels/bengaluru-boutique.jpg", "/images/rooms/executive-city.jpg", "/images/rooms/heritage-suite.jpg",
                        List.of("Indiranagar", "Koramangala", "MG Road", "UB City", "Whitefield", "Ulsoor Lake"),
                        List.of("Cubbon Park", "UB City", "Indiranagar 100ft Rd", "Ulsoor Lake"),
                        List.of("Design Hotel", "Boutique Stay", "Urban Resort")),
                new CityBlueprint("Jaipur", "India", 12, 26.9124, 75.7873, 360_000L,
                        "/images/hotels/jaipur-palace.jpg", "/images/rooms/heritage-suite.jpg", "/images/rooms/pool-villa.jpg",
                        List.of("Pink City Quarter", "Amer Road", "Civil Lines", "C-Scheme", "Bani Park"),
                        List.of("Hawa Mahal", "Amber Fort", "City Palace", "Jal Mahal"),
                        List.of("Heritage Palace", "Courtyard Haveli", "Luxury Resort")),
                new CityBlueprint("Udaipur", "India", 12, 24.5764, 73.6835, 390_000L,
                        "/images/hotels/udaipur-lake.jpg", "/images/rooms/heritage-suite.jpg", "/images/rooms/pool-villa.jpg",
                        List.of("Lake Pichola", "Fateh Sagar", "Old City Ghat", "Sajjangarh Foothills"),
                        List.of("City Palace Ghat", "Lake Pichola", "Jagdish Temple", "Monsoon Palace"),
                        List.of("Lakefront Haveli", "Heritage Palace", "Boutique Retreat")),
                new CityBlueprint("Manali", "India", 10, 32.2432, 77.1892, 280_000L,
                        "/images/hotels/manali-chalet.jpg", "/images/rooms/heritage-suite.jpg", "/images/rooms/executive-city.jpg",
                        List.of("Old Manali", "Solang Valley", "Vashisht Hills", "Mall Road", "Naggar Road"),
                        List.of("Hadimba Temple", "Solang Ropeway", "Beas Riverfront", "Mall Road"),
                        List.of("Mountain Chalet", "Alpine Lodge", "Pine Resort")),
                new CityBlueprint("Kochi", "India", 10, 9.9658, 76.2421, 320_000L,
                        "/images/hotels/kerala-backwaters.jpg", "/images/rooms/pool-villa.jpg", "/images/rooms/deluxe-ocean.jpg",
                        List.of("Fort Kochi", "Bolgatty Island", "Marine Drive", "Vypeen Coast", "Vembanad Shore"),
                        List.of("Chinese Fishing Nets", "Fort Kochi Beach", "Vembanad Lagoon", "Mattancherry Palace"),
                        List.of("Backwater Resort", "Pool Villa", "Heritage Boutique")),
                new CityBlueprint("Bangkok", "Thailand", 12, 13.7262, 100.5143, 420_000L,
                        "/images/hotels/bangkok-riverside.jpg", "/images/rooms/executive-city.jpg", "/images/rooms/deluxe-ocean.jpg",
                        List.of("Riverside", "Sukhumvit Thonglor", "Siam Square", "Silom Sathorn", "Ari"),
                        List.of("ICONSIAM Pier", "BTS Asok", "Lumpini Park", "Chao Phraya Ferry"),
                        List.of("Riverside Tower", "Skyline Hotel", "Boutique Suite"))
        );
    }

    private static final String[] HOTEL_PREFIXES = {
            "Aurelia", "The Verandah", "Solis", "Saffron", "Marigold",
            "Zuriya", "Casa", "The Crest", "Navya", "Lumina", "Serena", "Kaya"
    };

    private static final String[] HOTEL_SUFFIXES = {
            "Resort & Spa", "Grand Hotel", "Boutique Suites", "Sanctuary",
            "Retreat", "Residences", "Collection", "Palace Stay"
    };

    private record CityBlueprint(
            String city,
            String country,
            int count,
            double baseLat,
            double baseLng,
            long baseNightlyMinor,
            String hotelImage,
            String primaryRoomImage,
            String secondaryRoomImage,
            List<String> neighborhoods,
            List<String> landmarks,
            List<String> propertyTypes
    ) {}
}
