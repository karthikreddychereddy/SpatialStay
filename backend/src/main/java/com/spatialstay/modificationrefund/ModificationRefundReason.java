package com.spatialstay.modificationrefund;

public enum ModificationRefundReason {

    /*
     * Customer moved to a cheaper reservation.
     */
    PRICE_DECREASE,

    /*
     * Razorpay captured an additional modification payment after
     * its replacement inventory could no longer be committed.
     */
    LATE_PAYMENT_COMPENSATION
}