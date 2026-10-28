package com.spatialstay.hoteloperations;

import com.spatialstay.booking.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record HotelOperationsDashboardResponse(
        HotelSummary hotel,
        LocalDate businessDate,
        long todayArrivals,
        long todayDepartures,
        long currentlyStaying,
        long upcomingReservations,
        long totalRoomUnits,
        long occupiedRoomUnits,
        long heldRoomUnits,
        long availableRoomUnits,
        BigDecimal confirmedBookingValue,
        String currency,
        List<RecentReservation> recentReservations
) {
    public record HotelSummary(
            Long id,
            String slug,
            String name,
            String city,
            String country,
            String status
    ) {}

    public record RecentReservation(
            UUID bookingPublicId,
            String bookingReference,
            String guestName,
            String roomName,
            LocalDate checkIn,
            LocalDate checkOut,
            Integer guests,
            Integer rooms,
            BigDecimal totalAmount,
            String currency,
            BookingStatus status,
            Instant createdAt
    ) {}
}
