package com.spatialstay.refund;

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RefundProcessingService {

    private final RefundProcessingTransactionService transactionService;
    private final RazorpayRefundGateway razorpayRefundGateway;

    public RefundProcessingService(
            RefundProcessingTransactionService transactionService,
            RazorpayRefundGateway razorpayRefundGateway
    ) {
        this.transactionService = transactionService;
        this.razorpayRefundGateway = razorpayRefundGateway;
    }

    /*
     * Not transactional.
     *
     * Each transactionService call opens and commits its own transaction.
     * The network request runs between those transactions.
     *
     * Do not expose this directly as a public customer endpoint.
     */
    public void submitPendingRefund(
            UUID refundPublicId
    ) {
        RefundProcessingTransactionService.RefundClaim claim =
                transactionService.claim(refundPublicId);

        try {
            RazorpayRefundResult result =
                    razorpayRefundGateway.createRefund(
                            claim.refundId(),
                            claim.providerPaymentId(),
                            claim.refundAmount(),
                            claim.currency()
                    );

            transactionService.recordProviderAcceptance(
                    claim.refundId(),
                    result
            );

        } catch (Exception exception) {

            /*
             * We cannot assume an exception means Razorpay rejected
             * the refund.
             *
             * The provider might have accepted the request before
             * the connection failed.
             *
             * Therefore:
             * - do not submit again automatically,
             * - preserve the durable obligation,
             * - require reconciliation.
             */
            try {
                transactionService.markUncertain(
                        claim.refundId()
                );
            } catch (Exception reconciliationException) {
                exception.addSuppressed(
                        reconciliationException
                );
            }

            throw new IllegalStateException(
                    "Refund submission requires verification or reconciliation.",
                    exception
            );
        }
    }
}