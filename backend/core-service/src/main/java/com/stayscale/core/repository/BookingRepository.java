package com.stayscale.core.repository;

import com.stayscale.core.dto.BookingResponse;
import com.stayscale.core.exception.InventoryConflictException;
import com.stayscale.core.exception.ResourceNotFoundException;
import com.stayscale.core.pricing.DynamicPricingEngine.NightInventorySnapshot;
import com.stayscale.core.pricing.PricingAdjustment;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Layer 2 Persistence & Row-Level Locking Repository for holds, confirmations, and cancellations.
 *
 * <p>Every inventory mutation executes {@code SELECT ... FROM room_inventory_by_date ORDER BY stay_date ASC FOR UPDATE}
 * inside a single PostgreSQL {@code @Transactional} boundary so concurrent checkouts serialize cleanly
 * without deadlocks or negative room counts.
 */
@Repository
public class BookingRepository {

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public BookingRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<RoomOfferRow> findRoomOffer(UUID roomTypeId) {
        String sql = """
                SELECT rt.id AS room_type_id,
                       rt.name AS room_name,
                       rt.max_guests,
                       rt.base_price_minor,
                       rt.currency,
                       rt.image_url AS room_image_url,
                       h.id AS hotel_id,
                       h.name AS hotel_name,
                       h.city AS hotel_city,
                       h.neighborhood AS hotel_neighborhood,
                       h.image_url AS hotel_image_url
                FROM room_types rt
                JOIN hotels h ON h.id = rt.hotel_id
                WHERE rt.id = :roomTypeId
                """;
        List<RoomOfferRow> rows = jdbc.query(sql, new MapSqlParameterSource("roomTypeId", roomTypeId), (rs, rowNum) ->
                new RoomOfferRow(
                        rs.getObject("room_type_id", UUID.class),
                        rs.getString("room_name"),
                        rs.getInt("max_guests"),
                        rs.getLong("base_price_minor"),
                        rs.getString("currency"),
                        rs.getString("room_image_url"),
                        rs.getObject("hotel_id", UUID.class),
                        rs.getString("hotel_name"),
                        rs.getString("hotel_city"),
                        rs.getString("hotel_neighborhood"),
                        rs.getString("hotel_image_url")
                ));
        return rows.stream().findFirst();
    }

    public List<NightInventorySnapshot> findInventorySnapshots(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        String sql = """
                SELECT stay_date, total_rooms, available_rooms
                FROM room_inventory_by_date
                WHERE room_type_id = :roomTypeId
                  AND stay_date >= :checkIn
                  AND stay_date < :checkOut
                ORDER BY stay_date ASC
                """;
        return jdbc.query(sql, new MapSqlParameterSource()
                        .addValue("roomTypeId", roomTypeId)
                        .addValue("checkIn", checkIn)
                        .addValue("checkOut", checkOut),
                (rs, rowNum) -> new NightInventorySnapshot(
                        rs.getObject("stay_date", LocalDate.class),
                        rs.getInt("total_rooms"),
                        rs.getInt("available_rooms")
                ));
    }

    /**
     * Acquires pessimistic PostgreSQL row locks in ascending {@code stay_date} order (deadlock-free by construction),
     * verifies availability across every stayed night, and decrements {@code available_rooms} atomically.
     */
    public void lockAndDecrementInventory(UUID roomTypeId, LocalDate checkIn, LocalDate checkOut, int roomsRequested) {
        int expectedNights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);

        String lockSql = """
                SELECT stay_date, available_rooms
                FROM room_inventory_by_date
                WHERE room_type_id = :roomTypeId
                  AND stay_date >= :checkIn
                  AND stay_date < :checkOut
                ORDER BY stay_date ASC
                FOR UPDATE
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("roomTypeId", roomTypeId)
                .addValue("checkIn", checkIn)
                .addValue("checkOut", checkOut)
                .addValue("rooms", roomsRequested);

        List<LockedNightRow> lockedRows = jdbc.query(lockSql, params, (rs, rowNum) ->
                new LockedNightRow(rs.getObject("stay_date", LocalDate.class), rs.getInt("available_rooms")));

        if (lockedRows.size() != expectedNights) {
            throw new InventoryConflictException("Room inventory is not open for all selected dates");
        }
        for (LockedNightRow row : lockedRows) {
            if (row.availableRooms() < roomsRequested) {
                throw new InventoryConflictException(
                        "Insufficient inventory remaining on " + row.stayDate() + " during final confirmation");
            }
        }

        String updateSql = """
                UPDATE room_inventory_by_date
                SET available_rooms = available_rooms - :rooms,
                    version = version + 1
                WHERE room_type_id = :roomTypeId
                  AND stay_date >= :checkIn
                  AND stay_date < :checkOut
                  AND available_rooms >= :rooms
                """;
        int updated = jdbc.update(updateSql, params);
        if (updated != expectedNights) {
            throw new InventoryConflictException("Concurrent update conflicted during inventory decrement");
        }
    }

    public UUID insertConfirmedBooking(
            String userId,
            UUID hotelId,
            UUID roomTypeId,
            String holdId,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomsCount,
            int guestsCount,
            long nightlyBaseMinor,
            long totalPriceMinor,
            String currency,
            List<PricingAdjustment> pricingBreakdown,
            String guestName,
            String guestEmail,
            String paymentReference
    ) {
        UUID bookingId = UUID.randomUUID();
        String breakdownJson = objectMapper.writeValueAsString(pricingBreakdown);

        String sql = """
                INSERT INTO bookings (
                    id, user_id, hotel_id, room_type_id, hold_id,
                    check_in, check_out, rooms_count, guests_count,
                    nightly_base_minor, total_price_minor, currency,
                    pricing_breakdown, status, guest_name, guest_email, payment_reference
                ) VALUES (
                    :id, :userId, :hotelId, :roomTypeId, :holdId,
                    :checkIn, :checkOut, :roomsCount, :guestsCount,
                    :nightlyBaseMinor, :totalPriceMinor, :currency,
                    CAST(:pricingBreakdown AS jsonb), 'CONFIRMED', :guestName, :guestEmail, :paymentReference
                )
                """;
        jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", bookingId)
                .addValue("userId", userId)
                .addValue("hotelId", hotelId)
                .addValue("roomTypeId", roomTypeId)
                .addValue("holdId", holdId)
                .addValue("checkIn", checkIn)
                .addValue("checkOut", checkOut)
                .addValue("roomsCount", roomsCount)
                .addValue("guestsCount", guestsCount)
                .addValue("nightlyBaseMinor", nightlyBaseMinor)
                .addValue("totalPriceMinor", totalPriceMinor)
                .addValue("currency", currency)
                .addValue("pricingBreakdown", breakdownJson)
                .addValue("guestName", guestName)
                .addValue("guestEmail", guestEmail)
                .addValue("paymentReference", paymentReference));

        return bookingId;
    }

    public BookingResponse lockAndCancelBooking(UUID bookingId) {
        String lockBookingSql = """
                SELECT id, room_type_id, check_in, check_out, rooms_count, status
                FROM bookings
                WHERE id = :id
                FOR UPDATE
                """;
        List<LockedBookingRow> rows = jdbc.query(lockBookingSql, new MapSqlParameterSource("id", bookingId), (rs, rowNum) ->
                new LockedBookingRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("room_type_id", UUID.class),
                        rs.getObject("check_in", LocalDate.class),
                        rs.getObject("check_out", LocalDate.class),
                        rs.getInt("rooms_count"),
                        rs.getString("status")
                ));
        if (rows.isEmpty()) {
            throw new ResourceNotFoundException("Booking not found: " + bookingId);
        }
        LockedBookingRow b = rows.getFirst();
        if ("CANCELLED".equalsIgnoreCase(b.status())) {
            throw new InventoryConflictException("Booking " + bookingId + " is already cancelled");
        }

        // Lock inventory rows in ascending stay_date order before restoring availability.
        MapSqlParameterSource invParams = new MapSqlParameterSource()
                .addValue("roomTypeId", b.roomTypeId())
                .addValue("checkIn", b.checkIn())
                .addValue("checkOut", b.checkOut())
                .addValue("roomsCount", b.roomsCount());

        jdbc.query("""
                SELECT stay_date
                FROM room_inventory_by_date
                WHERE room_type_id = :roomTypeId
                  AND stay_date >= :checkIn
                  AND stay_date < :checkOut
                ORDER BY stay_date ASC
                FOR UPDATE
                """, invParams, (rs, rowNum) -> rs.getObject("stay_date", LocalDate.class));

        jdbc.update("""
                UPDATE room_inventory_by_date
                SET available_rooms = LEAST(total_rooms, available_rooms + :roomsCount),
                    version = version + 1
                WHERE room_type_id = :roomTypeId
                  AND stay_date >= :checkIn
                  AND stay_date < :checkOut
                """, invParams);

        jdbc.update("""
                UPDATE bookings
                SET status = 'CANCELLED',
                    cancelled_at = NOW()
                WHERE id = :id
                """, new MapSqlParameterSource("id", bookingId));

        return findBookingById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found after cancellation: " + bookingId));
    }

    public Optional<BookingResponse> findBookingById(UUID bookingId) {
        String sql = baseBookingSelect() + " WHERE b.id = :id ";
        return jdbc.query(sql, new MapSqlParameterSource("id", bookingId), (rs, rowNum) -> mapBookingRow(rs))
                .stream()
                .findFirst();
    }

    public List<BookingResponse> findBookingsByUser(String userId) {
        String sql = baseBookingSelect() + " WHERE b.user_id = :userId ORDER BY b.created_at DESC ";
        return jdbc.query(sql, new MapSqlParameterSource("userId", userId), (rs, rowNum) -> mapBookingRow(rs));
    }

    private String baseBookingSelect() {
        return """
                SELECT b.id,
                       b.user_id,
                       b.hold_id,
                       b.hotel_id,
                       h.name AS hotel_name,
                       h.city AS hotel_city,
                       h.neighborhood AS hotel_neighborhood,
                       h.image_url AS hotel_image_url,
                       b.room_type_id,
                       rt.name AS room_name,
                       b.check_in,
                       b.check_out,
                       b.rooms_count,
                       b.guests_count,
                       b.nightly_base_minor,
                       b.total_price_minor,
                       b.currency,
                       b.pricing_breakdown::text AS breakdown_json,
                       b.status,
                       b.guest_name,
                       b.guest_email,
                       b.payment_reference,
                       b.experiment_variant,
                       b.cancelled_at,
                       b.created_at
                FROM bookings b
                JOIN hotels h ON h.id = b.hotel_id
                JOIN room_types rt ON rt.id = b.room_type_id
                """;
    }

    private BookingResponse mapBookingRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        LocalDate checkIn = rs.getObject("check_in", LocalDate.class);
        LocalDate checkOut = rs.getObject("check_out", LocalDate.class);
        int nights = (int) ChronoUnit.DAYS.between(checkIn, checkOut);

        String breakdownJson = rs.getString("breakdown_json");
        PricingAdjustment[] adjustments = (breakdownJson == null || breakdownJson.isBlank())
                ? new PricingAdjustment[0]
                : objectMapper.readValue(breakdownJson, PricingAdjustment[].class);

        OffsetDateTime cancelledOdt = rs.getObject("cancelled_at", OffsetDateTime.class);
        OffsetDateTime createdOdt = rs.getObject("created_at", OffsetDateTime.class);

        return new BookingResponse(
                rs.getObject("id", UUID.class),
                rs.getString("user_id"),
                rs.getString("hold_id"),
                rs.getObject("hotel_id", UUID.class),
                rs.getString("hotel_name"),
                rs.getString("hotel_city"),
                rs.getString("hotel_neighborhood"),
                rs.getString("hotel_image_url"),
                rs.getObject("room_type_id", UUID.class),
                rs.getString("room_name"),
                checkIn,
                checkOut,
                nights,
                rs.getInt("rooms_count"),
                rs.getInt("guests_count"),
                rs.getLong("nightly_base_minor"),
                rs.getLong("total_price_minor"),
                rs.getString("currency"),
                List.of(adjustments),
                rs.getString("status"),
                rs.getString("guest_name"),
                rs.getString("guest_email"),
                rs.getString("payment_reference"),
                rs.getString("experiment_variant"),
                cancelledOdt != null ? cancelledOdt.toInstant() : null,
                createdOdt != null ? createdOdt.toInstant() : Instant.now()
        );
    }

    public record RoomOfferRow(
            UUID roomTypeId,
            String roomName,
            int maxGuests,
            long basePriceMinor,
            String currency,
            String roomImageUrl,
            UUID hotelId,
            String hotelName,
            String hotelCity,
            String hotelNeighborhood,
            String hotelImageUrl
    ) {}

    private record LockedNightRow(LocalDate stayDate, int availableRooms) {}

    private record LockedBookingRow(
            UUID id,
            UUID roomTypeId,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomsCount,
            String status
    ) {}
}
