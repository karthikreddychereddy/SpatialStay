package com.spatialstay.booking;

public enum CancellationEligibilityReason {

    ELIGIBLE,

    MANAGEMENT_COOLDOWN,

    BOOKING_NOT_CONFIRMED,

    CHECK_IN_ALREADY_STARTED,

    NON_REFUNDABLE_WINDOW
}