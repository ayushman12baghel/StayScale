package com.stayscale.core.repository;

import com.stayscale.core.dto.HotelSearchCriteria;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Live PostgreSQL integration checks verifying JSONB GIN amenity filters,
 * price ceilings, and multi-night queries.
 */
@SpringBootTest
class HotelSearchIntegrationTest {

    @Autowired
    private HotelSearchRepository repository;

    @BeforeAll
    static void requirePostgres() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("localhost", 5433), 500);
        } catch (Exception e) {
            Assumptions.assumeTrue(false, "Requires local Docker Postgres on port 5433");
        }
    }

    @Test
    void filtersByAmenitiesPriceAndCityWithoutSqlErrors() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(2);

        var res = repository.searchHotels(new HotelSearchCriteria(
                "Bengaluru",
                "Bengaluru",
                checkIn,
                checkOut,
                2,
                1,
                450_000L,
                null,
                null,
                List.of("Free WiFi"),
                "recommended",
                0,
                10
        ));

        assertTrue(res.totalElements() >= 1, "Expected Bengaluru hotels matching Free WiFi <= ₹4,500");
        assertTrue(res.items().stream().allMatch(h -> h.amenities().contains("Free WiFi")));
        assertTrue(res.items().stream().allMatch(h -> h.startingNightlyPriceMinor() <= 450_000L));
    }

    @Test
    void filtersByMultipleAmenitiesViaJsonbContainment() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(2);

        var res = repository.searchHotels(new HotelSearchCriteria(
                "Goa",
                null,
                checkIn,
                checkOut,
                2,
                1,
                null,
                8.0,
                4,
                List.of("Free WiFi", "Pool", "Beachfront"),
                "price_asc",
                0,
                12
        ));

        assertTrue(res.totalElements() >= 1, "Expected Goa hotels matching multiple amenities");
        assertTrue(res.items().stream().allMatch(h ->
                h.amenities().containsAll(List.of("Free WiFi", "Pool", "Beachfront"))));
    }

    @Test
    void searchCardPriceAlwaysMatchesCheapestBookableRoomOnHotelDetail() {
        // Test across a 40-day-ahead weekend window (triggers Early Bird -10% AND Weekend +12%)
        LocalDate checkIn = LocalDate.now().plusDays(40);
        while (checkIn.getDayOfWeek().getValue() != 5) { // Friday
            checkIn = checkIn.plusDays(1);
        }
        LocalDate checkOut = checkIn.plusDays(2); // Fri + Sat nights

        var search = repository.searchHotels(new HotelSearchCriteria(
                "Kochi",
                null,
                checkIn,
                checkOut,
                2,
                1,
                null,
                null,
                null,
                List.of(),
                "price_asc",
                0,
                5
        ));

        assertFalse(search.items().isEmpty());
        for (var card : search.items()) {
            var detail = repository.findHotelDetail(card.id(), checkIn, checkOut, 2, 1).orElseThrow();
            var cheapestBookable = detail.roomTypes().stream()
                    .filter(r -> r.availableRooms() >= 1 && r.maxGuests() >= 2)
                    .min(java.util.Comparator.comparingLong(r -> r.nightlyPriceMinor()))
                    .orElseThrow();
            assertEquals(cheapestBookable.nightlyPriceMinor(), card.startingNightlyPriceMinor(),
                    "Search card nightly price must equal cheapest bookable room dynamic price for " + card.name());
            assertEquals(cheapestBookable.totalStayPriceMinor(), card.totalStayPriceMinor(),
                    "Search card total stay price must equal cheapest bookable room total for " + card.name());
        }
    }
}
