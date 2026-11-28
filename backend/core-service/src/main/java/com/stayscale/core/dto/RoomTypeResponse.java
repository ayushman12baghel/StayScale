package com.stayscale.core.dto;

import com.stayscale.core.pricing.PriceQuoteResponse;

import java.util.UUID;

/**
 * Room-type availability and live dynamic pricing returned on the hotel detail page.
 */
public record RoomTypeResponse(
        UUID id,
        String name,
        String description,
        String bedType,
        int roomSizeSqm,
        int maxGuests,
        long nightlyPriceMinor,
        long totalStayPriceMinor,
        String currency,
        int totalUnits,
        int availableRooms,
        boolean freeCancellation,
        boolean breakfastIncluded,
        String imageUrl,
        PriceQuoteResponse dynamicPricing
) {}
