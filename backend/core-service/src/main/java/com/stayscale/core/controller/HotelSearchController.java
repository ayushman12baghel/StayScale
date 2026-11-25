package com.stayscale.core.controller;

import com.stayscale.core.dto.CitySummaryResponse;
import com.stayscale.core.dto.HotelDetailResponse;
import com.stayscale.core.dto.PagedSearchResponse;
import com.stayscale.core.service.HotelCatalogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Public REST endpoints for browsing destinations, searching available hotels,
 * and viewing room-level availability.
 */
@RestController
@RequestMapping("/api/v1/hotels")
public class HotelSearchController {

    private final HotelCatalogService catalogService;

    public HotelSearchController(HotelCatalogService catalogService) {
        this.catalogService = catalogService;
    }

    /** Lists all cities with hotel counts, starting prices, and average map centers. */
    @GetMapping("/cities")
    public List<CitySummaryResponse> listCities() {
        return catalogService.listCities();
    }

    /**
     * Multi-criteria search across available hotels.
     *
     * @param maxPrice Optional nightly price ceiling in minor currency units (paise, e.g., 500000 = ₹5,000).
     * @param amenities Comma-separated amenity names matched via PostgreSQL JSONB GIN index.
     */
    @GetMapping("/search")
    public PagedSearchResponse searchHotels(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(defaultValue = "2") int guests,
            @RequestParam(defaultValue = "1") int rooms,
            @RequestParam(required = false) Long maxPrice,
            @RequestParam(required = false) Double minRating,
            @RequestParam(required = false) Integer minStars,
            @RequestParam(required = false) List<String> amenities,
            @RequestParam(defaultValue = "recommended") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return catalogService.searchHotels(
                city, query, checkIn, checkOut, guests, rooms,
                maxPrice, minRating, minStars, amenities, sort, page, size
        );
    }

    /** Returns full details and room-by-room availability for a single hotel UUID. */
    @GetMapping("/{id}")
    public HotelDetailResponse getHotelDetail(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(defaultValue = "2") int guests,
            @RequestParam(defaultValue = "1") int rooms
    ) {
        return catalogService.getHotelDetail(id, checkIn, checkOut, guests, rooms);
    }
}
