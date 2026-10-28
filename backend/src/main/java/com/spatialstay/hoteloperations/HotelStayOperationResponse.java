package com.spatialstay.hoteloperations;

import java.time.Instant;
import java.util.UUID;

public record HotelStayOperationResponse(
        UUID bookingId,
        String bookingReference,
        StayStatus stayStatus,
        Instant checkedInAt,
        Instant checkedOutAt
) {}
