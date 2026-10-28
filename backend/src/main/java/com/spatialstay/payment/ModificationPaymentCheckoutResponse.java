package com.spatialstay.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ModificationPaymentCheckoutResponse(

        UUID paymentId,

        UUID bookingId,

        UUID modificationId,

        String bookingReference,

        PaymentProvider provider,

        PaymentStatus paymentStatus,

        PaymentPurpose purpose,

        BigDecimal amount,

        long amountInSubunits,

        String currency,

        String providerOrderId,

        String checkoutKeyId,

        Instant holdExpiresAt

) {
}