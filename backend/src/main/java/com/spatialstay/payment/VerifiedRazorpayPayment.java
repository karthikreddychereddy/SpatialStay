package com.spatialstay.payment;

public record VerifiedRazorpayPayment(
        String paymentId,
        String orderId,
        long amountInSubunits,
        String currency,
        String status,
        boolean captured
) {
}