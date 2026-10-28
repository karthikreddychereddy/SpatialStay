package com.spatialstay.modificationrefund;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ModificationRefundProcessingEventListener {

    private final ModificationRefundProcessingService
            processingService;

    public ModificationRefundProcessingEventListener(
            ModificationRefundProcessingService processingService
    ) {
        this.processingService =
                processingService;
    }

    /*
     * Razorpay must only be contacted after the booking modification,
     * replacement hold consumption and durable refund obligations have
     * committed successfully.
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onRefundRequested(
            ModificationRefundProcessingRequestedEvent event
    ) {
        try {
            processingService.processBatch(
                    event.batchPublicId()
            );

        } catch (RuntimeException exception) {

            /*
             * Do not propagate provider-processing failures back into
             * the already committed booking transaction.
             *
             * Durable PENDING/PROCESSING/RECONCILIATION records remain
             * available for recovery.
             */
        }
    }
}