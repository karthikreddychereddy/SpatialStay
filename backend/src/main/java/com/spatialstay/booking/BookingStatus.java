package com.spatialstay.booking;

/**
 * Booking lifecycle states.
 *
 * Step 9 may create PENDING_PAYMENT bookings. CONFIRMED is intentionally
 * reserved for Step 10 after a trusted payment-provider webhook is verified.
 */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    EXPIRED,
    CANCELLED
}
