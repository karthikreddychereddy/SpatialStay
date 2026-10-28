package com.spatialstay.payment;

import java.time.Instant;


public record ProviderOrderResult(

        String providerOrderId,

        long amountInSubunits,

        String currency,

        String providerStatus,

        Instant createdAt

) {

    public ProviderOrderResult {

        if (providerOrderId == null
                || providerOrderId.isBlank()) {

            throw new IllegalArgumentException(
                    "Provider order ID is required."
            );
        }

        if (amountInSubunits <= 0) {

            throw new IllegalArgumentException(
                    "Provider order amount must be positive."
            );
        }

        if (currency == null
                || currency.isBlank()) {

            throw new IllegalArgumentException(
                    "Provider currency is required."
            );
        }

        providerOrderId =
                providerOrderId.trim();

        currency =
                currency.trim().toUpperCase();
    }
}