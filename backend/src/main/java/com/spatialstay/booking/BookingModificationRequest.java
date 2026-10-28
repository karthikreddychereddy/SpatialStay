package com.spatialstay.booking;

import java.time.LocalDate;

public record BookingModificationRequest(
        String roomSlug,
        LocalDate checkIn,
        LocalDate checkOut,
        Integer guests,
        Integer units
) {
}