package com.stayscale.core.pricing;

import com.stayscale.core.pricing.DynamicPricingEngine.NightInventorySnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DynamicPricingEngineTest {

    private final DynamicPricingEngine engine = new DynamicPricingEngine(List.of(
            new OccupancySurgeRule(),
            new WeekendDemandRule(),
            new EarlyBirdDiscountRule()
    ));

    @Test
    void appliesPeakOccupancyAndWeekendSurge() {
        // Friday night, 9 of 10 rooms booked (90% -> >75% = +15%), plus Fri weekend (+12%)
        LocalDate friday = LocalDate.of(2026, 12, 4); // Friday
        LocalDate bookingDate = LocalDate.of(2026, 11, 25);

        PriceQuoteResponse quote = engine.calculateQuote(
                100_000L, // ₹1,000 base
                "INR",
                2,
                List.of(new NightInventorySnapshot(friday, 10, 1)),
                bookingDate
        );

        // 100_000 + 15% (15_000) + 12% (12_000) = 127_000 per room per night -> 254_000 for 2 rooms
        assertEquals(127_000L, quote.averageDynamicNightlyMinor());
        assertEquals(200_000L, quote.totalBaseMinor());
        assertEquals(254_000L, quote.totalStayPriceMinor());
        assertEquals(2, quote.ruleSummaries().size());
        assertEquals("OCCUPANCY_SURGE_75", quote.ruleSummaries().get(0).ruleCode());
        assertEquals("WEEKEND_DEMAND", quote.ruleSummaries().get(1).ruleCode());
    }

    @Test
    void appliesExtreme95PercentOccupancySurge() {
        LocalDate tuesday = LocalDate.of(2026, 12, 1); // Tuesday
        LocalDate bookingDate = LocalDate.of(2026, 11, 25);

        PriceQuoteResponse quote = engine.calculateQuote(
                200_000L,
                "INR",
                1,
                List.of(new NightInventorySnapshot(tuesday, 20, 1)), // 95% occupied -> +30%
                bookingDate
        );

        assertEquals(260_000L, quote.totalStayPriceMinor());
        assertEquals("OCCUPANCY_SURGE_90", quote.ruleSummaries().getFirst().ruleCode());
    }

    @Test
    void appliesEarlyBirdDiscountWhenBookedOver30DaysAhead() {
        LocalDate bookingDate = LocalDate.of(2026, 11, 25);
        LocalDate futureWednesday = bookingDate.plusDays(42); // Wednesday
        while (futureWednesday.getDayOfWeek().getValue() >= 5) {
            futureWednesday = futureWednesday.plusDays(1);
        }

        PriceQuoteResponse quote = engine.calculateQuote(
                400_000L,
                "INR",
                1,
                List.of(new NightInventorySnapshot(futureWednesday, 10, 8)), // 20% occupied, >30d ahead -> -10%
                bookingDate
        );

        assertEquals(360_000L, quote.totalStayPriceMinor());
        assertEquals(1, quote.ruleSummaries().size());
        assertEquals("EARLY_BIRD_30", quote.ruleSummaries().getFirst().ruleCode());
    }
}
