package com.spatialstay.refund;

import com.spatialstay.modificationrefund.ModificationRefundAllocation;
import com.spatialstay.modificationrefund.ModificationRefundAllocationRepository;
import com.spatialstay.modificationrefund.ModificationRefundVerificationService;
import com.spatialstay.payment.PaymentProvider;

import org.json.JSONObject;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RazorpayRefundWebhookProcessor {

    private final RefundRepository refundRepository;
    private final RazorpayRefundGateway refundGateway;

    private final RefundVerificationTransactionService
            verificationTransactionService;
    private final RefundAllocationRepository refundAllocationRepository;
    private final RefundAllocationVerificationService refundAllocationVerificationService;

    private final ModificationRefundAllocationRepository
            modificationRefundAllocationRepository;

    private final ModificationRefundVerificationService
            modificationRefundVerificationService;

    public RazorpayRefundWebhookProcessor(
            RefundRepository refundRepository,
            RazorpayRefundGateway refundGateway,
            RefundVerificationTransactionService verificationTransactionService,
            RefundAllocationRepository refundAllocationRepository,
            RefundAllocationVerificationService refundAllocationVerificationService,
            ModificationRefundAllocationRepository modificationRefundAllocationRepository,
            ModificationRefundVerificationService modificationRefundVerificationService
    ) {
        this.refundRepository = refundRepository;
        this.refundGateway = refundGateway;
        this.verificationTransactionService = verificationTransactionService;
        this.refundAllocationRepository = refundAllocationRepository;
        this.refundAllocationVerificationService = refundAllocationVerificationService;

        this.modificationRefundAllocationRepository =
                modificationRefundAllocationRepository;

        this.modificationRefundVerificationService =
                modificationRefundVerificationService;
    }

    /*
     * Network IO must not run inside the durable webhook transaction.
     *
     * The signed webhook is only a correlation hint. Razorpay's
     * authoritative refund record is fetched before local settlement
     * state is changed.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ProcessingResult process(
            String rawPayload
    ) {
        RefundWebhookHint hint =
                parseHint(rawPayload);

        if (!isSupportedRefundEvent(
                hint.eventType()
        )) {
            return ProcessingResult.IGNORED;
        }

        if (hint.cancellationRefundAllocationId() != null) {
            RefundAllocation allocation = refundAllocationRepository.findByPublicId(
                    hint.cancellationRefundAllocationId()).orElse(null);
            if (allocation == null) return ProcessingResult.REQUIRES_RECONCILIATION;
            RazorpayRefundResult providerResult = refundGateway.fetchRefund(hint.providerRefundId());
            return refundAllocationVerificationService.applyAuthoritativeResult(
                    allocation.getPublicId(), providerResult)
                    == RefundAllocationVerificationService.ProcessingResult.PROCESSED
                    ? ProcessingResult.PROCESSED
                    : ProcessingResult.REQUIRES_RECONCILIATION;
        }

        /*
         * ------------------------------------------------------------
         * STEP 11.11: MODIFICATION REFUND
         * ------------------------------------------------------------
         *
         * Modification refunds use their own Razorpay note:
         *
         * spatialstay_modification_refund_allocation_id
         *
         * This route must run BEFORE the legacy cancellation fallback.
         * Otherwise a provider payment shared by multiple refund
         * allocations could be correlated to the wrong refund.
         */
        if (hint.modificationRefundAllocationId() != null) {

            ModificationRefundAllocation allocation =
                    modificationRefundAllocationRepository
                            .findByPublicId(
                                    hint.modificationRefundAllocationId()
                            )
                            .orElse(null);

            if (allocation == null
                    || allocation.getPublicId() == null) {

                return ProcessingResult
                        .REQUIRES_RECONCILIATION;
            }

            RazorpayRefundResult providerResult =
                    refundGateway.fetchRefund(
                            hint.providerRefundId()
                    );

            ModificationRefundVerificationService
                    .ProcessingResult result =
                    modificationRefundVerificationService
                            .applyAuthoritativeResult(
                                    allocation.getPublicId(),
                                    providerResult
                            );

            if (result
                    == ModificationRefundVerificationService
                    .ProcessingResult
                    .REQUIRES_RECONCILIATION) {

                return ProcessingResult
                        .REQUIRES_RECONCILIATION;
            }

            return ProcessingResult.PROCESSED;
        }

        /*
         * ------------------------------------------------------------
         * EXISTING CANCELLATION REFUND
         * ------------------------------------------------------------
         */

        /*
         * First preference:
         * provider refund ID already persisted by synchronous refund
         * processing.
         */
        Refund refund =
                refundRepository
                        .findByProviderAndProviderRefundId(
                                PaymentProvider.RAZORPAY,
                                hint.providerRefundId()
                        )
                        .orElse(null);

        /*
         * Second preference:
         * stable cancellation refund UUID stored in Razorpay notes.
         */
        if (refund == null
                && hint.spatialStayRefundId() != null) {

            refund =
                    refundRepository
                            .findByPublicId(
                                    hint.spatialStayRefundId()
                            )
                            .orElse(null);
        }

        /*
         * Compatibility fallback for historical cancellation refunds
         * without SpatialStay notes.
         *
         * This remains last because multiple future refund operations
         * may legitimately reference the same payment.
         */
        if (refund == null) {

            refund =
                    refundRepository
                            .findByProviderAndProviderPaymentId(
                                    PaymentProvider.RAZORPAY,
                                    hint.providerPaymentId()
                            )
                            .orElse(null);
        }

        if (refund == null
                || refund.getPublicId() == null) {

            return ProcessingResult
                    .REQUIRES_RECONCILIATION;
        }

        UUID refundPublicId =
                refund.getPublicId();

        /*
         * Authoritative provider read.
         */
        RazorpayRefundResult providerResult =
                refundGateway.fetchRefund(
                        hint.providerRefundId()
                );

        RefundVerificationTransactionService
                .ProcessingResult result =
                verificationTransactionService
                        .applyAuthoritativeResult(
                                refundPublicId,
                                providerResult
                        );

        if (result
                == RefundVerificationTransactionService
                .ProcessingResult
                .REQUIRES_RECONCILIATION) {

            return ProcessingResult
                    .REQUIRES_RECONCILIATION;
        }

        return ProcessingResult.PROCESSED;
    }

    private RefundWebhookHint parseHint(
            String rawPayload
    ) {
        if (rawPayload == null
                || rawPayload.isBlank()) {

            throw new IllegalArgumentException(
                    "Razorpay webhook payload is required."
            );
        }

        try {
            JSONObject root =
                    new JSONObject(rawPayload);

            String eventType =
                    requireText(
                            root.optString(
                                    "event",
                                    null
                            ),
                            "Razorpay event type"
                    );

            if (!isSupportedRefundEvent(
                    eventType
            )) {
                return new RefundWebhookHint(
                        eventType,
                        null,
                        null,
                        null,
                        null,
                        null
                );
            }

            JSONObject payload =
                    requireObject(
                            root,
                            "payload"
                    );

            JSONObject refundContainer =
                    requireObject(
                            payload,
                            "refund"
                    );

            JSONObject refund =
                    requireObject(
                            refundContainer,
                            "entity"
                    );

            String providerRefundId =
                    requireText(
                            refund.optString(
                                    "id",
                                    null
                            ),
                            "Razorpay refund ID"
                    );

            String providerPaymentId =
                    requireText(
                            refund.optString(
                                    "payment_id",
                                    null
                            ),
                            "Razorpay payment ID"
                    );

            JSONObject notes =
                    refund.optJSONObject(
                            "notes"
                    );

            UUID spatialStayRefundId =
                    parseSpatialStayRefundId(
                            notes
                    );

            UUID modificationRefundAllocationId =
                    parseModificationRefundAllocationId(
                            notes
                    );

            UUID cancellationRefundAllocationId =
                    parseCancellationRefundAllocationId(notes);

            /*
             * A refund must never claim to be both a cancellation
             * refund and a modification allocation.
             */
            int correlationCount = (spatialStayRefundId != null ? 1 : 0)
                    + (modificationRefundAllocationId != null ? 1 : 0)
                    + (cancellationRefundAllocationId != null ? 1 : 0);
            if (correlationCount > 1) {

                throw new IllegalArgumentException(
                        "Razorpay refund contains conflicting SpatialStay correlation IDs."
                );
            }

            return new RefundWebhookHint(
                    eventType,
                    providerRefundId,
                    providerPaymentId,
                    spatialStayRefundId,
                    modificationRefundAllocationId,
                    cancellationRefundAllocationId
            );

        } catch (IllegalArgumentException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Invalid Razorpay refund webhook payload.",
                    exception
            );
        }
    }

    private UUID parseSpatialStayRefundId(
            JSONObject notes
    ) {
        return parseOptionalUuid(
                notes,
                "spatialstay_refund_id",
                "Razorpay refund contains an invalid SpatialStay refund ID."
        );
    }

    private UUID parseModificationRefundAllocationId(
            JSONObject notes
    ) {
        return parseOptionalUuid(
                notes,
                "spatialstay_modification_refund_allocation_id",
                "Razorpay refund contains an invalid SpatialStay modification refund allocation ID."
        );
    }

    private UUID parseCancellationRefundAllocationId(JSONObject notes) {
        return parseOptionalUuid(notes, "spatialstay_refund_allocation_id",
                "Razorpay refund contains an invalid SpatialStay cancellation refund allocation ID.");
    }

    private UUID parseOptionalUuid(
            JSONObject notes,
            String key,
            String errorMessage
    ) {
        if (notes == null) {
            return null;
        }

        String value =
                notes.optString(
                        key,
                        null
                );

        if (value == null
                || value.isBlank()) {

            return null;
        }

        try {
            return UUID.fromString(
                    value.trim()
            );

        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    errorMessage,
                    exception
            );
        }
    }

    private boolean isSupportedRefundEvent(
            String eventType
    ) {
        return "refund.created".equals(eventType)
                || "refund.processed".equals(eventType)
                || "refund.failed".equals(eventType);
    }

    private JSONObject requireObject(
            JSONObject parent,
            String name
    ) {
        JSONObject value =
                parent.optJSONObject(
                        name
                );

        if (value == null) {
            throw new IllegalArgumentException(
                    "Razorpay webhook "
                            + name
                            + " object is missing."
            );
        }

        return value;
    }

    private String requireText(
            String value,
            String field
    ) {
        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    field + " is required."
            );
        }

        return value.trim();
    }

    private record RefundWebhookHint(
            String eventType,
            String providerRefundId,
            String providerPaymentId,
            UUID spatialStayRefundId,
            UUID modificationRefundAllocationId,
            UUID cancellationRefundAllocationId
    ) {
    }

    public enum ProcessingResult {
        PROCESSED,
        IGNORED,
        REQUIRES_RECONCILIATION
    }
}