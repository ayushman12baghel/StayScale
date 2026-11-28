package com.stayscale.core.pricing;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates a composable chain of {@link PricingRule}s across every stay night
 * to produce an auditable, itemized {@link PriceQuoteResponse}.
 */
@Service
public class DynamicPricingEngine {

    private final List<PricingRule> rules;

    public DynamicPricingEngine(List<PricingRule> rules) {
        this.rules = rules;
    }

    public PriceQuoteResponse calculateQuote(
            long baseNightlyMinor,
            String currency,
            int rooms,
            List<NightInventorySnapshot> nights,
            LocalDate bookingDate
    ) {
        if (nights == null || nights.isEmpty()) {
            return new PriceQuoteResponse(baseNightlyMinor, baseNightlyMinor, 0L, 0L, currency, 0, rooms, List.of(), List.of());
        }

        List<NightlyPriceQuote> nightlyQuotes = new ArrayList<>(nights.size());
        Map<String, SummaryAccumulator> summaryMap = new LinkedHashMap<>();

        long totalBasePerRoom = 0L;
        long totalDynamicPerRoom = 0L;

        for (NightInventorySnapshot night : nights) {
            PricingContext ctx = new PricingContext(
                    baseNightlyMinor,
                    night.stayDate(),
                    bookingDate,
                    night.totalRooms(),
                    night.availableRooms(),
                    rooms
            );

            List<PricingAdjustment> nightAdjustments = new ArrayList<>();
            long nightDelta = 0L;

            for (PricingRule rule : rules) {
                var maybeAdj = rule.evaluate(ctx);
                if (maybeAdj.isPresent()) {
                    PricingAdjustment adj = maybeAdj.get();
                    nightAdjustments.add(adj);
                    nightDelta += adj.amountDeltaMinor();

                    // Aggregate each rule across all nights & requested rooms for clean checkout UI display.
                    summaryMap.computeIfAbsent(adj.ruleCode(), k -> new SummaryAccumulator(adj))
                            .add(adj.amountDeltaMinor() * rooms);
                }
            }

            long dynamicNight = Math.max(10_000L, baseNightlyMinor + nightDelta);
            totalBasePerRoom += baseNightlyMinor;
            totalDynamicPerRoom += dynamicNight;

            nightlyQuotes.add(new NightlyPriceQuote(
                    night.stayDate(),
                    baseNightlyMinor,
                    dynamicNight,
                    night.availableRooms(),
                    nightAdjustments
            ));
        }

        long totalBase = totalBasePerRoom * rooms;
        long totalStay = totalDynamicPerRoom * rooms;
        long avgNightly = Math.round((double) totalDynamicPerRoom / nights.size());

        List<PricingAdjustment> summaries = summaryMap.values().stream()
                .map(SummaryAccumulator::toAdjustment)
                .toList();

        return new PriceQuoteResponse(
                baseNightlyMinor,
                avgNightly,
                totalBase,
                totalStay,
                currency,
                nights.size(),
                rooms,
                summaries,
                nightlyQuotes
        );
    }

    public record NightInventorySnapshot(LocalDate stayDate, int totalRooms, int availableRooms) {}

    private static final class SummaryAccumulator {
        private final String ruleCode;
        private final String label;
        private final int percentageDelta;
        private final String explanation;
        private long totalAmountMinor = 0L;
        private int nightCount = 0;

        private SummaryAccumulator(PricingAdjustment seed) {
            this.ruleCode = seed.ruleCode();
            this.label = seed.label();
            this.percentageDelta = seed.percentageDelta();
            this.explanation = seed.explanation();
        }

        private void add(long deltaMinor) {
            this.totalAmountMinor += deltaMinor;
            this.nightCount++;
        }

        private PricingAdjustment toAdjustment() {
            String desc = nightCount > 1
                    ? explanation + " (applied on " + nightCount + " nights)"
                    : explanation;
            return new PricingAdjustment(ruleCode, label, percentageDelta, totalAmountMinor, desc);
        }
    }
}
