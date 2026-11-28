package com.stayscale.core.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body for acquiring a 10-minute checkout hold on a room type.
 */
public record CreateHoldRequest(
        @NotNull(message = "roomTypeId is required") UUID roomTypeId,
        @NotNull(message = "checkIn date is required") LocalDate checkIn,
        @NotNull(message = "checkOut date is required") LocalDate checkOut,
        @Min(value = 1, message = "rooms must be at least 1")
        @Max(value = 8, message = "rooms cannot exceed 8")
        int rooms,
        @Min(value = 1, message = "guests must be at least 1")
        @Max(value = 16, message = "guests cannot exceed 16")
        Integer guests
) {}
