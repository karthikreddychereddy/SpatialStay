package com.spatialstay.payment;

public enum PaymentWebhookEventStatus {

    /*
     * Signature verified and webhook durably recorded.
     *
     * No payment/booking state transition has happened yet.
     */
    RECEIVED,

    /*
     * Step 10.8 successfully applied the authoritative
     * payment confirmation transaction.
     */
    PROCESSED,

    /*
     * The provider event was valid but could not safely
     * confirm the booking.
     *
     * Examples:
     * - expired inventory hold
     * - amount mismatch
     * - currency mismatch
     *
     * Step 10.8 will use this for reconciliation/refund cases.
     */
    REQUIRES_RECONCILIATION
}