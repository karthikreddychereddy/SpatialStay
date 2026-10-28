package com.spatialstay.hoteloperations;

import java.math.BigDecimal;

public record UpdateHotelProfileRequest(
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
        String contactPhone
) {}
