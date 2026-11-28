package com.stayscale.core.pricing;

/**
 * Represents one transparent pricing adjustment rule applied to a night or stay.
 */
public record PricingAdjustment(
        String ruleCode,
        String label,
        int percentageDelta,
        long amountDeltaMinor,
        String explanation
) {}
