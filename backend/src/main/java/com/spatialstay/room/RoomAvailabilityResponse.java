package com.spatialstay.room;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RoomAvailabilityResponse(
        Long roomId,
        String roomSlug,
        String roomName,
        LocalDate checkIn,
        LocalDate checkOut,
        long nights,
        Integer guests,
        Integer maxGuests,
        Integer totalUnits,
        Integer availableUnits,
        boolean available,
        BigDecimal pricePerNight,
        BigDecimal estimatedStayTotal,
        String currency
) {
}