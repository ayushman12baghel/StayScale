package com.stayscale.core.dto;

import com.stayscale.core.pricing.PriceQuoteResponse;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response representing an active 10-minute Redis checkout hold with locked dynamic pricing.
 */
public record HoldResponse(
        String holdId,
        String userId,
        UUID hotelId,
        String hotelName,
        String hotelCity,
        String hotelNeighborhood,
        String hotelImageUrl,
        UUID roomTypeId,
        String roomName,
        String roomImageUrl,
        LocalDate checkIn,
        LocalDate checkOut,
        int totalNights,
        int rooms,
        int guests,
        Instant expiresAt,
        long ttlSeconds,
        PriceQuoteResponse pricing
) {}
