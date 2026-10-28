package com.spatialstay.refund;

import com.spatialstay.payment.PaymentProvider;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class RefundVerificationTransactionService {

    private final RefundRepository refundRepository;

    public RefundVerificationTransactionService(
            RefundRepository refundRepository
    ) {
        this.refundRepository = refundRepository;
    }

    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public ProcessingResult applyAuthoritativeResult(
            UUID refundPublicId,
            RazorpayRefundResult providerResult
    ) {
        if (refundPublicId == null) {
            throw new IllegalArgumentException(
                    "Refund public ID is required."
            );
        }

        if (providerResult == null) {
            throw new IllegalArgumentException(
                    "Authoritative provider refund is required."
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

        Instant now = Instant.now();

        if (refund.getProvider()
                != PaymentProvider.RAZORPAY) {

            refund.markRequiresReconciliation(
                    "Refund provider does not match Razorpay.",
                    now
            );

            return ProcessingResult
                    .REQUIRES_RECONCILIATION;
        }

        if (refund.getProviderPaymentId() == null
                || providerResult.providerPaymentId() == null
                || !refund.getProviderPaymentId()
                        .equals(
                                providerResult.providerPaymentId()
                        )) {

            refund.markRequiresReconciliation(
                    "Authoritative Razorpay payment ID does not match the refund.",
                    now
            );

            return ProcessingResult
                    .REQUIRES_RECONCILIATION;
        }

        if (providerResult.providerRefundId() == null
                || providerResult.providerRefundId().isBlank()
                || !providerResult.providerRefundId()
                        .startsWith("rfnd_")) {

            refund.markRequiresReconciliation(
                    "Razorpay returned an invalid refund identifier.",
                    now
            );

            return ProcessingResult
                    .REQUIRES_RECONCILIATION;
        }

        if (refund.getProviderRefundId() != null
                && !refund.getProviderRefundId().isBlank()
                && !refund.getProviderRefundId()
                        .equals(
                                providerResult.providerRefundId()
                        )) {

            refund.markRequiresReconciliation(
                    "Authoritative Razorpay refund ID does not match the stored refund ID.",
                    now
            );

            return ProcessingResult
                    .REQUIRES_RECONCILIATION;
        }

        long expectedPaise;

        try {
            expectedPaise =
                    refund.getRefundAmount()
                            .movePointRight(2)
                            .longValueExact();

        } catch (ArithmeticException exception) {
            refund.markRequiresReconciliation(
                    "Stored refund amount cannot be represented safely in paise.",
                    now
            );

            return ProcessingResult
                    .REQUIRES_RECONCILIATION;
        }

        if (providerResult.amountInPaise()
                != expectedPaise) {

            refund.markRequiresReconciliation(
                    "Authoritative Razorpay refund amount does not match SpatialStay.",
                    now
            );

            return ProcessingResult
                    .REQUIRES_RECONCILIATION;
        }

        if (refund.getCurrency() == null
                || providerResult.currency() == null
                || !refund.getCurrency()
                        .equalsIgnoreCase(
                                providerResult.currency()
                        )) {

            refund.markRequiresReconciliation(
                    "Authoritative Razorpay refund currency does not match SpatialStay.",
                    now
            );

            return ProcessingResult
                    .REQUIRES_RECONCILIATION;
        }

        String providerStatus =
                providerResult.providerStatus() == null
                        ? ""
                        : providerResult
                                .providerStatus()
                                .trim()
                                .toLowerCase(Locale.ROOT);

        return switch (providerStatus) {

            case "processed" -> {
                refund.markSucceeded(
                        providerResult.providerRefundId(),
                        now
                );

                yield ProcessingResult.PROCESSED;
            }

            case "pending", "created" -> {
                if (refund.getStatus()
                        != RefundStatus.SUCCEEDED) {

                    refund.markProcessing(
                            providerResult.providerRefundId(),
                            now
                    );
                }

                yield ProcessingResult.PROCESSED;
            }

            case "failed" -> {
                if (refund.getStatus()
                        == RefundStatus.SUCCEEDED) {

                    refund.markRequiresReconciliation(
                            "Razorpay reported FAILED for a refund already recorded as SUCCEEDED.",
                            now
                    );

                    yield ProcessingResult
                            .REQUIRES_RECONCILIATION;
                }

                if (refund.getStatus()
                        == RefundStatus.REQUIRES_RECONCILIATION) {

                    yield ProcessingResult
                            .REQUIRES_RECONCILIATION;
                }

                if (refund.getProviderRefundId() == null
                        || refund.getProviderRefundId()
                                .isBlank()) {

                    refund.markProcessing(
                            providerResult.providerRefundId(),
                            now
                    );
                }

                refund.markFailed(
                        "Razorpay authoritatively reported that the refund failed.",
                        now
                );

                yield ProcessingResult.PROCESSED;
            }

            default -> {
                refund.markRequiresReconciliation(
                        "Unsupported Razorpay refund status: "
                                + providerStatus,
                        now
                );

                yield ProcessingResult
                        .REQUIRES_RECONCILIATION;
            }
        };
    }

    public enum ProcessingResult {
        PROCESSED,
        REQUIRES_RECONCILIATION
    }
}