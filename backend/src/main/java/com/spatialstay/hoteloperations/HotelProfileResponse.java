package com.spatialstay.hoteloperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

public record HotelProfileResponse(
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
        String heroImageUrl,
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
        Boolean verified,
        String status,
        LocalDateTime updatedAt
) {}
