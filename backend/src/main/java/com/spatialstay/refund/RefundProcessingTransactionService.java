package com.spatialstay.refund;

import com.spatialstay.payment.PaymentProvider;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class RefundProcessingTransactionService {

    private final RefundRepository refundRepository;

    public RefundProcessingTransactionService(
            RefundRepository refundRepository
    ) {
        this.refundRepository = refundRepository;
    }

    /*
     * Claims a PENDING refund before any external provider request.
     *
     * REQUIRES_NEW is intentional.
     *
     * Refund processing may be triggered from an AFTER_COMMIT listener.
     * A fresh transaction is therefore required so that the pessimistic
     * database lock always executes inside a real active transaction.
     *
     * The transaction commits PROCESSING before the Razorpay network
     * request starts. That prevents another worker from independently
     * claiming the same PENDING refund.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public RefundClaim claim(
            UUID refundPublicId
    ) {
        if (refundPublicId == null) {
            throw new IllegalArgumentException(
                    "Refund public ID is required."
            );
        }

        Refund refund =
                refundRepository
                        .findByPublicIdForUpdate(
                                refundPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Refund does not exist."
                                )
                        );

        if (refund.getStatus()
                != RefundStatus.PENDING) {

            throw new IllegalStateException(
                    "Only PENDING refunds may be submitted."
            );
        }

        if (refund.getProvider()
                != PaymentProvider.RAZORPAY) {

            throw new IllegalStateException(
                    "Unsupported refund provider."
            );
        }

        if (refund.getProviderRefundId()
                != null) {

            throw new IllegalStateException(
                    "Refund already has a provider refund identifier."
            );
        }

        if (refund.getPaymentAttempt()
                == null
                || refund.getPaymentAttempt()
                        .getProviderPaymentId()
                        == null
                || refund.getProviderPaymentId()
                        == null
                || !refund.getProviderPaymentId()
                        .equals(
                                refund.getPaymentAttempt()
                                        .getProviderPaymentId()
                        )) {

            throw new IllegalStateException(
                    "Refund payment relationship is inconsistent."
            );
        }

        refund.markProcessing(
                null,
                Instant.now()
        );

        return new RefundClaim(
                refund.getPublicId(),
                refund.getProviderPaymentId(),
                refund.getRefundAmount(),
                refund.getCurrency()
        );
    }

    /*
     * Records Razorpay's acceptance in another short independent
     * transaction.
     *
     * The provider call itself happens outside all database
     * transactions in RefundProcessingService.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void recordProviderAcceptance(
            UUID refundPublicId,
            RazorpayRefundResult result
    ) {
        if (refundPublicId == null) {
            throw new IllegalArgumentException(
                    "Refund public ID is required."
            );
        }

        if (result == null) {
            throw new IllegalArgumentException(
                    "Provider refund result is required."
            );
        }

        Refund refund =
                refundRepository
                        .findByPublicIdForUpdate(
                                refundPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Refund does not exist."
                                )
                        );

        if (refund.getStatus()
                != RefundStatus.PROCESSING
                || refund.getProviderRefundId()
                        != null) {

            throw new IllegalStateException(
                    "Refund is not awaiting its first provider response."
            );
        }

        if (refund.getProviderPaymentId()
                == null
                || !refund.getProviderPaymentId()
                        .equals(
                                result.providerPaymentId()
                        )) {

            throw new IllegalStateException(
                    "Provider payment mismatch."
            );
        }

        long expectedPaise =
                refund.getRefundAmount()
                        .movePointRight(2)
                        .longValueExact();

        if (result.amountInPaise()
                != expectedPaise
                || result.currency()
                        == null
                || refund.getCurrency()
                        == null
                || !refund.getCurrency()
                        .equalsIgnoreCase(
                                result.currency()
                        )) {

            throw new IllegalStateException(
                    "Provider refund financial snapshot mismatch."
            );
        }

        /*
         * Provider acceptance does not by itself mean that the
         * refund has reached final settlement.
         *
         * The provider refund identifier is persisted here so that
         * webhook/reconciliation processing can authoritatively
         * verify the final provider state.
         */
        refund.markProcessing(
                result.providerRefundId(),
                Instant.now()
        );
    }

    /*
     * Marks an ambiguous provider submission for reconciliation.
     *
     * This also needs its own transaction because it may run after
     * an exception from the external provider request.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void markUncertain(
            UUID refundPublicId
    ) {
        if (refundPublicId == null) {
            throw new IllegalArgumentException(
                    "Refund public ID is required."
            );
        }

        Refund refund =
                refundRepository
                        .findByPublicIdForUpdate(
                                refundPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Refund does not exist."
                                )
                        );

        if (refund.getStatus()
                == RefundStatus.PROCESSING
                && refund.getProviderRefundId()
                        == null) {

            refund.markRequiresReconciliation(
                    "Refund request outcome is uncertain. Verify with Razorpay before retrying.",
                    Instant.now()
            );
        }
    }

    public record RefundClaim(
            UUID refundId,
            String providerPaymentId,
            BigDecimal refundAmount,
            String currency
    ) {
    }
}