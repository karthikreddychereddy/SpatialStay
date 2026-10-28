package com.spatialstay.booking;

/**
 * Lifecycle of a temporary inventory hold.
 *
 * ACTIVE
 *     Inventory is temporarily reserved for the customer.
 *
 * CONSUMED
 *     The hold has been successfully used to create a booking.
 *
 * EXPIRED
 *     The hold reached its expiration time without being consumed.
 *
 * RELEASED
 *     The hold was intentionally released before expiration.
 */
public enum InventoryHoldStatus {

    ACTIVE,
    CONSUMED,
    EXPIRED,
    RELEASED
}