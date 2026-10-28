package com.spatialstay.modificationrefund;


import java.util.UUID;

public record ModificationRefundProcessingRequestedEvent(
        UUID batchPublicId
) {
    public ModificationRefundProcessingRequestedEvent {
        if (batchPublicId == null) {
            throw new IllegalArgumentException(
                    "Modification refund batch ID is required."
            );
        }
    }
}