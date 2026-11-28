package com.stayscale.core.pricing;

import java.time.LocalDate;
import java.util.List;

/**
 * Itemized price breakdown for a single night of stay.
 */
public record NightlyPriceQuote(
        LocalDate stayDate,
        long baseNightlyMinor,
        long dynamicNightlyMinor,
        int availableRooms,
        List<PricingAdjustment> adjustments
) {}
