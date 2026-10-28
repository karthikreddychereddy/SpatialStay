package com.spatialstay.refund;

import com.spatialstay.payment.PaymentProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class RefundReconciliationService {

    private static final Logger log =
            LoggerFactory.getLogger(
                    RefundReconciliationService.class
            );

    private static final int BATCH_SIZE = 20;

    private final RefundRepository refundRepository;
    private final RazorpayRefundGateway refundGateway;
    private final RefundVerificationTransactionService
            verificationTransactionService;

    public RefundReconciliationService(
            RefundRepository refundRepository,
            RazorpayRefundGateway refundGateway,
            RefundVerificationTransactionService
                    verificationTransactionService
    ) {
        this.refundRepository =
                refundRepository;

        this.refundGateway =
                refundGateway;

        this.verificationTransactionService =
                verificationTransactionService;
    }

    /*
     * Verification only.
     *
     * This service NEVER creates or resubmits a refund.
     *
     * It only examines PROCESSING refunds that already have a provider
     * refund ID and asks Razorpay for their authoritative state.
     */
    @Scheduled(
            initialDelay = 20_000,
            fixedDelay = 60_000
    )
    public void reconcileProcessingRefunds() {

        List<RefundReconciliationCandidate> candidates =
                refundRepository
                        .findReconciliationCandidates(
                                RefundStatus.PROCESSING,
                                PaymentProvider.RAZORPAY,
                                PageRequest.of(
                                        0,
                                        BATCH_SIZE
                                )
                        );

        for (RefundReconciliationCandidate candidate
                : candidates) {

            try {
                RazorpayRefundResult result =
                        refundGateway.fetchRefund(
                                candidate
                                        .getProviderRefundId()
                        );

                RefundVerificationTransactionService
                        .ProcessingResult processingResult =
                        verificationTransactionService
                                .applyAuthoritativeResult(
                                        candidate.getPublicId(),
                                        result
                                );

                log.info(
                        "Reconciled Razorpay refund {} for SpatialStay refund {} with result {}.",
                        candidate.getProviderRefundId(),
                        candidate.getPublicId(),
                        processingResult
                );

            } catch (Exception exception) {
                /*
                 * A failed provider read does NOT mean the refund failed.
                 * Leave it PROCESSING so a later verification pass can
                 * safely retry the read.
                 */
                log.error(
                        "Unable to verify Razorpay refund {} for SpatialStay refund {}.",
                        candidate.getProviderRefundId(),
                        candidate.getPublicId(),
                        exception
                );
            }
        }
    }
}