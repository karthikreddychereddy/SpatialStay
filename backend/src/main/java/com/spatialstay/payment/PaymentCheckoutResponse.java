package com.spatialstay.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCheckoutResponse(

        UUID paymentId,

        UUID bookingId,

        String bookingReference,

        PaymentProvider provider,

        PaymentStatus paymentStatus,

        BigDecimal amount,

        long amountInSubunits,

        String currency,

        String providerOrderId,

        String checkoutKeyId,

        Instant holdExpiresAt

) {
}