package com.stayscale.core.dto;

public record CitySummaryResponse(
        String city,
        String country,
        long hotelCount,
        long minNightlyPriceMinor,
        String currency,
        String sampleImageUrl,
        String tagline,
        double centerLat,
        double centerLng
) {}
