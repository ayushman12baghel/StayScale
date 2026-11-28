package com.stayscale.core.pricing;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Rewards guests booking more than 30 days ahead with a -10% discount.
 */
@Component
@Order(3)
public class EarlyBirdDiscountRule implements PricingRule {

    @Override
    public Optional<PricingAdjustment> evaluate(PricingContext context) {
        long leadDays = ChronoUnit.DAYS.between(context.bookingDate(), context.stayDate());
        if (leadDays > 30) {
            long delta = -Math.round(context.baseNightlyMinor() * 0.10);
            return Optional.of(new PricingAdjustment(
                    "EARLY_BIRD_30",
                    "Early Bird Discount (-10%)",
                    -10,
                    delta,
                    String.format("Booked %d days in advance (>30 days)", leadDays)
            ));
        }
        return Optional.empty();
    }
}
