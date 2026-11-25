package com.stayscale.core.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HotelDetailResponse(
        UUID id,
        String name,
        String city,
        String country,
        String neighborhood,
        String address,
        double latitude,
        double longitude,
        String landmarkName,
        double distanceToCenterKm,
        int starRating,
        double guestScore,
        int reviewCount,
        String propertyType,
        String description,
        String imageUrl,
        List<String> galleryUrls,
        List<String> amenities,
        LocalDate checkIn,
        LocalDate checkOut,
        int totalNights,
        int guestsRequested,
        int roomsRequested,
        List<RoomTypeResponse> roomTypes
) {}
