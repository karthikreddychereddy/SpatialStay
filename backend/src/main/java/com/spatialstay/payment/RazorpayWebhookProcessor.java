package com.spatialstay.payment;
import com.spatialstay.booking.Booking;
import com.spatialstay.booking.BookingModification;
import com.spatialstay.booking.BookingPaymentConfirmationService;
import com.spatialstay.booking.BookingStatus;
import com.spatialstay.booking.InventoryHold;
import com.spatialstay.booking.InventoryHoldStatus;
import com.spatialstay.refund.RefundRepository;
import com.spatialstay.booking.BookingModificationPaymentConfirmationService;
import com.spatialstay.refund.RazorpayRefundWebhookProcessor;
import com.spatialstay.modificationrefund.ModificationRefundAllocation;
import com.spatialstay.modificationrefund.ModificationRefundAllocationRepository;
import com.spatialstay.modificationrefund.ModificationRefundBatch;
import com.spatialstay.modificationrefund.ModificationRefundBatchRepository;
import com.spatialstay.modificationrefund.ModificationRefundProcessingRequestedEvent;
import com.spatialstay.modificationrefund.ModificationRefundReason;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
@Service
public class RazorpayWebhookProcessor {
    private static final BigDecimal SUBUNITS_PER_INR =
            new BigDecimal("100");
    private final PaymentWebhookEventRepository
            webhookEventRepository;
    private final PaymentAttemptRepository
            paymentAttemptRepository;
    private final RazorpayPaymentVerifier
            razorpayPaymentVerifier;
    private final BookingPaymentConfirmationService
            bookingPaymentConfirmationService;
    private final RazorpayRefundWebhookProcessor
            refundWebhookProcessor;
    private final BookingModificationPaymentConfirmationService
            bookingModificationPaymentConfirmationService;
    private final RefundRepository refundRepository;
    private final ModificationRefundBatchRepository modificationRefundBatchRepository;
    private final ModificationRefundAllocationRepository modificationRefundAllocationRepository;
    private final ApplicationEventPublisher eventPublisher;
    /*
     * Runtime Spring constructor.
     */
        @Autowired
        public RazorpayWebhookProcessor(
                PaymentWebhookEventRepository webhookEventRepository,
                PaymentAttemptRepository paymentAttemptRepository,
                RazorpayPaymentVerifier razorpayPaymentVerifier,
                BookingPaymentConfirmationService bookingPaymentConfirmationService,
                RazorpayRefundWebhookProcessor refundWebhookProcessor,
                RefundRepository refundRepository,
                BookingModificationPaymentConfirmationService
                        bookingModificationPaymentConfirmationService,
                ModificationRefundBatchRepository modificationRefundBatchRepository,
                ModificationRefundAllocationRepository modificationRefundAllocationRepository,
                ApplicationEventPublisher eventPublisher
        ) {
        this.webhookEventRepository =
                webhookEventRepository;
        this.paymentAttemptRepository =
                paymentAttemptRepository;
        this.razorpayPaymentVerifier =
                razorpayPaymentVerifier;
        this.bookingPaymentConfirmationService =
                bookingPaymentConfirmationService;
        this.refundWebhookProcessor =
                refundWebhookProcessor;
        this.refundRepository =
                refundRepository;
        this.bookingModificationPaymentConfirmationService =
                bookingModificationPaymentConfirmationService;
        this.modificationRefundBatchRepository = modificationRefundBatchRepository;
        this.modificationRefundAllocationRepository = modificationRefundAllocationRepository;
        this.eventPublisher = eventPublisher;
        }
        /*
        * Kept for existing Step 10 unit tests that directly construct the
        * processor with the original four dependencies.
        *
        * Spring runtime uses the @Autowired constructor above.
        */
        RazorpayWebhookProcessor(
                PaymentWebhookEventRepository webhookEventRepository,
                PaymentAttemptRepository paymentAttemptRepository,
                RazorpayPaymentVerifier razorpayPaymentVerifier,
                BookingPaymentConfirmationService bookingPaymentConfirmationService
        ) {
        this.webhookEventRepository =
                webhookEventRepository;
        this.paymentAttemptRepository =
                paymentAttemptRepository;
        this.razorpayPaymentVerifier =
                razorpayPaymentVerifier;
        this.bookingPaymentConfirmationService =
                bookingPaymentConfirmationService;
        this.refundWebhookProcessor =
                null;
        this.refundRepository =
                null;
        this.bookingModificationPaymentConfirmationService =
                null;
        this.modificationRefundBatchRepository = null;
        this.modificationRefundAllocationRepository = null;
        this.eventPublisher = null;
        }
    @Transactional
    public ProcessingResult process(
            String payloadFingerprint
    ) {
        String fingerprint =
                requireText(
                        payloadFingerprint,
                        "Webhook payload fingerprint"
                );
        PaymentWebhookEvent managedEvent =
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                fingerprint
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Recorded Razorpay webhook event was not found."
                                )
                        );
        /*
         * Byte-identical retry already completed.
         */
        if (managedEvent.getStatus()
                == PaymentWebhookEventStatus.PROCESSED) {
            return ProcessingResult.PROCESSED;
        }
        /*
         * Automatic processing must not silently override a webhook
         * event that has already been isolated for reconciliation.
         */
        if (managedEvent.getStatus()
                == PaymentWebhookEventStatus
                .REQUIRES_RECONCILIATION) {
            return ProcessingResult
                    .REQUIRES_RECONCILIATION;
        }
        RazorpayWebhookPayload payload =
                parsePayload(
                        managedEvent.getRawPayload()
                );
        /*
         * Step 11.8 refund events use the same:
         *
         * - endpoint
         * - exact raw body
         * - HMAC verification
         * - durable PaymentWebhookEvent
         *
         * as Step 10.
         */
        if (isRefundEvent(
                payload.eventType()
        )) {
            if (refundWebhookProcessor == null) {
                /*
                 * This can occur only in old direct-construction
                 * Step 10 tests, never in the Spring runtime.
                 */
                throw new IllegalStateException(
                        "Razorpay refund webhook processor is unavailable."
                );
            }
            RazorpayRefundWebhookProcessor
                    .ProcessingResult refundResult =
                    refundWebhookProcessor.process(
                            managedEvent.getRawPayload()
                    );
            Instant now =
                    Instant.now();
            if (refundResult
                    == RazorpayRefundWebhookProcessor
                    .ProcessingResult
                    .REQUIRES_RECONCILIATION) {
                managedEvent.markRequiresReconciliation(
                        now
                );
                return ProcessingResult
                        .REQUIRES_RECONCILIATION;
            }
            managedEvent.markProcessed(
                    now
            );
            if (refundResult
                    == RazorpayRefundWebhookProcessor
                    .ProcessingResult.PROCESSED) {
                return ProcessingResult.PROCESSED;
            }
            return ProcessingResult.IGNORED;
        }
        /*
         * Other correctly signed Razorpay events remain durably
         * recorded but cannot mutate payment/booking/refund state.
         */
        if (!"payment.captured".equals(
                payload.eventType()
        )) {
            managedEvent.markProcessed(
                    Instant.now()
            );
            return ProcessingResult.IGNORED;
        }
        /*
         * Initial non-locking lookup used only to discover the
         * authoritative booking/hold relationship.
         */
        PaymentAttempt discoveredAttempt =
                paymentAttemptRepository
                        .findByProviderAndProviderOrderId(
                                PaymentProvider.RAZORPAY,
                                payload.orderId()
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Razorpay order is not associated with a SpatialStay payment."
                                )
                        );
        /*
        * Step 11.11
        *
        * Modification additional payments must be routed away from the
        * original Step 10 confirmation lifecycle BEFORE we inspect the
        * original booking InventoryHold.
        */
        if (discoveredAttempt.getPurpose()
                == PaymentPurpose.MODIFICATION_ADDITIONAL) {
        return processModificationAdditionalPayment(
                discoveredAttempt,
                payload,
                managedEvent
        );
        }
        /*
        * Everything below this point is the frozen original-booking
        * payment path.
        */
        Booking discoveredBooking =
                discoveredAttempt.getBooking();
        if (discoveredBooking == null
                || discoveredBooking.getPublicId() == null
                || discoveredBooking.getInventoryHold() == null
                || discoveredBooking
                        .getInventoryHold()
                        .getPublicId() == null) {
            throw new IllegalStateException(
                    "Payment booking relationship is invalid."
            );
        }
        Instant now =
                Instant.now();
        /*
         * Semantic duplicate path.
         *
         * After Step 11 a successful historical payment can
         * legitimately belong to a later CANCELLED booking.
         */
        if (discoveredAttempt.getStatus()
                == PaymentStatus.SUCCEEDED) {
            validateAlreadyCompletedPayment(
                    discoveredAttempt,
                    discoveredBooking,
                    payload
            );
            managedEvent.markProcessed(
                    now
            );
            return ProcessingResult.PROCESSED;
        }
        BookingPaymentConfirmationService
                .LockedConfirmationContext confirmationContext;
        try {
            /*
             * LOCK ORDER #1:
             * InventoryHold remains the Step 9/10 serialization
             * boundary.
             */
            confirmationContext =
                    bookingPaymentConfirmationService
                            .lockForPaymentConfirmation(
                                    discoveredBooking
                                            .getInventoryHold()
                                            .getPublicId(),
                                    discoveredBooking
                                            .getPublicId(),
                                    now
                            );
        } catch (
                BookingPaymentConfirmationService
                        .HoldUnavailableForConfirmationException
                        exception
        ) {
            /*
             * Never resurrect expired inventory because money arrived
             * late.
             */
            managedEvent.markRequiresReconciliation(
                    now
            );
            return ProcessingResult
                    .REQUIRES_RECONCILIATION;
        }
        /*
         * LOCK ORDER #2:
         * PaymentAttempt only after InventoryHold.
         */
        PaymentAttempt paymentAttempt =
                paymentAttemptRepository
                        .findByProviderOrderForUpdate(
                                PaymentProvider.RAZORPAY,
                                payload.orderId()
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Locked payment attempt was not found."
                                )
                        );
        revalidateRelationships(
                paymentAttempt,
                confirmationContext
        );
        /*
         * Payment confirmation retains the Step 10 server-side
         * provider verification.
         */
        VerifiedRazorpayPayment verifiedPayment =
                razorpayPaymentVerifier
                        .fetchCapturedPayment(
                                payload.paymentId()
                        );
        validateProviderPayment(
                paymentAttempt,
                verifiedPayment,
                payload
        );
        validateAuthoritativeAmounts(
                paymentAttempt,
                confirmationContext.booking(),
                confirmationContext.hold()
        );
        /*
         * Concurrent duplicate protection.
         */
        if (paymentAttempt.getStatus()
                == PaymentStatus.SUCCEEDED) {
            validateAlreadyCompletedPayment(
                    paymentAttempt,
                    confirmationContext.booking(),
                    payload
            );
            managedEvent.markProcessed(
                    now
            );
            return ProcessingResult.PROCESSED;
        }
        paymentAttempt.markSucceeded(
                verifiedPayment.paymentId(),
                now
        );
        bookingPaymentConfirmationService
                .confirmLocked(
                        confirmationContext,
                        now
                );
        managedEvent.markProcessed(
                now
        );
        return ProcessingResult.PROCESSED;
    }
    private ProcessingResult processModificationAdditionalPayment(
            PaymentAttempt discoveredAttempt,
            RazorpayWebhookPayload payload,
            PaymentWebhookEvent managedEvent
    ) {
        if (bookingModificationPaymentConfirmationService == null) {
            throw new IllegalStateException("Booking modification payment confirmation service is unavailable.");
        }
        if (discoveredAttempt == null || discoveredAttempt.getPurpose() != PaymentPurpose.MODIFICATION_ADDITIONAL) {
            throw new IllegalStateException("Invalid modification additional payment.");
        }
        BookingModification modification = discoveredAttempt.getBookingModification();
        if (modification == null || modification.getPublicId() == null
                || discoveredAttempt.getBooking() == null
                || discoveredAttempt.getBooking().getPublicId() == null) {
            throw new IllegalStateException("Modification payment relationships are invalid.");
        }
        Instant now = Instant.now();
        if (discoveredAttempt.getStatus() == PaymentStatus.SUCCEEDED) {
            boolean compensationExists = modificationRefundBatchRepository != null
                    && modificationRefundBatchRepository
                    .findByBookingModificationPublicId(modification.getPublicId())
                    .filter(batch -> batch.getReason() == ModificationRefundReason.LATE_PAYMENT_COMPENSATION)
                    .isPresent();
            if (compensationExists) {
                validateSuccessfulModificationPaymentIdentity(discoveredAttempt, payload);
            } else {
                validateAlreadyCompletedModificationPayment(discoveredAttempt, payload);
            }
            managedEvent.markProcessed(now);
            return ProcessingResult.PROCESSED;
        }
        BookingModificationPaymentConfirmationService.LockedModificationContext context;
        try {
            context = bookingModificationPaymentConfirmationService
                    .lockForPaymentConfirmation(modification.getPublicId(), now);
        } catch (BookingModificationPaymentConfirmationService.ReplacementHoldUnavailableException exception) {
            return compensateLateModificationPayment(payload, managedEvent, now);
        }
        PaymentAttempt paymentAttempt = paymentAttemptRepository
                .findByProviderOrderForUpdate(PaymentProvider.RAZORPAY, payload.orderId())
                .orElseThrow(() -> new IllegalStateException("Locked modification payment attempt was not found."));
        validateModificationPaymentRelationships(paymentAttempt, context);
        VerifiedRazorpayPayment verifiedPayment = razorpayPaymentVerifier.fetchCapturedPayment(payload.paymentId());
        validateProviderPayment(paymentAttempt, verifiedPayment, payload);
        validateModificationPaymentAmount(paymentAttempt, context, verifiedPayment);
        if (paymentAttempt.getStatus() == PaymentStatus.SUCCEEDED) {
            validateAlreadyCompletedModificationPayment(paymentAttempt, payload);
            managedEvent.markProcessed(now);
            return ProcessingResult.PROCESSED;
        }
        paymentAttempt.markSucceeded(verifiedPayment.paymentId(), now);
        bookingModificationPaymentConfirmationService.confirmLocked(context, now);
        managedEvent.markProcessed(now);
        return ProcessingResult.PROCESSED;
    }

    private ProcessingResult compensateLateModificationPayment(
            RazorpayWebhookPayload payload,
            PaymentWebhookEvent managedEvent,
            Instant now
    ) {
        if (modificationRefundBatchRepository == null
                || modificationRefundAllocationRepository == null
                || eventPublisher == null) {
            throw new IllegalStateException("Modification refund infrastructure is unavailable.");
        }
        PaymentAttempt paymentAttempt = paymentAttemptRepository
                .findByProviderOrderForUpdate(PaymentProvider.RAZORPAY, payload.orderId())
                .orElseThrow(() -> new IllegalStateException("Locked late modification payment attempt was not found."));
        if (paymentAttempt.getPurpose() != PaymentPurpose.MODIFICATION_ADDITIONAL) {
            throw new IllegalStateException("Late payment is not a modification additional payment.");
        }
        BookingModification modification = paymentAttempt.getBookingModification();
        Booking booking = paymentAttempt.getBooking();
        if (modification == null || modification.getPublicId() == null
                || booking == null || booking.getPublicId() == null
                || paymentAttempt.getUser() == null) {
            throw new IllegalStateException("Late modification payment relationships are invalid.");
        }
        VerifiedRazorpayPayment verifiedPayment = razorpayPaymentVerifier.fetchCapturedPayment(payload.paymentId());
        validateProviderPayment(paymentAttempt, verifiedPayment, payload);
        validateLateModificationPaymentAmount(paymentAttempt, modification, verifiedPayment);
        if (paymentAttempt.getStatus() == PaymentStatus.SUCCEEDED) {
            validateSuccessfulModificationPaymentIdentity(paymentAttempt, payload);
            managedEvent.markProcessed(now);
            return ProcessingResult.PROCESSED;
        }
        paymentAttempt.markSucceeded(verifiedPayment.paymentId(), now);
        ModificationRefundBatch batch = modificationRefundBatchRepository
                .findByBookingModificationPublicId(modification.getPublicId()).orElse(null);
        if (batch != null) {
            if (batch.getReason() != ModificationRefundReason.LATE_PAYMENT_COMPENSATION
                    || batch.getTotalAmount() == null
                    || paymentAttempt.getExpectedAmount() == null
                    || batch.getTotalAmount().compareTo(paymentAttempt.getExpectedAmount()) != 0
                    || batch.getCurrency() == null
                    || paymentAttempt.getCurrency() == null
                    || !batch.getCurrency().equalsIgnoreCase(paymentAttempt.getCurrency())) {
                throw new IllegalStateException("Existing late-payment refund obligation is inconsistent.");
            }
        } else {
            batch = ModificationRefundBatch.create(
                    paymentAttempt.getUser(), booking, modification,
                    ModificationRefundReason.LATE_PAYMENT_COMPENSATION,
                    paymentAttempt.getExpectedAmount(), paymentAttempt.getCurrency(), now);
            batch = modificationRefundBatchRepository.save(batch);
        }
        if (batch.getPublicId() == null) {
            throw new IllegalStateException("Late-payment refund batch is missing its public ID.");
        }
        boolean allocationExists = modificationRefundAllocationRepository
                .findAllByBatchPublicId(batch.getPublicId()).stream()
                .anyMatch(a -> a.getPaymentAttempt() != null
                        && a.getPaymentAttempt().getId() != null
                        && paymentAttempt.getId() != null
                        && a.getPaymentAttempt().getId().equals(paymentAttempt.getId()));
        if (!allocationExists) {
            modificationRefundAllocationRepository.save(
                    ModificationRefundAllocation.create(
                            batch, paymentAttempt, paymentAttempt.getExpectedAmount(),
                            paymentAttempt.getCurrency(), now));
        }
        eventPublisher.publishEvent(new ModificationRefundProcessingRequestedEvent(batch.getPublicId()));
        managedEvent.markProcessed(now);
        return ProcessingResult.PROCESSED;
    }

    private void validateLateModificationPaymentAmount(
            PaymentAttempt paymentAttempt,
            BookingModification modification,
            VerifiedRazorpayPayment verifiedPayment
    ) {
        if (paymentAttempt.getExpectedAmount() == null || modification.getPriceDifference() == null
                || modification.getPriceDifference().signum() <= 0
                || paymentAttempt.getExpectedAmount().compareTo(modification.getPriceDifference()) != 0) {
            throw new IllegalStateException("Late modification payment amount relationship is invalid.");
        }
        if (paymentAttempt.getCurrency() == null || modification.getCurrency() == null
                || verifiedPayment == null || verifiedPayment.currency() == null
                || !paymentAttempt.getCurrency().equalsIgnoreCase(modification.getCurrency())
                || !paymentAttempt.getCurrency().equalsIgnoreCase(verifiedPayment.currency())) {
            throw new IllegalStateException("Late modification payment currency relationship is invalid.");
        }
        long expectedSubunits = toRazorpaySubunits(modification.getPriceDifference(), modification.getCurrency());
        if (verifiedPayment.amountInSubunits() != expectedSubunits) {
            throw new IllegalStateException("Captured late payment amount does not match modification price difference.");
        }
    }

    private void validateSuccessfulModificationPaymentIdentity(
            PaymentAttempt paymentAttempt,
            RazorpayWebhookPayload payload
    ) {
        if (paymentAttempt == null || payload == null
                || paymentAttempt.getStatus() != PaymentStatus.SUCCEEDED
                || paymentAttempt.getPurpose() != PaymentPurpose.MODIFICATION_ADDITIONAL
                || paymentAttempt.getProvider() != PaymentProvider.RAZORPAY
                || paymentAttempt.getProviderOrderId() == null
                || !paymentAttempt.getProviderOrderId().equals(payload.orderId())
                || paymentAttempt.getProviderPaymentId() == null
                || !paymentAttempt.getProviderPaymentId().equals(payload.paymentId())
                || paymentAttempt.getBooking() == null
                || paymentAttempt.getBookingModification() == null) {
            throw new IllegalStateException("Successful modification payment identity is invalid.");
        }
    }

    private RazorpayWebhookPayload parsePayload(
            String rawPayload
    ) {
        try {
            JSONObject root =
                    new JSONObject(
                            rawPayload
                    );
            String eventType =
                    requireText(
                            root.optString(
                                    "event",
                                    null
                            ),
                            "Razorpay event type"
                    );
            /*
             * Refund details are parsed by the dedicated refund
             * processor from the same already-verified durable raw
             * payload.
             */
            if (!"payment.captured".equals(
                    eventType
            )) {
                return new RazorpayWebhookPayload(
                        eventType,
                        null,
                        null
                );
            }
            JSONObject payload =
                    requireObject(
                            root,
                            "payload"
                    );
            JSONObject paymentContainer =
                    requireObject(
                            payload,
                            "payment"
                    );
            JSONObject payment =
                    requireObject(
                            paymentContainer,
                            "entity"
                    );
            String paymentId =
                    requireText(
                            payment.optString(
                                    "id",
                                    null
                            ),
                            "Razorpay payment ID"
                    );
            String orderId =
                    requireText(
                            payment.optString(
                                    "order_id",
                                    null
                            ),
                            "Razorpay order ID"
                    );
            return new RazorpayWebhookPayload(
                    eventType,
                    paymentId,
                    orderId
            );
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Invalid Razorpay webhook payload.",
                    exception
            );
        }
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
    private void revalidateRelationships(
            PaymentAttempt paymentAttempt,
            BookingPaymentConfirmationService
                    .LockedConfirmationContext context
    ) {
        if (paymentAttempt.getProvider()
                != PaymentProvider.RAZORPAY) {
            throw new IllegalStateException(
                    "Payment provider mismatch."
            );
        }
        if (paymentAttempt.getBooking() == null
                || paymentAttempt
                        .getBooking()
                        .getPublicId() == null
                || context.booking() == null
                || context.booking().getPublicId() == null
                || !paymentAttempt
                        .getBooking()
                        .getPublicId()
                        .equals(
                                context
                                        .booking()
                                        .getPublicId()
                        )) {
            throw new IllegalStateException(
                    "Payment and booking relationship mismatch."
            );
        }
        if (paymentAttempt.getUser() == null
                || context.booking().getUser() == null
                || paymentAttempt.getUser().getId() == null
                || context.booking().getUser().getId() == null
                || !paymentAttempt
                        .getUser()
                        .getId()
                        .equals(
                                context
                                        .booking()
                                        .getUser()
                                        .getId()
                        )) {
            throw new IllegalStateException(
                    "Payment and booking owners do not match."
            );
        }
    }
    private void validateProviderPayment(
            PaymentAttempt paymentAttempt,
            VerifiedRazorpayPayment payment,
            RazorpayWebhookPayload payload
    ) {
        if (payment == null) {
            throw new IllegalStateException(
                    "Verified Razorpay payment is missing."
            );
        }
        if (!payload.paymentId().equals(
                payment.paymentId()
        )) {
            throw new IllegalStateException(
                    "Razorpay payment identifier mismatch."
            );
        }
        if (!payload.orderId().equals(
                payment.orderId()
        )) {
            throw new IllegalStateException(
                    "Razorpay payment order mismatch."
            );
        }
        if (!payload.orderId().equals(
                paymentAttempt.getProviderOrderId()
        )) {
            throw new IllegalStateException(
                    "Payment attempt order mismatch."
            );
        }
        long expectedSubunits =
                toRazorpaySubunits(
                        paymentAttempt
                                .getExpectedAmount(),
                        paymentAttempt
                                .getCurrency()
                );
        if (payment.amountInSubunits()
                != expectedSubunits) {
            throw new IllegalStateException(
                    "Razorpay payment amount mismatch."
            );
        }
        if (payment.currency() == null
                || paymentAttempt.getCurrency() == null
                || !paymentAttempt
                        .getCurrency()
                        .equalsIgnoreCase(
                                payment.currency()
                        )) {
            throw new IllegalStateException(
                    "Razorpay payment currency mismatch."
            );
        }
        if (!"captured".equalsIgnoreCase(
                payment.status()
        )
                || !payment.captured()) {
            throw new IllegalStateException(
                    "Razorpay payment is not captured."
            );
        }
        String existingPaymentId =
                paymentAttempt
                        .getProviderPaymentId();
        if (existingPaymentId != null
                && !existingPaymentId.isBlank()
                && !existingPaymentId.equals(
                        payment.paymentId()
                )) {
            throw new IllegalStateException(
                    "Payment attempt is associated with another Razorpay payment."
            );
        }
    }
        private void validateAlreadyCompletedPayment(
                PaymentAttempt paymentAttempt,
                Booking booking,
                RazorpayWebhookPayload payload
        ) {
        if (paymentAttempt == null) {
                throw new IllegalStateException(
                        "Completed payment attempt is missing."
                );
        }
        if (payload == null) {
                throw new IllegalStateException(
                        "Completed payment webhook payload is missing."
                );
        }
        if (paymentAttempt.getProvider()
                != PaymentProvider.RAZORPAY) {
                throw new IllegalStateException(
                        "Completed payment provider mismatch."
                );
        }
        if (paymentAttempt.getProviderOrderId() == null
                || payload.orderId() == null
                || !paymentAttempt
                        .getProviderOrderId()
                        .equals(
                                payload.orderId()
                        )) {
                throw new IllegalStateException(
                        "Completed payment order mismatch."
                );
        }
        if (paymentAttempt.getProviderPaymentId() == null
                || payload.paymentId() == null
                || !paymentAttempt
                        .getProviderPaymentId()
                        .equals(
                                payload.paymentId()
                        )) {
                throw new IllegalStateException(
                        "Completed payment identifier mismatch."
                );
        }
        if (booking == null
                || booking.getPublicId() == null) {
                throw new IllegalStateException(
                        "Successful payment has no valid booking."
                );
        }
        /*
        * A historical payment.captured webhook may legitimately arrive
        * after the customer has cancelled a previously CONFIRMED booking.
        *
        * CANCELLED status by itself is not enough proof.
        *
        * For a cancelled booking we require:
        *
        * 1. a real cancellation timestamp;
        * 2. a durable Refund associated with this booking;
        * 3. the Refund to belong to Razorpay;
        * 4. the Refund to reference this exact successful Razorpay
        *    payment.
        *
        * This prevents a malformed/inconsistent CANCELLED booking from
        * making an old payment webhook appear legitimate.
        */
        if (booking.getStatus()
                == BookingStatus.CANCELLED) {
                if (booking.getCancelledAt() == null) {
                throw new IllegalStateException(
                        "Cancelled booking has no cancellation timestamp."
                );
                }
                if (refundRepository == null) {
                throw new IllegalStateException(
                        "Refund repository is unavailable for cancelled booking validation."
                );
                }
                boolean validRefundRelationship =
                        refundRepository
                                .existsByBookingPublicIdAndProviderAndProviderPaymentId(
                                        booking.getPublicId(),
                                        PaymentProvider.RAZORPAY,
                                        paymentAttempt
                                                .getProviderPaymentId()
                                );
                if (!validRefundRelationship) {
                throw new IllegalStateException(
                        "Cancelled booking does not have a valid refund relationship for this payment."
                );
                }
        } else if (booking.getStatus()
                != BookingStatus.CONFIRMED) {
                throw new IllegalStateException(
                        "Successful payment has an invalid booking lifecycle state."
                );
        }
        InventoryHold hold =
                booking.getInventoryHold();
        /*
        * The original successful payment must still belong to the
        * authoritative inventory hold that was consumed when the booking
        * was confirmed.
        *
        * Cancellation releases bookable availability through Booking
        * lifecycle state. It does not rewrite the historical consumed
        * hold.
        */
        if (hold == null
                || hold.getStatus()
                != InventoryHoldStatus.CONSUMED) {
                throw new IllegalStateException(
                        "Successful payment does not have a consumed inventory hold."
                );
        }
        /*
        * Finally preserve the original Step 10 financial relationship:
        *
        * PaymentAttempt expected amount
        *      ==
        * Booking total amount
        *      ==
        * InventoryHold total amount
        *
        * and all currencies must agree.
        */
        validateAuthoritativeAmounts(
                paymentAttempt,
                booking,
                hold
        );
        }
    private void validateAuthoritativeAmounts(
            PaymentAttempt paymentAttempt,
            Booking booking,
            InventoryHold hold
    ) {
        if (paymentAttempt.getExpectedAmount() == null
                || booking == null
                || booking.getTotalAmount() == null
                || hold == null
                || hold.getTotalAmount() == null) {
            throw new IllegalStateException(
                    "SpatialStay payment amount relationship is incomplete."
            );
        }
        if (paymentAttempt
                .getExpectedAmount()
                .compareTo(
                        booking.getTotalAmount()
                ) != 0
                || paymentAttempt
                        .getExpectedAmount()
                        .compareTo(
                                hold.getTotalAmount()
                        ) != 0) {
            throw new IllegalStateException(
                    "SpatialStay payment amount relationship mismatch."
            );
        }
        if (paymentAttempt.getCurrency() == null
                || booking.getCurrency() == null
                || hold.getCurrency() == null) {
            throw new IllegalStateException(
                    "SpatialStay payment currency relationship is incomplete."
            );
        }
        String expectedCurrency =
                paymentAttempt
                        .getCurrency()
                        .toUpperCase(
                                Locale.ROOT
                        );
        if (!expectedCurrency.equals(
                booking
                        .getCurrency()
                        .toUpperCase(
                                Locale.ROOT
                        ))
                || !expectedCurrency.equals(
                        hold
                                .getCurrency()
                                .toUpperCase(
                                        Locale.ROOT
                                ))) {
            throw new IllegalStateException(
                    "SpatialStay payment currency relationship mismatch."
            );
        }
    }
        private void validateModificationPaymentRelationships(
                PaymentAttempt paymentAttempt,
                BookingModificationPaymentConfirmationService
                        .LockedModificationContext context
        ) {
        if (paymentAttempt == null) {
                throw new IllegalStateException(
                        "Modification payment attempt is missing."
                );
        }
        if (context == null
                || context.modification() == null
                || context.booking() == null
                || context.replacementHold() == null) {
                throw new IllegalStateException(
                        "Locked modification payment context is incomplete."
                );
        }
        if (paymentAttempt.getProvider()
                != PaymentProvider.RAZORPAY) {
                throw new IllegalStateException(
                        "Modification payment provider mismatch."
                );
        }
        if (paymentAttempt.getPurpose()
                != PaymentPurpose.MODIFICATION_ADDITIONAL) {
                throw new IllegalStateException(
                        "Payment attempt has an invalid modification payment purpose."
                );
        }
        if (paymentAttempt.getBookingModification() == null
                || paymentAttempt
                        .getBookingModification()
                        .getPublicId() == null
                || context
                        .modification()
                        .getPublicId() == null
                || !paymentAttempt
                        .getBookingModification()
                        .getPublicId()
                        .equals(
                                context
                                        .modification()
                                        .getPublicId()
                        )) {
                throw new IllegalStateException(
                        "Payment attempt does not belong to the locked booking modification."
                );
        }
        if (paymentAttempt.getBooking() == null
                || paymentAttempt
                        .getBooking()
                        .getPublicId() == null
                || context
                        .booking()
                        .getPublicId() == null
                || !paymentAttempt
                        .getBooking()
                        .getPublicId()
                        .equals(
                                context
                                        .booking()
                                        .getPublicId()
                        )) {
                throw new IllegalStateException(
                        "Modification payment booking relationship is invalid."
                );
        }
        if (paymentAttempt.getUser() == null
                || context
                        .modification()
                        .getUser() == null
                || paymentAttempt
                        .getUser()
                        .getId() == null
                || context
                        .modification()
                        .getUser()
                        .getId() == null
                || !paymentAttempt
                        .getUser()
                        .getId()
                        .equals(
                                context
                                        .modification()
                                        .getUser()
                                        .getId()
                        )) {
                throw new IllegalStateException(
                        "Modification payment owner relationship is invalid."
                );
        }
        if (context
                .modification()
                .getReplacementHold() == null
                || context
                        .modification()
                        .getReplacementHold()
                        .getPublicId() == null
                || context
                        .replacementHold()
                        .getPublicId() == null
                || !context
                        .modification()
                        .getReplacementHold()
                        .getPublicId()
                        .equals(
                                context
                                        .replacementHold()
                                        .getPublicId()
                        )) {
                throw new IllegalStateException(
                        "Modification payment replacement-hold relationship is invalid."
                );
        }
        }
        private void validateModificationPaymentAmount(
                PaymentAttempt paymentAttempt,
                BookingModificationPaymentConfirmationService
                        .LockedModificationContext context,
                VerifiedRazorpayPayment verifiedPayment
        ) {
        if (paymentAttempt.getExpectedAmount() == null
                || context
                        .modification()
                        .getPriceDifference() == null) {
                throw new IllegalStateException(
                        "Modification payment amount relationship is incomplete."
                );
        }
        if (context
                .modification()
                .getPriceDifference()
                .signum() <= 0) {
                throw new IllegalStateException(
                        "Modification additional-payment difference must be positive."
                );
        }
        if (paymentAttempt
                .getExpectedAmount()
                .compareTo(
                        context
                                .modification()
                                .getPriceDifference()
                ) != 0) {
                throw new IllegalStateException(
                        "Modification payment amount does not match the authoritative price difference."
                );
        }
        /*
        * validateProviderPayment() has already verified the Razorpay
        * amount against PaymentAttempt.expectedAmount.
        *
        * We deliberately verify it again against the modification
        * price difference so all three values agree.
        */
        long expectedSubunits =
                toRazorpaySubunits(
                        context
                                .modification()
                                .getPriceDifference(),
                        context
                                .modification()
                                .getCurrency()
                );
        if (verifiedPayment == null
                || verifiedPayment.amountInSubunits()
                != expectedSubunits) {
                throw new IllegalStateException(
                        "Captured Razorpay amount does not match the modification price difference."
                );
        }
        if (paymentAttempt.getCurrency() == null
                || context
                        .modification()
                        .getCurrency() == null
                || verifiedPayment.currency() == null) {
                throw new IllegalStateException(
                        "Modification payment currency relationship is incomplete."
                );
        }
        String expectedCurrency =
                paymentAttempt
                        .getCurrency()
                        .toUpperCase(
                                Locale.ROOT
                        );
        if (!expectedCurrency.equals(
                context
                        .modification()
                        .getCurrency()
                        .toUpperCase(
                                Locale.ROOT
                        ))
                || !expectedCurrency.equals(
                        verifiedPayment
                                .currency()
                                .toUpperCase(
                                        Locale.ROOT
                                ))) {
                throw new IllegalStateException(
                        "Modification payment currency relationship mismatch."
                );
        }
        }
        private void validateAlreadyCompletedModificationPayment(
                PaymentAttempt paymentAttempt,
                RazorpayWebhookPayload payload
        ) {
        if (paymentAttempt == null) {
                throw new IllegalStateException(
                        "Completed modification payment attempt is missing."
                );
        }
        if (payload == null) {
                throw new IllegalStateException(
                        "Completed modification payment webhook payload is missing."
                );
        }
        if (paymentAttempt.getPurpose()
                != PaymentPurpose.MODIFICATION_ADDITIONAL) {
                throw new IllegalStateException(
                        "Completed payment has an invalid modification payment purpose."
                );
        }
        if (paymentAttempt.getStatus()
                != PaymentStatus.SUCCEEDED) {
                throw new IllegalStateException(
                        "Modification payment is not successful."
                );
        }
        if (paymentAttempt.getProvider()
                != PaymentProvider.RAZORPAY) {
                throw new IllegalStateException(
                        "Completed modification payment provider mismatch."
                );
        }
        if (paymentAttempt.getProviderOrderId() == null
                || payload.orderId() == null
                || !paymentAttempt
                        .getProviderOrderId()
                        .equals(
                                payload.orderId()
                        )) {
                throw new IllegalStateException(
                        "Completed modification payment order mismatch."
                );
        }
        if (paymentAttempt.getProviderPaymentId() == null
                || payload.paymentId() == null
                || !paymentAttempt
                        .getProviderPaymentId()
                        .equals(
                                payload.paymentId()
                        )) {
                throw new IllegalStateException(
                        "Completed modification payment identifier mismatch."
                );
        }
        if (paymentAttempt.getBookingModification() == null) {
                throw new IllegalStateException(
                        "Successful modification payment has no modification."
                );
        }
        if (paymentAttempt
                .getBookingModification()
                .getStatus()
                != com.spatialstay.booking
                        .BookingModificationStatus
                        .COMPLETED) {
                throw new IllegalStateException(
                        "Successful modification payment does not belong to a completed modification."
                );
        }
        if (paymentAttempt
                .getBookingModification()
                .getReplacementHold() == null
                || paymentAttempt
                        .getBookingModification()
                        .getReplacementHold()
                        .getStatus()
                != InventoryHoldStatus.CONSUMED) {
                throw new IllegalStateException(
                        "Completed modification does not have consumed replacement inventory."
                );
        }
        if (paymentAttempt.getBooking() == null
                || paymentAttempt
                        .getBooking()
                        .getStatus()
                != BookingStatus.CONFIRMED) {
                throw new IllegalStateException(
                        "Completed modification payment does not belong to a confirmed booking."
                );
        }
        if (paymentAttempt.getExpectedAmount() == null
                || paymentAttempt
                        .getBookingModification()
                        .getPriceDifference() == null
                || paymentAttempt
                        .getExpectedAmount()
                        .compareTo(
                                paymentAttempt
                                        .getBookingModification()
                                        .getPriceDifference()
                        ) != 0) {
                throw new IllegalStateException(
                        "Completed modification payment amount relationship is invalid."
                );
        }
        if (paymentAttempt.getCurrency() == null
                || paymentAttempt
                        .getBookingModification()
                        .getCurrency() == null
                || !paymentAttempt
                        .getCurrency()
                        .equalsIgnoreCase(
                                paymentAttempt
                                        .getBookingModification()
                                        .getCurrency()
                        )) {
                throw new IllegalStateException(
                        "Completed modification payment currency relationship is invalid."
                );
        }
        }
    private long toRazorpaySubunits(
            BigDecimal amount,
            String currency
    ) {
        if (amount == null
                || amount.signum() <= 0) {
            throw new IllegalStateException(
                    "Expected payment amount is invalid."
            );
        }
        if (!"INR".equalsIgnoreCase(
                currency
        )) {
            throw new IllegalStateException(
                    "Razorpay payment currently supports INR only."
            );
        }
        try {
            return amount
                    .multiply(
                            SUBUNITS_PER_INR
                    )
                    .setScale(
                            0,
                            RoundingMode.UNNECESSARY
                    )
                    .longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "Expected payment amount cannot be represented in paise.",
                    exception
            );
        }
    }
    private boolean isRefundEvent(
            String eventType
    ) {
        return "refund.created".equals(eventType)
                || "refund.processed".equals(eventType)
                || "refund.failed".equals(eventType);
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
    private record RazorpayWebhookPayload(
            String eventType,
            String paymentId,
            String orderId
    ) {
    }
    public enum ProcessingResult {
        PROCESSED,
        IGNORED,
        REQUIRES_RECONCILIATION
    }
}