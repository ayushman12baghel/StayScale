package com.stayscale.core.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for confirming a held reservation into a permanent PostgreSQL booking.
 */
public record ConfirmBookingRequest(
        @NotBlank(message = "holdId is required") String holdId,
        @NotBlank(message = "guestName is required") String guestName,
        @NotBlank(message = "guestEmail is required")
        @Email(message = "guestEmail must be a valid email address")
        String guestEmail,
        @NotBlank(message = "paymentToken is required") String paymentToken
) {}
