package com.spatialstay.hoteloperations;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record HotelRoomManagementResponse(
        Long roomId,
        Long hotelId,
        String slug,
        String name,
        String roomType,
        Integer maxGuests,
        Integer totalUnits,
        BigDecimal pricePerNight,
        String currency,
        String status,
        Boolean spatialAvailable,
        LocalDateTime updatedAt
) {}
