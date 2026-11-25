package com.stayscale.core.service;

import com.stayscale.core.dto.CitySummaryResponse;
import com.stayscale.core.dto.HotelSearchCriteria;
import com.stayscale.core.dto.HotelSearchItemResponse;
import com.stayscale.core.dto.PagedSearchResponse;
import com.stayscale.core.exception.InvalidSearchException;
import com.stayscale.core.exception.ResourceNotFoundException;
import com.stayscale.core.repository.HotelSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HotelCatalogServiceTest {

    @Mock
    private HotelSearchRepository repository;

    private HotelCatalogService service;

    @BeforeEach
    void setUp() {
        service = new HotelCatalogService(repository);
    }

    @Test
    void rejectsCheckoutBeforeOrEqualCheckin() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        assertThrows(InvalidSearchException.class, () ->
                service.searchHotels("Goa", null, tomorrow, tomorrow, 2, 1,
                        null, null, null, null, "recommended", 0, 12));
    }

    @Test
    void rejectsStaysOverThirtyNights() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(31);
        assertThrows(InvalidSearchException.class, () ->
                service.searchHotels("Goa", null, checkIn, checkOut, 2, 1,
                        null, null, null, null, "recommended", 0, 12));
    }

    @Test
    void computesGuestsPerRoomAndPagedTotalsAccurately() {
        LocalDate checkIn = LocalDate.now().plusDays(5);
        LocalDate checkOut = checkIn.plusDays(3);
        HotelSearchItemResponse item = new HotelSearchItemResponse(
                UUID.randomUUID(), "Aurelia Calangute Resort", "Goa", "India",
                "Calangute", "14 Calangute Ave", 15.54, 73.75, "Calangute Beach", 0.5,
                5, 9.1, 412, "Beach Resort", "/images/hotels/goa-resort.jpg",
                List.of("Pool", "Beachfront"), 450_000L, 2_700_000L, "INR", 3, 4, true, true
        );
        when(repository.searchHotels(any())).thenReturn(new HotelSearchRepository.SearchQueryResult(List.of(item), 16));

        PagedSearchResponse res = service.searchHotels(
                "Goa", null, checkIn, checkOut, 5, 2,
                null, 8.0, 4, List.of("Pool", " "), "price_asc", 0, 10
        );

        ArgumentCaptor<HotelSearchCriteria> captor = ArgumentCaptor.forClass(HotelSearchCriteria.class);
        verify(repository).searchHotels(captor.capture());
        HotelSearchCriteria criteria = captor.getValue();
        assertEquals(3, criteria.totalNights());
        assertEquals(3, criteria.guestsPerRoom()); // ceil(5 / 2) = 3
        assertEquals(List.of("Pool"), criteria.amenities());
        assertEquals(2, res.totalPages());
        assertEquals(16, res.totalElements());
        assertEquals(1, res.items().size());
    }

    @Test
    void throwsNotFoundWhenHotelMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findHotelDetail(eq(id), any(), any(), anyInt(), anyInt())).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () ->
                service.getHotelDetail(id, LocalDate.now().plusDays(1), LocalDate.now().plusDays(3), 2, 1));
    }

    @Test
    void forwardsCitySummaries() {
        when(repository.findCitySummaries()).thenReturn(List.of(
                new CitySummaryResponse("Goa", "India", 16, 340_000L, "INR", "/images/hotels/goa-resort.jpg", "Beaches", 15.5, 73.7)
        ));
        assertEquals(1, service.listCities().size());
    }
}
