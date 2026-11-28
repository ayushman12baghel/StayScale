package com.stayscale.core.service;

import com.stayscale.core.dto.BookingResponse;
import com.stayscale.core.dto.ConfirmBookingRequest;
import com.stayscale.core.dto.CreateHoldRequest;
import com.stayscale.core.dto.HoldResponse;
import com.stayscale.core.exception.HoldExpiredException;
import com.stayscale.core.exception.IdempotencyConflictException;
import com.stayscale.core.exception.InventoryConflictException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end concurrency and idempotency verification against live PostgreSQL + Redis.
 * Automatically skipped if Postgres (5433) or Redis (6379) is not reachable.
 */
@SpringBootTest(properties = "stayscale.seed.enabled=false")
class BookingConcurrencyIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private StringRedisTemplate redis;

    private UUID testHotelId;
    private UUID testRoomTypeId;
    private final List<String> createdIdempotencyKeys = new ArrayList<>();

    @BeforeAll
    static void requireLocalInfra() {
        Assumptions.assumeTrue(isReachable("localhost", 5433) && isReachable("localhost", 6379),
                "Requires local Docker Postgres (5433) and Redis (6379)");
    }

    @AfterEach
    void cleanupTestRows() {
        if (testRoomTypeId != null) {
            jdbc.update("DELETE FROM bookings WHERE room_type_id = :rtId",
                    new MapSqlParameterSource("rtId", testRoomTypeId));
            jdbc.update("DELETE FROM room_inventory_by_date WHERE room_type_id = :rtId",
                    new MapSqlParameterSource("rtId", testRoomTypeId));
            jdbc.update("DELETE FROM room_types WHERE id = :rtId",
                    new MapSqlParameterSource("rtId", testRoomTypeId));
        }
        if (testHotelId != null) {
            jdbc.update("DELETE FROM hotels WHERE id = :hId",
                    new MapSqlParameterSource("hId", testHotelId));
        }
        for (String key : createdIdempotencyKeys) {
            jdbc.update("DELETE FROM idempotency_keys WHERE idempotency_key = :k",
                    new MapSqlParameterSource("k", key));
        }
        createdIdempotencyKeys.clear();
    }

    @Test
    void fiveHundredConcurrentCheckoutsForFiveRoomsYieldExactlyFiveConfirmedZeroOversells() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = checkIn.plusDays(3);
        seedTestHotelWithFiveRooms(checkIn, checkOut, 5);

        int totalRequests = 500;
        AtomicInteger confirmedCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();
        AtomicInteger unexpectedErrors = new AtomicInteger();

        CountDownLatch readyGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>(totalRequests);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < totalRequests; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    readyGate.await();
                    String holdKey = "test-hold-" + testRoomTypeId + "-" + idx;
                    String confirmKey = "test-confirm-" + testRoomTypeId + "-" + idx;
                    createdIdempotencyKeys.add(holdKey);
                    createdIdempotencyKeys.add(confirmKey);

                    try {
                        var holdOutcome = bookingService.createHold(
                                holdKey,
                                "load-user-" + idx,
                                new CreateHoldRequest(testRoomTypeId, checkIn, checkOut, 1, 2)
                        );
                        bookingService.confirmBooking(
                                confirmKey,
                                "load-user-" + idx,
                                new ConfirmBookingRequest(
                                        holdOutcome.body().holdId(),
                                        "Test Traveler " + idx,
                                        "traveler" + idx + "@stayscale.test",
                                        "tok_visa_4242"
                                )
                        );
                        confirmedCount.incrementAndGet();
                    } catch (InventoryConflictException conflict) {
                        conflictCount.incrementAndGet();
                    } catch (Throwable t) {
                        unexpectedErrors.incrementAndGet();
                    }
                    return null;
                }));
            }

            readyGate.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        }

        assertEquals(0, unexpectedErrors.get(), "Unexpected exceptions occurred during contention burst");
        assertEquals(5, confirmedCount.get(), "Exactly 5 bookings should succeed");
        assertEquals(495, conflictCount.get(), "Exactly 495 requests should receive 409 Conflict");

        Integer minRemaining = jdbc.queryForObject("""
                SELECT MIN(available_rooms)
                FROM room_inventory_by_date
                WHERE room_type_id = :rtId
                  AND stay_date >= :checkIn
                  AND stay_date < :checkOut
                """, new MapSqlParameterSource()
                        .addValue("rtId", testRoomTypeId)
                        .addValue("checkIn", checkIn)
                        .addValue("checkOut", checkOut),
                Integer.class);
        assertEquals(0, minRemaining, "Available rooms must hit exactly 0 with zero oversells");
    }

    @Test
    void enforcesIdempotencyReplayMismatchExpiryAndCancellationRestore() {
        LocalDate checkIn = LocalDate.now().plusDays(15);
        LocalDate checkOut = checkIn.plusDays(2);
        seedTestHotelWithFiveRooms(checkIn, checkOut, 2);

        String holdKey = "idem-hold-" + UUID.randomUUID();
        String confirmKey = "idem-confirm-" + UUID.randomUUID();
        String cancelKey = "idem-cancel-" + UUID.randomUUID();
        createdIdempotencyKeys.addAll(List.of(holdKey, confirmKey, cancelKey));

        CreateHoldRequest holdReq = new CreateHoldRequest(testRoomTypeId, checkIn, checkOut, 1, 2);
        HoldResponse hold = bookingService.createHold(holdKey, "usr-1", holdReq).body();

        // 1. Reusing same Idempotency-Key with a DIFFERENT payload -> 422 IdempotencyConflictException
        assertThrows(IdempotencyConflictException.class, () ->
                bookingService.createHold(holdKey, "usr-1", new CreateHoldRequest(testRoomTypeId, checkIn, checkOut, 2, 2)));

        // 2. Confirm booking and replay exact same Idempotency-Key -> same booking ID, no double decrement
        ConfirmBookingRequest confirmReq = new ConfirmBookingRequest(hold.holdId(), "Ayush", "ayush@test.com", "tok_test_4242");
        var firstConfirm = bookingService.confirmBooking(confirmKey, "usr-1", confirmReq);
        assertFalse(firstConfirm.replayed());

        var secondConfirm = bookingService.confirmBooking(confirmKey, "usr-1", confirmReq);
        assertTrue(secondConfirm.replayed());
        assertEquals(firstConfirm.body().id(), secondConfirm.body().id());

        Integer afterConfirmAvail = jdbc.queryForObject(
                "SELECT MIN(available_rooms) FROM room_inventory_by_date WHERE room_type_id = :rtId",
                new MapSqlParameterSource("rtId", testRoomTypeId), Integer.class);
        assertEquals(1, afterConfirmAvail);

        // 3. Confirming an expired/missing hold -> 410 Gone
        assertThrows(HoldExpiredException.class, () ->
                bookingService.confirmBooking("expired-" + UUID.randomUUID(), "usr-1",
                        new ConfirmBookingRequest("hold_nonexistent", "Ayush", "ayush@test.com", "tok_test_4242")));

        // 4. Cancel booking -> restores inventory back to 2
        BookingResponse cancelled = bookingService.cancelBooking(cancelKey, firstConfirm.body().id()).body();
        assertEquals("CANCELLED", cancelled.status());

        Integer afterCancelAvail = jdbc.queryForObject(
                "SELECT MIN(available_rooms) FROM room_inventory_by_date WHERE room_type_id = :rtId",
                new MapSqlParameterSource("rtId", testRoomTypeId), Integer.class);
        assertEquals(2, afterCancelAvail);
    }

    private void seedTestHotelWithFiveRooms(LocalDate checkIn, LocalDate checkOut, int units) {
        testHotelId = UUID.randomUUID();
        testRoomTypeId = UUID.randomUUID();

        jdbc.update("""
                INSERT INTO hotels (id, name, city, country, neighborhood, address, latitude, longitude,
                    landmark_name, distance_to_center_km, star_rating, guest_score, review_count,
                    property_type, description, image_url)
                VALUES (:id, 'Concurrency Test Resort', 'Goa', 'India', 'Calangute', '1 Test Rd',
                    15.54, 73.75, 'Calangute Beach', 0.5, 5, 9.2, 100, 'Resort', 'Test', '/images/hotels/goa-resort.jpg')
                """, new MapSqlParameterSource("id", testHotelId));

        jdbc.update("""
                INSERT INTO room_types (id, hotel_id, name, description, bed_type, room_size_sqm,
                    max_guests, base_price_minor, currency, total_units, free_cancellation, breakfast_included, image_url)
                VALUES (:id, :hotelId, 'Flash Sale Suite', 'Test Suite', 'King Bed', 40,
                    2, 500000, 'INR', :units, true, true, '/images/rooms/deluxe-ocean.jpg')
                """, new MapSqlParameterSource()
                .addValue("id", testRoomTypeId)
                .addValue("hotelId", testHotelId)
                .addValue("units", units));

        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            jdbc.update("""
                    INSERT INTO room_inventory_by_date (room_type_id, stay_date, total_rooms, available_rooms, version)
                    VALUES (:rtId, :stayDate, :units, :units, 0)
                    """, new MapSqlParameterSource()
                    .addValue("rtId", testRoomTypeId)
                    .addValue("stayDate", d)
                    .addValue("units", units));
        }

        // Clean any leftover test Redis keys for this roomTypeId
        for (LocalDate d = checkIn; d.isBefore(checkOut); d = d.plusDays(1)) {
            redis.delete("stayscale:hold_idx:" + testRoomTypeId + ":" + d);
        }
    }

    private static boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 500);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
