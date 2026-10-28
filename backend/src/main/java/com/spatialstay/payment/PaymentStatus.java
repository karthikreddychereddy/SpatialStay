package com.spatialstay.payment;

public enum PaymentStatus {

    /*
     * SpatialStay has created the payment attempt,
     * but no provider checkout/order has been created yet.
     */
    CREATED,

    /*
     * A provider-side payment order/session exists and
     * the customer may proceed with payment.
     */
    PENDING,

    /*
     * The provider has indicated that payment processing
     * is currently in progress.
     */
    PROCESSING,

    /*
     * SpatialStay has independently verified successful
     * payment through the trusted provider flow.
     */
    SUCCEEDED,

    /*
     * The payment provider reported that the attempt failed.
     */
    FAILED,

    /*
     * The payment attempt can no longer be used.
     */
    EXPIRED,

    /*
     * The attempt was intentionally cancelled before
     * successful payment.
     */
    CANCELLED
}