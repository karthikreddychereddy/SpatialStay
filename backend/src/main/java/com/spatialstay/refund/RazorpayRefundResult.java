package com.spatialstay.refund;

public record RazorpayRefundResult(
        String providerRefundId,
        String providerPaymentId,
        long amountInPaise,
        String currency,
        String providerStatus
) {
}