package com.spatialstay.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookingResponse(
        UUID bookingId,
        String bookingReference,
        UUID holdId,

        String hotelSlug,
        String hotelName,

        String roomSlug,
        String roomName,

        LocalDate checkIn,
        LocalDate checkOut,
        Integer nights,

        Integer guests,
        Integer units,

        BigDecimal pricePerNight,
        BigDecimal totalAmount,
        String currency,

        BookingStatus status,

        Instant createdAt,
        Instant updatedAt,
        Instant confirmedAt,
        Instant cancelledAt,
        Instant expiredAt
) {
}