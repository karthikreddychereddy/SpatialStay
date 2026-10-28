package com.spatialstay.room;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public record RoomResponse(
        Long id,
        String slug,
        String name,
        String description,
        String roomType,
        Integer maxGuests,
        String bedType,
        Integer roomSizeSqFt,
        BigDecimal pricePerNight,
        String currency,
        String heroImageUrl,
        List<String> imageUrls,
        Boolean spatialAvailable,
        String status,
        Long hotelId,
        String hotelSlug,
        String hotelName,
        Set<AmenityResponse> amenities,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public record AmenityResponse(
            String code,
            String name
    ) {
    }
}