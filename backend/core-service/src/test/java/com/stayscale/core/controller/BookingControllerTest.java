package com.stayscale.core.controller;

import com.stayscale.core.config.TraceIdFilter;
import com.stayscale.core.dto.ConfirmBookingRequest;
import com.stayscale.core.dto.CreateHoldRequest;
import com.stayscale.core.exception.GlobalExceptionHandler;
import com.stayscale.core.exception.HoldExpiredException;
import com.stayscale.core.exception.IdempotencyConflictException;
import com.stayscale.core.exception.InventoryConflictException;
import com.stayscale.core.exception.MissingIdempotencyKeyException;
import com.stayscale.core.service.BookingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BookingControllerTest {

    @Mock
    private BookingService bookingService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BookingController(bookingService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new TraceIdFilter())
                .build();
    }

    @Test
    void returns400WhenIdempotencyKeyMissing() throws Exception {
        when(bookingService.createHold(isNull(), any(), any(CreateHoldRequest.class)))
                .thenThrow(new MissingIdempotencyKeyException("Idempotency-Key header is required"));

        String body = """
                {"roomTypeId":"%s","checkIn":"%s","checkOut":"%s","rooms":1,"guests":2}
                """.formatted(UUID.randomUUID(), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        mockMvc.perform(post("/api/v1/holds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Missing Idempotency-Key Header"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void returns409OnInventoryConflict() throws Exception {
        when(bookingService.createHold(any(), any(), any(CreateHoldRequest.class)))
                .thenThrow(new InventoryConflictException("Sold out"));

        String body = """
                {"roomTypeId":"%s","checkIn":"%s","checkOut":"%s","rooms":1,"guests":2}
                """.formatted(UUID.randomUUID(), LocalDate.now().plusDays(1), LocalDate.now().plusDays(3));

        mockMvc.perform(post("/api/v1/holds")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Inventory Conflict"));
    }

    @Test
    void returns410WhenHoldExpired() throws Exception {
        when(bookingService.getHold("hold_expired")).thenThrow(new HoldExpiredException("Expired"));

        mockMvc.perform(get("/api/v1/holds/hold_expired"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.title").value("Checkout Hold Expired"));
    }

    @Test
    void returns422WhenIdempotencyPayloadMismatches() throws Exception {
        when(bookingService.confirmBooking(any(), any(), any(ConfirmBookingRequest.class)))
                .thenThrow(new IdempotencyConflictException("Payload mismatch"));

        String body = """
                {"holdId":"hold_123","guestName":"Ayush","guestEmail":"ayush@test.com","paymentToken":"tok_4242"}
                """;

        mockMvc.perform(post("/api/v1/bookings/confirm")
                        .header("Idempotency-Key", "k2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Idempotency Key Payload Mismatch"));
    }
}
