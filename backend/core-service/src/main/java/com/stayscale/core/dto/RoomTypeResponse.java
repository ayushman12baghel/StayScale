package com.stayscale.core.dto;

import java.util.UUID;

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
        String imageUrl
) {}
