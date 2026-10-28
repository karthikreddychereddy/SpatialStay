package com.spatialstay.refund;

import com.spatialstay.payment.PaymentProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PendingRefundRecoveryService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    PendingRefundRecoveryService.class
            );

    /*
     * Keep each recovery pass deliberately small.
     *
     * RefundProcessingTransactionService.claim() performs the real
     * concurrency control. This batch limit only prevents a recovery
     * pass from trying to process an unbounded number of refunds.
     */
    private static final int BATCH_SIZE = 20;

    private final RefundRepository refundRepository;
    private final RefundProcessingService refundProcessingService;
    private final RefundAllocationRepository refundAllocationRepository;
    private final RefundAllocationProcessingService refundAllocationProcessingService;

    public PendingRefundRecoveryService(
            RefundRepository refundRepository,
            RefundProcessingService refundProcessingService,
            RefundAllocationRepository refundAllocationRepository,
            RefundAllocationProcessingService refundAllocationProcessingService
    ) {
        this.refundRepository =
                refundRepository;

        this.refundProcessingService = refundProcessingService;
        this.refundAllocationRepository = refundAllocationRepository;
        this.refundAllocationProcessingService = refundAllocationProcessingService;
    }

    /*
     * Durable fallback for refunds that were committed as PENDING but
     * were not submitted by the normal AFTER_COMMIT path.
     *
     * Example:
     *
     * 1. cancellation transaction commits
     * 2. Refund(PENDING) is durable
     * 3. process crashes, listener fails, or submission never starts
     * 4. this worker later discovers the PENDING refund
     * 5. claim() atomically locks it and changes it to PROCESSING
     * 6. only then is the Razorpay refund request made
     *
     * IMPORTANT:
     *
     * This worker NEVER discovers PROCESSING, SUCCEEDED, FAILED or
     * REQUIRES_RECONCILIATION refunds.
     *
     * PROCESSING / REQUIRES_RECONCILIATION may already exist at the
     * provider and therefore require verification/reconciliation,
     * not blind resubmission.
     */
    @Scheduled(
            initialDelay = 15_000,
            fixedDelay = 60_000
    )
    public void recoverPendingRefunds() {

        final List<UUID> refundIds;

        try {
            refundIds =
                    refundRepository
                            .findPublicIdsForRecovery(
                                    RefundStatus.PENDING,
                                    PaymentProvider.RAZORPAY,
                                    PageRequest.of(
                                            0,
                                            BATCH_SIZE
                                    )
                            );

        } catch (DataAccessException exception) {
            log.error(
                    "Unable to discover PENDING refunds for recovery.",
                    exception
            );

            return;
        }

        if (refundIds.isEmpty()) {
            return;
        }

        log.info(
                "Found {} PENDING refund(s) eligible for recovery.",
                refundIds.size()
        );

        for (UUID refundPublicId : refundIds) {

            if (refundPublicId == null) {
                continue;
            }

            try {
                if (!refundAllocationRepository.findAllByRefundPublicId(refundPublicId).isEmpty()) {
                    refundAllocationProcessingService.submit(refundPublicId);
                } else {
                    refundProcessingService.submitPendingRefund(refundPublicId);
                }

                log.info(
                        "Recovered PENDING refund {}.",
                        refundPublicId
                );

            } catch (IllegalStateException exception) {

                /*
                 * Another execution path may have claimed the refund
                 * after this worker discovered it.
                 *
                 * claim() remains authoritative, so we do not retry
                 * inside this recovery pass.
                 */
                log.warn(
                        "PENDING refund {} was not recovered in this pass: {}",
                        refundPublicId,
                        exception.getMessage()
                );

            } catch (Exception exception) {

                /*
                 * Never continuously retry an ambiguous provider call
                 * in this loop.
                 *
                 * RefundProcessingService is responsible for moving an
                 * uncertain PROCESSING refund to
                 * REQUIRES_RECONCILIATION.
                 */
                log.error(
                        "Unexpected failure while recovering refund {}.",
                        refundPublicId,
                        exception
                );
            }
        }
    }
}