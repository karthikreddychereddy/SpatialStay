package com.spatialstay.booking;

import java.time.LocalDate;

/**
 * Internal command for creating an inventory hold.
 *
 * Price, total amount, currency and expiration are deliberately NOT
 * accepted from the caller.
 *
 * Those values are calculated authoritatively by the backend.
 */
public record CreateInventoryHoldRequest(

        String hotelSlug,

        String roomSlug,

        LocalDate checkIn,

        LocalDate checkOut,

        Integer guests,

        Integer units
) {
}