package com.spatialstay.hoteloperations;

import java.math.BigDecimal;

public record UpdateHotelRoomCommercialRequest(
        Integer totalUnits,
        BigDecimal pricePerNight,
        String currency,
        String status
) {}
