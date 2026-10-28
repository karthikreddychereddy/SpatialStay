package com.spatialstay.modificationrefund;

import com.spatialstay.payment.PaymentGatewayException;
import com.spatialstay.refund.RazorpayRefundGateway;
import com.spatialstay.refund.RazorpayRefundResult;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ModificationRefundProcessingService {

    private final ModificationRefundAllocationRepository
            allocationRepository;

    private final ModificationRefundProcessingTransactionService
            transactionService;

    private final RazorpayRefundGateway
            razorpayRefundGateway;

    public ModificationRefundProcessingService(
            ModificationRefundAllocationRepository allocationRepository,
            ModificationRefundProcessingTransactionService transactionService,
            RazorpayRefundGateway razorpayRefundGateway
    ) {
        this.allocationRepository =
                allocationRepository;

        this.transactionService =
                transactionService;

        this.razorpayRefundGateway =
                razorpayRefundGateway;
    }

    /*
     * Deliberately prevents one transaction from spanning provider
     * network IO.
     */
    @Transactional(
            propagation = Propagation.NOT_SUPPORTED
    )
    public void processBatch(
            UUID batchPublicId
    ) {
        if (batchPublicId == null) {
            throw new IllegalArgumentException(
                    "Modification refund batch ID is required."
            );
        }

        List<ModificationRefundAllocation> pending =
                allocationRepository
                        .findPendingByBatchPublicId(
                                batchPublicId
                        );

        for (ModificationRefundAllocation candidate
                : pending) {

            processAllocation(
                    candidate.getPublicId()
            );
        }
    }

    private void processAllocation(
            UUID allocationPublicId
    ) {
        ModificationRefundProcessingTransactionService
                .ClaimedAllocation claim =
                transactionService.claim(
                        allocationPublicId
                );

        /*
         * Another worker/webhook/retry already moved it.
         */
        if (claim == null) {
            return;
        }

        try {
            RazorpayRefundResult result =
                    razorpayRefundGateway
                            .createModificationRefund(
                                    claim.allocationPublicId(),
                                    claim.providerPaymentId(),
                                    claim.amount(),
                                    claim.currency()
                            );

            transactionService.recordProviderResult(
                    claim.allocationPublicId(),
                    result
            );

        } catch (PaymentGatewayException exception) {

            /*
             * Provider/network exception is treated as uncertain.
             *
             * NEVER automatically create a second refund here.
             */
            transactionService.markUncertain(
                    claim.allocationPublicId(),
                    safeReason(exception)
            );

        } catch (RuntimeException exception) {

            /*
             * Validation/serialization/unexpected failures after the
             * provider call may also leave provider state uncertain.
             */
            transactionService.markUncertain(
                    claim.allocationPublicId(),
                    safeReason(exception)
            );
        }
    }

    private String safeReason(
            Exception exception
    ) {
        String message =
                exception.getMessage();

        if (message == null
                || message.isBlank()) {

            return "Modification refund processing outcome is uncertain.";
        }

        String normalized =
                message.trim();

        return normalized.length() <= 900
                ? normalized
                : normalized.substring(
                        0,
                        900
                );
    }
}