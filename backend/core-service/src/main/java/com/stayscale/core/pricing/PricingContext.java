package com.stayscale.core.pricing;

import java.time.LocalDate;

/**
 * Immutable input passed to each {@link PricingRule} for a single stay night.
 */
public record PricingContext(
                long baseNightlyMinor,
                LocalDate stayDate,
                LocalDate bookingDate,
                int totalRooms,
                int availableRooms,
                int roomsRequested) {
}