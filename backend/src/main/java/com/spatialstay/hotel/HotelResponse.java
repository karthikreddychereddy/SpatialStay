package com.spatialstay.hotel;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record HotelResponse(
        Long id,
        String slug,
        String name,
        String description,
        String city,
        String state,
        String country,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal rating,
        Integer reviewCount,
        BigDecimal startingPrice,
        String heroImageUrl,
        List<String> imageUrls,
        Boolean verified,
        String status,
        String contactEmail,
        String contactPhone,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        String checkInInstructions,
        Set<String> amenities,
        String houseRules,
        String childPolicy,
        String petPolicy,
        String smokingPolicy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}