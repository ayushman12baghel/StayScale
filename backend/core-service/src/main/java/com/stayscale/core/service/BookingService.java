package com.stayscale.core.service;

import com.stayscale.core.dto.BookingResponse;
import com.stayscale.core.dto.ConfirmBookingRequest;
import com.stayscale.core.dto.CreateHoldRequest;
import com.stayscale.core.dto.HoldResponse;
import com.stayscale.core.exception.InvalidSearchException;
import com.stayscale.core.exception.InventoryConflictException;
import com.stayscale.core.exception.ResourceNotFoundException;
import com.stayscale.core.pricing.DynamicPricingEngine;
import com.stayscale.core.pricing.DynamicPricingEngine.NightInventorySnapshot;
import com.stayscale.core.pricing.PriceQuoteResponse;
import com.stayscale.core.repository.BookingRepository;
import com.stayscale.core.repository.BookingRepository.RoomOfferRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates the two-phase atomic StayScale booking pipeline:
 * <ol>
 *   <li>Phase A ({@link #createHold}): Idempotency check -> Dynamic pricing quote -> Redis lock + 10-min hold.</li>
 *   <li>Phase B ({@link #confirmBooking}): Idempotency check -> Validate Redis hold -> Postgres
 *       {@code SELECT FOR UPDATE} decrement + insert booking + save idempotency record atomically -> release Redis hold.</li>
 * </ol>
 */
@Service
public class BookingService {

    private static final int MAX_NIGHTS = 30;

    private final BookingRepository bookingRepository;
    private final RedisHoldService redisHoldService;
    private final DynamicPricingEngine pricingEngine;
    private final IdempotencyService idempotencyService;

    public BookingService(
            BookingRepository bookingRepository,
            RedisHoldService redisHoldService,
            DynamicPricingEngine pricingEngine,
            IdempotencyService idempotencyService
    ) {
        this.bookingRepository = bookingRepository;
        this.redisHoldService = redisHoldService;
        this.pricingEngine = pricingEngine;
        this.idempotencyService = idempotencyService;
    }

    public IdempotentOutcome<HoldResponse> createHold(String rawIdempotencyKey, String userId, CreateHoldRequest req) {
        String key = idempotencyService.requireKey(rawIdempotencyKey);
        String effectiveUserId = normalizeUserId(userId);
        String requestHash = idempotencyService.hashRequest("POST:/api/v1/holds", req);

        var replay = idempotencyService.findReplay(key, requestHash, HoldResponse.class);
        if (replay.isPresent()) {
            return new IdempotentOutcome<>(replay.get().httpStatus(), replay.get().body(), true);
        }

        validateHoldDates(req.checkIn(), req.checkOut());
        int totalNights = (int) ChronoUnit.DAYS.between(req.checkIn(), req.checkOut());
        int guests = req.guests() != null ? req.guests() : req.rooms() * 2;

        RoomOfferRow offer = bookingRepository.findRoomOffer(req.roomTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Room type not found: " + req.roomTypeId()));

        int requiredGuestsPerRoom = (int) Math.ceil((double) guests / req.rooms());
        if (offer.maxGuests() < requiredGuestsPerRoom) {
            throw new InvalidSearchException(String.format(
                    "Room '%s' accommodates max %d guests per room (requested %d across %d rooms)",
                    offer.roomName(), offer.maxGuests(), guests, req.rooms()));
        }

        List<NightInventorySnapshot> snapshots = bookingRepository.findInventorySnapshots(
                req.roomTypeId(), req.checkIn(), req.checkOut());
        if (snapshots.size() != totalNights) {
            throw new InventoryConflictException("Selected dates are not fully open for reservation");
        }

        Map<LocalDate, Integer> dbAvailByDate = new LinkedHashMap<>();
        for (NightInventorySnapshot snap : snapshots) {
            dbAvailByDate.put(snap.stayDate(), snap.availableRooms());
        }

        PriceQuoteResponse quote = pricingEngine.calculateQuote(
                offer.basePriceMinor(),
                offer.currency(),
                req.rooms(),
                snapshots,
                LocalDate.now()
        );

        HoldResponse draft = new HoldResponse(
                null,
                effectiveUserId,
                offer.hotelId(),
                offer.hotelName(),
                offer.hotelCity(),
                offer.hotelNeighborhood(),
                offer.hotelImageUrl(),
                offer.roomTypeId(),
                offer.roomName(),
                offer.roomImageUrl(),
                req.checkIn(),
                req.checkOut(),
                totalNights,
                req.rooms(),
                guests,
                null,
                0L,
                quote
        );

        HoldResponse created = redisHoldService.acquireHold(
                req.roomTypeId(),
                req.checkIn(),
                req.checkOut(),
                req.rooms(),
                dbAvailByDate,
                draft
        );

        idempotencyService.store(key, "POST:/api/v1/holds", requestHash, 201, created);
        return new IdempotentOutcome<>(201, created, false);
    }

    public HoldResponse getHold(String holdId) {
        return redisHoldService.getActiveHold(holdId);
    }

    /**
     * Confirms a held checkout inside an ACID PostgreSQL transaction:
     * locks rows in ascending date order, decrements availability, writes the booking,
     * and stores the idempotency payload together before releasing the Redis hold.
     */
    @Transactional
    public IdempotentOutcome<BookingResponse> confirmBooking(
            String rawIdempotencyKey,
            String userId,
            ConfirmBookingRequest req
    ) {
        String key = idempotencyService.requireKey(rawIdempotencyKey);
        String effectiveUserId = normalizeUserId(userId);
        String requestHash = idempotencyService.hashRequest("POST:/api/v1/bookings/confirm", req);

        var replay = idempotencyService.findReplay(key, requestHash, BookingResponse.class);
        if (replay.isPresent()) {
            return new IdempotentOutcome<>(replay.get().httpStatus(), replay.get().body(), true);
        }

        HoldResponse hold = redisHoldService.getActiveHold(req.holdId().trim());

        bookingRepository.lockAndDecrementInventory(
                hold.roomTypeId(),
                hold.checkIn(),
                hold.checkOut(),
                hold.rooms()
        );

        String paymentRef = maskPaymentReference(req.paymentToken());
        UUID bookingId;
        try {
            bookingId = bookingRepository.insertConfirmedBooking(
                    hold.userId() != null ? hold.userId() : effectiveUserId,
                    hold.hotelId(),
                    hold.roomTypeId(),
                    hold.holdId(),
                    hold.checkIn(),
                    hold.checkOut(),
                    hold.rooms(),
                    hold.guests(),
                    hold.pricing().baseNightlyMinor(),
                    hold.pricing().totalStayPriceMinor(),
                    hold.pricing().currency(),
                    hold.pricing().ruleSummaries(),
                    req.guestName().trim(),
                    req.guestEmail().trim(),
                    paymentRef
            );
        } catch (DuplicateKeyException dup) {
            throw new InventoryConflictException("Checkout hold '" + hold.holdId() + "' was already confirmed");
        }

        BookingResponse confirmed = bookingRepository.findBookingById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found after confirmation"));

        idempotencyService.store(key, "POST:/api/v1/bookings/confirm", requestHash, 201, confirmed);
        redisHoldService.consumeHold(hold);
        return new IdempotentOutcome<>(201, confirmed, false);
    }

    @Transactional
    public IdempotentOutcome<BookingResponse> cancelBooking(String rawIdempotencyKey, UUID bookingId) {
        String key = idempotencyService.requireKey(rawIdempotencyKey);
        String endpoint = "POST:/api/v1/bookings/" + bookingId + "/cancel";
        String requestHash = idempotencyService.hashRequest(endpoint, bookingId);

        var replay = idempotencyService.findReplay(key, requestHash, BookingResponse.class);
        if (replay.isPresent()) {
            return new IdempotentOutcome<>(replay.get().httpStatus(), replay.get().body(), true);
        }

        BookingResponse cancelled = bookingRepository.lockAndCancelBooking(bookingId);
        idempotencyService.store(key, endpoint, requestHash, 200, cancelled);
        return new IdempotentOutcome<>(200, cancelled, false);
    }

    @Transactional(readOnly = true)
    public List<BookingResponse> listBookingsForUser(String userId) {
        return bookingRepository.findBookingsByUser(normalizeUserId(userId));
    }

    private void validateHoldDates(LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidSearchException("checkOut date must be strictly after checkIn date");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > MAX_NIGHTS) {
            throw new InvalidSearchException("Stay duration cannot exceed " + MAX_NIGHTS + " nights");
        }
        if (checkIn.isBefore(LocalDate.now().minusDays(1))) {
            throw new InvalidSearchException("checkIn date cannot be in the past");
        }
    }

    private String normalizeUserId(String userId) {
        return (userId != null && !userId.isBlank()) ? userId.trim() : "guest-demo";
    }

    private String maskPaymentReference(String token) {
        String clean = token.trim();
        String tail = clean.length() > 4 ? clean.substring(clean.length() - 4) : clean;
        return "PAY-" + tail.toUpperCase();
    }

    public record IdempotentOutcome<T>(int httpStatus, T body, boolean replayed) {}
}
