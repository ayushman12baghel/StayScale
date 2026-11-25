package com.stayscale.core.dto;

import java.util.List;
import java.util.UUID;

public record HotelSearchItemResponse(
        UUID id,
        String name,
        String city,
        String country,
        String neighborhood,
        String address,
        double latitude,
        double longitude,
        String landmarkName,
        double distanceToCenterKm,
        int starRating,
        double guestScore,
        int reviewCount,
        String propertyType,
        String imageUrl,
        List<String> amenities,
        long startingNightlyPriceMinor,
        long totalStayPriceMinor,
        String currency,
        int totalNights,
        int minAvailableRooms,
        boolean freeCancellationAvailable,
        boolean breakfastIncludedAvailable
) {}
