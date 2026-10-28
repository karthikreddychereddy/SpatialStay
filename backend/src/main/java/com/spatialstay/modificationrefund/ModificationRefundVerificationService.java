package com.spatialstay.modificationrefund;

import com.spatialstay.refund.RazorpayRefundResult;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class ModificationRefundVerificationService {

    private static final BigDecimal PAISE =
            new BigDecimal("100");

    private final ModificationRefundAllocationRepository
            allocationRepository;

    private final ModificationRefundBatchRepository
            batchRepository;

    public ModificationRefundVerificationService(
            ModificationRefundAllocationRepository allocationRepository,
            ModificationRefundBatchRepository batchRepository
    ) {
        this.allocationRepository = allocationRepository;
        this.batchRepository = batchRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessingResult applyAuthoritativeResult(
            UUID allocationPublicId,
            RazorpayRefundResult result
    ) {
        ModificationRefundAllocation allocation =
                allocationRepository
                        .findByPublicIdForUpdate(allocationPublicId)
                        .orElse(null);

        if (allocation == null) {
            return ProcessingResult.REQUIRES_RECONCILIATION;
        }

        if (!matches(allocation, result)) {
            allocation.markRequiresReconciliation(
                    "Authoritative Razorpay refund does not match allocation.",
                    Instant.now()
            );

            allocationRepository.save(allocation);

            updateBatch(
                    allocation.getBatch().getPublicId()
            );

            return ProcessingResult.REQUIRES_RECONCILIATION;
        }

        Instant now = Instant.now();

        String status =
                result.providerStatus() == null
                        ? ""
                        : result.providerStatus()
                                .trim()
                                .toLowerCase(Locale.ROOT);

        if ("processed".equals(status)) {

            allocation.markSucceeded(
                    result.providerRefundId(),
                    now
            );

        } else if ("pending".equals(status)
                || "created".equals(status)) {

            allocation.markProcessing(
                    result.providerRefundId(),
                    now
            );

        } else if ("failed".equals(status)) {

            allocation.markFailed(
                    "Razorpay authoritatively reported the refund as failed.",
                    now
            );

        } else {

            allocation.markRequiresReconciliation(
                    "Unexpected authoritative Razorpay refund status: "
                            + status,
                    now
            );
        }

        allocationRepository.save(allocation);

        updateBatch(
                allocation.getBatch().getPublicId()
        );

        return allocation.getStatus()
                == ModificationRefundAllocationStatus
                        .REQUIRES_RECONCILIATION
                ? ProcessingResult.REQUIRES_RECONCILIATION
                : ProcessingResult.PROCESSED;
    }

    private boolean matches(
            ModificationRefundAllocation allocation,
            RazorpayRefundResult result
    ) {
        if (result == null
                || result.providerRefundId() == null
                || result.providerPaymentId() == null
                || result.currency() == null) {

            return false;
        }

        if (!result.providerPaymentId()
                .equals(allocation.getProviderPaymentId())) {

            return false;
        }

        if (!result.currency()
                .equalsIgnoreCase(allocation.getCurrency())) {

            return false;
        }

        return result.amountInPaise()
                == toPaise(allocation.getAmount());
    }

    private void updateBatch(
            UUID batchPublicId
    ) {
        ModificationRefundBatch batch =
                batchRepository
                        .findByPublicIdForUpdate(batchPublicId)
                        .orElseThrow();

        var allocations =
                allocationRepository
                        .findAllByBatchPublicId(batchPublicId);

        if (allocations.stream().allMatch(
                a -> a.getStatus()
                        == ModificationRefundAllocationStatus.SUCCEEDED)) {

            batch.markSucceeded(Instant.now());

        } else if (allocations.stream().anyMatch(
                a -> a.getStatus()
                        == ModificationRefundAllocationStatus
                        .REQUIRES_RECONCILIATION)) {

            batch.markRequiresReconciliation(
                    "One or more refund allocations require reconciliation.",
                    Instant.now()
            );

        } else if (allocations.stream().anyMatch(
                a -> a.getStatus()
                        == ModificationRefundAllocationStatus.FAILED)) {

            batch.markFailed(
                    "One or more refund allocations failed.",
                    Instant.now()
            );

        } else {
            batch.markProcessing(Instant.now());
        }

        batchRepository.save(batch);
    }

    private long toPaise(BigDecimal amount) {
        return amount
                .multiply(PAISE)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();
    }

    public enum ProcessingResult {
        PROCESSED,
        REQUIRES_RECONCILIATION
    }
}