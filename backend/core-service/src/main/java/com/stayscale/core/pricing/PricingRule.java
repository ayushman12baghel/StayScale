package com.stayscale.core.pricing;

import java.util.Optional;

/**
 * Open/Closed Strategy contract for composable dynamic pricing rules.
 * Adding a new rule only requires registering a new Spring {@code @Component}
 * implementing this interface without touching {@link DynamicPricingEngine}.
 */
public interface PricingRule {
    Optional<PricingAdjustment> evaluate(PricingContext context);
}
