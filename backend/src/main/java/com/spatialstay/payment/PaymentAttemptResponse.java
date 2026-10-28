package com.spatialstay.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


public record PaymentAttemptResponse(

        UUID paymentId,

        UUID bookingId,

        String bookingReference,

        PaymentProvider provider,

        BigDecimal amount,

        String currency,

        PaymentStatus status,

        String providerOrderId,

        Instant expiresAt,

        Instant createdAt,

        Instant updatedAt

) {

    public static PaymentAttemptResponse from(
            PaymentAttempt payment
    ) {

        if (payment == null) {
            throw new IllegalArgumentException(
                    "Payment attempt is required."
            );
        }

        return new PaymentAttemptResponse(
                payment.getPublicId(),

                payment.getBooking()
                        .getPublicId(),

                payment.getBooking()
                        .getBookingReference(),

                payment.getProvider(),

                payment.getExpectedAmount(),

                payment.getCurrency(),

                payment.getStatus(),

                payment.getProviderOrderId(),

                payment.getExpiresAt(),

                payment.getCreatedAt(),

                payment.getUpdatedAt()
        );
    }
}