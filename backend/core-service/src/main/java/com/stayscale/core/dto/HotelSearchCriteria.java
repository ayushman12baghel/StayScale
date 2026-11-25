package com.stayscale.core.dto;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Normalized search parameters passed from service layer to {@code HotelSearchRepository}.
 */
public record HotelSearchCriteria(
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
    /** Check-out day is exclusive (e.g., Feb 10 to Feb 12 = 2 nights: Feb 10 & Feb 11). */
    public int totalNights() {
        return (int) ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /** Minimum per-room guest capacity needed when splitting total guests across rooms (e.g., 5 guests / 2 rooms = 3). */
    public int guestsPerRoom() {
        return (int) Math.ceil((double) guests / rooms);
    }
}
