package com.spatialstay.refund;

import java.util.UUID;

/**
 * Internal application event published when a durable refund obligation
 * has been created successfully.
 *
 * The event itself does not contact the payment provider.
 *
 * RefundProcessingRequestedEventListener consumes this event only after
 * the surrounding database transaction has committed.
 */
public record RefundProcessingRequestedEvent(
        UUID refundPublicId
) {

    public RefundProcessingRequestedEvent {
        if (refundPublicId == null) {
            throw new IllegalArgumentException(
                    "Refund public ID is required."
            );
        }
    }
}