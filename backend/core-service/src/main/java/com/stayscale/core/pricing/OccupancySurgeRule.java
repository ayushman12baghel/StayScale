package com.stayscale.core.pricing;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Applies scarcity-based surge pricing when hotel/room occupancy exceeds 75% (+15%) or 90% (+30%).
 */
@Component
@Order(1)
public class OccupancySurgeRule implements PricingRule {

    @Override
    public Optional<PricingAdjustment> evaluate(PricingContext context) {
        if (context.totalRooms() <= 0) {
            return Optional.empty();
        }
        int booked = Math.max(0, context.totalRooms() - context.availableRooms());
        double occupancyPct = (booked * 100.0) / context.totalRooms();

        if (occupancyPct > 90.0) {
            long delta = Math.round(context.baseNightlyMinor() * 0.30);
            return Optional.of(new PricingAdjustment(
                    "OCCUPANCY_SURGE_90",
                    "High Demand Surge (+30%)",
                    30,
                    delta,
                    String.format("Occupancy is %.0f%% (>90%% booked)", occupancyPct)
            ));
        }
        if (occupancyPct > 75.0) {
            long delta = Math.round(context.baseNightlyMinor() * 0.15);
            return Optional.of(new PricingAdjustment(
                    "OCCUPANCY_SURGE_75",
                    "Peak Occupancy Surge (+15%)",
                    15,
                    delta,
                    String.format("Occupancy is %.0f%% (>75%% booked)", occupancyPct)
            ));
        }
        return Optional.empty();
    }
}
