package com.stayscale.core.service;

import com.stayscale.core.dto.CitySummaryResponse;
import com.stayscale.core.dto.HotelDetailResponse;
import com.stayscale.core.dto.HotelSearchCriteria;
import com.stayscale.core.dto.PagedSearchResponse;
import com.stayscale.core.exception.InvalidSearchException;
import com.stayscale.core.exception.ResourceNotFoundException;
import com.stayscale.core.repository.HotelSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Business logic layer for hotel discovery and availability validation.
 *
 * <p>Marked {@code @Transactional(readOnly = true)} so PostgreSQL and HikariCP can optimize
 * read queries without dirty-checking overhead.
 */
@Service
@Transactional(readOnly = true)
public class HotelCatalogService {

    private static final int MAX_NIGHTS = 30;
    private static final int MAX_PAGE_SIZE = 50;

    private final HotelSearchRepository repository;

    public HotelCatalogService(HotelSearchRepository repository) {
        this.repository = repository;
    }

    /** Returns summary statistics for all seeded cities. */
    public List<CitySummaryResponse> listCities() {
        return repository.findCitySummaries();
    }

    /**
     * Validates search dates/occupancy, applies safe defaults (tomorrow -> +2 nights),
     * queries matching hotels, and records query latency in milliseconds for UI/debugging visibility.
     */
    public PagedSearchResponse searchHotels(
            String city,
            String query,
            LocalDate checkIn,
            LocalDate checkOut,
            int guests,
            int rooms,
            Long maxPriceMinor,
            Double minRating,
            Integer minStars,
            List<String> amenities,
            String sort,
            int page,
            int size
    ) {
        LocalDate effectiveCheckIn = checkIn != null ? checkIn : LocalDate.now().plusDays(1);
        LocalDate effectiveCheckOut = checkOut != null ? checkOut : effectiveCheckIn.plusDays(2);
        validateDatesAndOccupancy(effectiveCheckIn, effectiveCheckOut, guests, rooms);

        if (page < 0) {
            throw new InvalidSearchException("page must be >= 0");
        }
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        List<String> cleanAmenities = amenities == null ? List.of() :
                amenities.stream().map(String::trim).filter(s -> !s.isBlank()).toList();

        HotelSearchCriteria criteria = new HotelSearchCriteria(
                city,
                query,
                effectiveCheckIn,
                effectiveCheckOut,
                guests,
                rooms,
                maxPriceMinor,
                minRating,
                minStars,
                cleanAmenities,
                sort,
                page,
                safeSize
        );

        long startNanos = System.nanoTime();
        HotelSearchRepository.SearchQueryResult result = repository.searchHotels(criteria);
        long elapsedMs = Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L);

        int totalPages = (int) Math.ceil((double) result.totalElements() / safeSize);

        return new PagedSearchResponse(
                result.items(),
                page,
                safeSize,
                result.totalElements(),
                totalPages,
                city != null && !city.isBlank() ? city.trim() : "All Destinations",
                effectiveCheckIn,
                effectiveCheckOut,
                criteria.totalNights(),
                guests,
                rooms,
                elapsedMs
        );
    }

    /**
     * Fetches full hotel details and per-room availability for the requested stay window.
     */
    public HotelDetailResponse getHotelDetail(
            UUID hotelId,
            LocalDate checkIn,
            LocalDate checkOut,
            int guests,
            int rooms
    ) {
        LocalDate effectiveCheckIn = checkIn != null ? checkIn : LocalDate.now().plusDays(1);
        LocalDate effectiveCheckOut = checkOut != null ? checkOut : effectiveCheckIn.plusDays(2);
        validateDatesAndOccupancy(effectiveCheckIn, effectiveCheckOut, guests, rooms);

        return repository.findHotelDetail(hotelId, effectiveCheckIn, effectiveCheckOut, guests, rooms)
                .orElseThrow(() -> new ResourceNotFoundException("Hotel not found: " + hotelId));
    }

    /**
     * Guards the repository from invalid or abusively wide date scans (e.g., 10-year range scans).
     */
    private void validateDatesAndOccupancy(LocalDate checkIn, LocalDate checkOut, int guests, int rooms) {
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidSearchException("checkOut date must be strictly after checkIn date");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > MAX_NIGHTS) {
            throw new InvalidSearchException("Stay duration cannot exceed " + MAX_NIGHTS + " nights");
        }
        // Allow -1 day tolerance so travelers in earlier timezones can still search "today".
        if (checkIn.isBefore(LocalDate.now().minusDays(1))) {
            throw new InvalidSearchException("checkIn date cannot be in the past");
        }
        if (guests < 1 || guests > 16) {
            throw new InvalidSearchException("guests must be between 1 and 16");
        }
        if (rooms < 1 || rooms > 8) {
            throw new InvalidSearchException("rooms must be between 1 and 8");
        }
    }
}
