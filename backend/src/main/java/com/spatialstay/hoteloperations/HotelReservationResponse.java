package com.spatialstay.hoteloperations;

import com.spatialstay.booking.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record HotelReservationResponse(
        UUID bookingId,
        String bookingReference,
        BookingStatus bookingStatus,
        StayStatus stayStatus,
        String guestName,
        String guestEmail,
        String roomName,
        String roomType,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer guests,
        Integer rooms,
        BigDecimal totalAmount,
        String currency,
        Instant confirmedAt,
        Instant checkedInAt,
        Instant checkedOutAt
) {}
