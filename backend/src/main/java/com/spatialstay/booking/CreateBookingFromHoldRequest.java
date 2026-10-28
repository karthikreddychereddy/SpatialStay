package com.spatialstay.booking;

import java.util.UUID;

public record CreateBookingFromHoldRequest(
        UUID holdId
) {
}