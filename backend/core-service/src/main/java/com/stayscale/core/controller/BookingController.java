package com.stayscale.core.controller;

import com.stayscale.core.dto.BookingResponse;
import com.stayscale.core.dto.ConfirmBookingRequest;
import com.stayscale.core.dto.CreateHoldRequest;
import com.stayscale.core.dto.HoldResponse;
import com.stayscale.core.service.BookingService;
import com.stayscale.core.service.BookingService.IdempotentOutcome;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints for two-phase atomic reservations:
 * 1) Place 10-minute checkout holds ({@code POST /api/v1/holds}),
 * 2) Confirm payment idempotently ({@code POST /api/v1/bookings/confirm}),
 * 3) View and cancel bookings ({@code GET /api/v1/bookings}, {@code POST /api/v1/bookings/{id}/cancel}).
 */
@RestController
@RequestMapping("/api/v1")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/holds")
    public ResponseEntity<HoldResponse> createHold(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody CreateHoldRequest request
    ) {
        IdempotentOutcome<HoldResponse> outcome = bookingService.createHold(idempotencyKey, userId, request);
        return ResponseEntity.status(outcome.httpStatus())
                .header("X-Idempotency-Replayed", String.valueOf(outcome.replayed()))
                .body(outcome.body());
    }

    @GetMapping("/holds/{holdId}")
    public HoldResponse getHold(@PathVariable String holdId) {
        return bookingService.getHold(holdId);
    }

    @PostMapping("/bookings/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody ConfirmBookingRequest request
    ) {
        IdempotentOutcome<BookingResponse> outcome = bookingService.confirmBooking(idempotencyKey, userId, request);
        return ResponseEntity.status(outcome.httpStatus())
                .header("X-Idempotency-Replayed", String.valueOf(outcome.replayed()))
                .body(outcome.body());
    }

    @PostMapping("/bookings/{id}/cancel")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        IdempotentOutcome<BookingResponse> outcome = bookingService.cancelBooking(idempotencyKey, id);
        return ResponseEntity.status(outcome.httpStatus())
                .header("X-Idempotency-Replayed", String.valueOf(outcome.replayed()))
                .body(outcome.body());
    }

    @GetMapping("/bookings")
    public List<BookingResponse> listBookings(
            @RequestHeader(value = "X-User-Id", required = false) String headerUserId,
            @RequestParam(required = false) String userId
    ) {
        String effectiveUser = (userId != null && !userId.isBlank()) ? userId : headerUserId;
        return bookingService.listBookingsForUser(effectiveUser);
    }
}
