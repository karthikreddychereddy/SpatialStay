package com.spatialstay.modificationrefund;

import com.spatialstay.refund.RazorpayRefundResult;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ModificationRefundProcessingTransactionService {

    private static final BigDecimal PAISE =
            new BigDecimal("100");

    private final ModificationRefundAllocationRepository
            allocationRepository;

    private final ModificationRefundBatchRepository
            batchRepository;

    public ModificationRefundProcessingTransactionService(
            ModificationRefundAllocationRepository allocationRepository,
            ModificationRefundBatchRepository batchRepository
    ) {
        this.allocationRepository =
                allocationRepository;

        this.batchRepository =
                batchRepository;
    }

    /*
     * Claim one allocation in an independent transaction.
     *
     * Provider IO happens only after this transaction returns.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public ClaimedAllocation claim(
            UUID allocationPublicId
    ) {
        ModificationRefundAllocation allocation =
                allocationRepository
                        .findByPublicIdForUpdate(
                                allocationPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Modification refund allocation was not found."
                                )
                        );

        if (allocation.getStatus()
                != ModificationRefundAllocationStatus.PENDING) {

            return null;
        }

        if (allocation.getProviderRefundId() != null) {
            throw new IllegalStateException(
                    "Pending allocation unexpectedly already has a provider refund ID."
            );
        }

        allocation.markProcessing(
                null,
                Instant.now()
        );

        allocationRepository.save(
                allocation
        );

        refreshBatch(
                allocation.getBatch().getPublicId()
        );

        return new ClaimedAllocation(
                allocation.getPublicId(),
                allocation.getProviderPaymentId(),
                allocation.getAmount(),
                allocation.getCurrency()
        );
    }

    /*
     * Records the provider response after Razorpay accepted the
     * request.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void recordProviderResult(
            UUID allocationPublicId,
            RazorpayRefundResult result
    ) {
        if (result == null) {
            throw new IllegalArgumentException(
                    "Provider refund result is required."
            );
        }

        ModificationRefundAllocation allocation =
                allocationRepository
                        .findByPublicIdForUpdate(
                                allocationPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Modification refund allocation was not found."
                                )
                        );

        if (allocation.getStatus()
                == ModificationRefundAllocationStatus.SUCCEEDED) {

            validateProviderResult(
                    allocation,
                    result
            );

            return;
        }

        validateProviderResult(
                allocation,
                result
        );

        Instant now =
                Instant.now();

        String providerStatus =
                result.providerStatus() == null
                        ? ""
                        : result.providerStatus()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        /*
         * Razorpay may return a refund that is already processed or
         * one that is still pending.
         */
        if ("processed".equals(providerStatus)) {

            allocation.markSucceeded(
                    result.providerRefundId(),
                    now
            );

        } else if ("pending".equals(providerStatus)) {

            allocation.markProcessing(
                    result.providerRefundId(),
                    now
            );

        } else if ("failed".equals(providerStatus)) {

            allocation.markFailed(
                    "Razorpay reported the refund as failed.",
                    now
            );

        } else {

            allocation.markRequiresReconciliation(
                    "Unexpected Razorpay refund status: "
                            + providerStatus,
                    now
            );
        }

        allocationRepository.save(
                allocation
        );

        refreshBatch(
                allocation.getBatch().getPublicId()
        );
    }

    /*
     * Used when the create-refund request has an uncertain outcome.
     *
     * We MUST NOT blindly retry because Razorpay may already have
     * created the refund.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void markUncertain(
            UUID allocationPublicId,
            String reason
    ) {
        ModificationRefundAllocation allocation =
                allocationRepository
                        .findByPublicIdForUpdate(
                                allocationPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Modification refund allocation was not found."
                                )
                        );

        if (allocation.getStatus()
                == ModificationRefundAllocationStatus.SUCCEEDED) {
            return;
        }

        allocation.markRequiresReconciliation(
                reason,
                Instant.now()
        );

        allocationRepository.save(
                allocation
        );

        refreshBatch(
                allocation.getBatch().getPublicId()
        );
    }

    private void validateProviderResult(
            ModificationRefundAllocation allocation,
            RazorpayRefundResult result
    ) {
        if (result.providerRefundId() == null
                || result.providerRefundId().isBlank()
                || !result.providerRefundId()
                .startsWith("rfnd_")) {

            throw new IllegalStateException(
                    "Provider returned an invalid refund ID."
            );
        }

        if (result.providerPaymentId() == null
                || !result.providerPaymentId()
                .equals(
                        allocation.getProviderPaymentId()
                )) {

            throw new IllegalStateException(
                    "Provider refund payment ID does not match allocation."
            );
        }

        long expectedPaise =
                toPaise(
                        allocation.getAmount(),
                        allocation.getCurrency()
                );

        if (result.amountInPaise()
                != expectedPaise) {

            throw new IllegalStateException(
                    "Provider refund amount does not match allocation."
            );
        }

        if (result.currency() == null
                || !result.currency()
                .equalsIgnoreCase(
                        allocation.getCurrency()
                )) {

            throw new IllegalStateException(
                    "Provider refund currency does not match allocation."
            );
        }
    }

    private void refreshBatch(
            UUID batchPublicId
    ) {
        ModificationRefundBatch batch =
                batchRepository
                        .findByPublicIdForUpdate(
                                batchPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Modification refund batch was not found."
                                )
                        );

        List<ModificationRefundAllocation> allocations =
                allocationRepository
                        .findAllByBatchPublicId(
                                batchPublicId
                        );

        if (allocations.isEmpty()) {
            batch.markRequiresReconciliation(
                    "Refund batch contains no allocations.",
                    Instant.now()
            );

            batchRepository.save(
                    batch
            );

            return;
        }

        boolean allSucceeded =
                allocations.stream()
                        .allMatch(
                                allocation ->
                                        allocation.getStatus()
                                                == ModificationRefundAllocationStatus.SUCCEEDED
                        );

        if (allSucceeded) {
            batch.markSucceeded(
                    Instant.now()
            );

            batchRepository.save(
                    batch
            );

            return;
        }

        boolean requiresReconciliation =
                allocations.stream()
                        .anyMatch(
                                allocation ->
                                        allocation.getStatus()
                                                == ModificationRefundAllocationStatus
                                                .REQUIRES_RECONCILIATION
                        );

        if (requiresReconciliation) {
            batch.markRequiresReconciliation(
                    "One or more refund allocations require reconciliation.",
                    Instant.now()
            );

            batchRepository.save(
                    batch
            );

            return;
        }

        boolean failed =
                allocations.stream()
                        .anyMatch(
                                allocation ->
                                        allocation.getStatus()
                                                == ModificationRefundAllocationStatus.FAILED
                        );

        if (failed) {
            batch.markFailed(
                    "One or more refund allocations failed.",
                    Instant.now()
            );

            batchRepository.save(
                    batch
            );

            return;
        }

        batch.markProcessing(
                Instant.now()
        );

        batchRepository.save(
                batch
        );
    }

    private long toPaise(
            BigDecimal amount,
            String currency
    ) {
        if (!"INR".equalsIgnoreCase(currency)) {
            throw new IllegalArgumentException(
                    "Razorpay refunds currently support INR only."
            );
        }

        if (amount == null
                || amount.signum() <= 0) {

            throw new IllegalArgumentException(
                    "Refund amount must be positive."
            );
        }

        try {
            return amount
                    .multiply(PAISE)
                    .setScale(
                            0,
                            RoundingMode.UNNECESSARY
                    )
                    .longValueExact();

        } catch (ArithmeticException exception) {

            throw new IllegalArgumentException(
                    "Refund amount cannot be represented safely in paise.",
                    exception
            );
        }
    }

    public record ClaimedAllocation(
            UUID allocationPublicId,
            String providerPaymentId,
            BigDecimal amount,
            String currency
    ) {
    }
}