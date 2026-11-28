package com.stayscale.core.pricing;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.Optional;

/**
 * Adds +12% demand adjustment for Friday and Saturday stay nights.
 */
@Component
@Order(2)
public class WeekendDemandRule implements PricingRule {

    @Override
    public Optional<PricingAdjustment> evaluate(PricingContext context) {
        DayOfWeek dow = context.stayDate().getDayOfWeek();
        if (dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY) {
            long delta = Math.round(context.baseNightlyMinor() * 0.12);
            return Optional.of(new PricingAdjustment(
                    "WEEKEND_DEMAND",
                    "Weekend Stay Demand (+12%)",
                    12,
                    delta,
                    "Friday/Saturday peak leisure night"
            ));
        }
        return Optional.empty();
    }
}
