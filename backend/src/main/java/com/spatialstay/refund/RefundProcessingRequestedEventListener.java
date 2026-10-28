package com.spatialstay.refund;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;


/**
 * Starts provider refund processing only after the transaction that
 * created the durable Refund record has committed successfully.
 *
 * Important:
 *
 * BookingCancellationService
 *      -> CANCELLED + Refund PENDING
 *      -> database COMMIT
 *      -> this listener
 *      -> RefundProcessingService
 *      -> Razorpay
 *
 * This guarantees that a Razorpay refund request is never made for a
 * cancellation transaction that later rolls back.
 */
@Component
public class RefundProcessingRequestedEventListener {

    private static final Logger log =
            LoggerFactory.getLogger(
                    RefundProcessingRequestedEventListener.class
            );

    private final RefundProcessingService refundProcessingService;
    private final RefundAllocationRepository refundAllocationRepository;
    private final RefundAllocationProcessingService refundAllocationProcessingService;

    public RefundProcessingRequestedEventListener(
            RefundProcessingService refundProcessingService,
            RefundAllocationRepository refundAllocationRepository,
            RefundAllocationProcessingService refundAllocationProcessingService
    ) {
        this.refundProcessingService = refundProcessingService;
        this.refundAllocationRepository = refundAllocationRepository;
        this.refundAllocationProcessingService = refundAllocationProcessingService;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(
            RefundProcessingRequestedEvent event
    ) {

        if (event == null
                || event.refundPublicId() == null) {

            log.error(
                    "Received invalid refund processing event."
            );

            return;
        }

        try {

            if (!refundAllocationRepository.findAllByRefundPublicId(event.refundPublicId()).isEmpty()) {
                refundAllocationProcessingService.submit(event.refundPublicId());
            } else {
                refundProcessingService.submitPendingRefund(event.refundPublicId());
            }

            log.info(
                    "Refund submission completed for refund {}.",
                    event.refundPublicId()
            );

        } catch (Exception exception) {

            /*
             * Do NOT re-submit here.
             *
             * RefundProcessingService already moves an uncertain provider
             * outcome to REQUIRES_RECONCILIATION.
             *
             * Automatically retrying here could create a duplicate refund
             * if Razorpay accepted the request but our connection failed
             * before receiving the response.
             *
             * The cancellation itself has already committed and therefore
             * must remain CANCELLED.
             */
            log.error(
                    "Refund submission could not be safely completed for refund {}. "
                            + "The durable refund record has been preserved for "
                            + "verification/reconciliation.",
                    event.refundPublicId(),
                    exception
            );
        }
    }
}