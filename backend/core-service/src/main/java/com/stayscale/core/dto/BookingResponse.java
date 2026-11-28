package com.stayscale.core.dto;

import com.stayscale.core.pricing.PricingAdjustment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full confirmed or cancelled booking receipt returned to the traveler and My Bookings page.
 */
public record BookingResponse(
        UUID id,
        String userId,
        String holdId,
        UUID hotelId,
        String hotelName,
        String hotelCity,
        String hotelNeighborhood,
        String hotelImageUrl,
        UUID roomTypeId,
        String roomName,
        LocalDate checkIn,
        LocalDate checkOut,
        int totalNights,
        int roomsCount,
        int guestsCount,
        long nightlyBaseMinor,
        long totalPriceMinor,
        String currency,
        List<PricingAdjustment> pricingBreakdown,
        String status,
        String guestName,
        String guestEmail,
        String paymentReference,
        String experimentVariant,
        Instant cancelledAt,
        Instant createdAt
) {}
