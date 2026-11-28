package com.stayscale.core.pricing;

import java.util.List;

/**
 * Full dynamic pricing calculation for a room type across all nights and requested rooms.
 */
public record PriceQuoteResponse(
        long baseNightlyMinor,
        long averageDynamicNightlyMinor,
        long totalBaseMinor,
        long totalStayPriceMinor,
        String currency,
        int totalNights,
        int rooms,
        List<PricingAdjustment> ruleSummaries,
        List<NightlyPriceQuote> nightlyBreakdown
) {}
