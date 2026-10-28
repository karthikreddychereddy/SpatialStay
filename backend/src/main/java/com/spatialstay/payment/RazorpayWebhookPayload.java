package com.spatialstay.payment;

public record RazorpayWebhookPayload(
        String eventType,
        String paymentId,
        String orderId
) {
}