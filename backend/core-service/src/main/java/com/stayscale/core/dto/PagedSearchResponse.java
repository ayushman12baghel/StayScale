package com.stayscale.core.dto;

import java.time.LocalDate;
import java.util.List;

public record PagedSearchResponse(
        List<HotelSearchItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        String city,
        LocalDate checkIn,
        LocalDate checkOut,
        int totalNights,
        int guests,
        int rooms,
        long queryDurationMs
) {}
