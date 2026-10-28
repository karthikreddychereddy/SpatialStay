package com.spatialstay.booking;

import com.spatialstay.payment.PaymentAttempt;
import com.spatialstay.payment.PaymentAttemptRepository;

import com.spatialstay.refund.Refund;
import com.spatialstay.refund.RefundCreationService;
import com.spatialstay.refund.RefundAllocationService;
import com.spatialstay.refund.RefundProcessingRequestedEvent;
import com.spatialstay.refund.RefundRepository;

import com.spatialstay.user.CurrentUser;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Service
public class BookingCancellationService {

    private final BookingRepository bookingRepository;

    private final BookingCancellationPolicyService
            cancellationPolicyService;

    private final PaymentAttemptRepository
            paymentAttemptRepository;

    private final RefundCreationService
            refundCreationService;

    private final RefundRepository
            refundRepository;

    private final RefundAllocationService refundAllocationService;

    private final CurrentUser
            currentUser;

    private final ApplicationEventPublisher
            eventPublisher;


    public BookingCancellationService(
            BookingRepository bookingRepository,
            BookingCancellationPolicyService cancellationPolicyService,
            PaymentAttemptRepository paymentAttemptRepository,
            RefundCreationService refundCreationService,
            RefundRepository refundRepository,
            RefundAllocationService refundAllocationService,
            CurrentUser currentUser,
            ApplicationEventPublisher eventPublisher
    ) {

        this.bookingRepository =
                bookingRepository;

        this.cancellationPolicyService =
                cancellationPolicyService;

        this.paymentAttemptRepository =
                paymentAttemptRepository;

        this.refundCreationService =
                refundCreationService;

        this.refundRepository =
                refundRepository;

        this.refundAllocationService = refundAllocationService;

        this.currentUser =
                currentUser;

        this.eventPublisher =
                eventPublisher;
    }


    /**
     * Cancels a confirmed customer booking and atomically creates its
     * durable refund obligation when money is owed.
     *
     * Lock order:
     *
     * Booking
     *   -> successful PaymentAttempt
     *
     * Keep this order stable in future cancellation/refund work.
     *
     * Provider refund execution is intentionally NOT performed inside
     * this transaction.
     *
     * Instead:
     *
     * 1. Booking becomes CANCELLED.
     * 2. Refund becomes PENDING.
     * 3. This transaction commits.
     * 4. AFTER_COMMIT listener starts provider refund processing.
     */
    @Transactional
    public CancelBookingResponse cancel(
            UUID bookingPublicId
    ) {

        if (bookingPublicId == null) {

            throw new IllegalArgumentException(
                    "Booking ID is required."
            );
        }


        UUID userPublicId =
                currentUser.publicId();


        /*
         * First serialization boundary for customer cancellation.
         */
        Booking booking =
                bookingRepository
                        .findOwnedByPublicIdForUpdate(
                                bookingPublicId,
                                userPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Booking does not exist or is not accessible."
                                )
                        );


        /*
         * Idempotent retry after a committed cancellation.
         *
         * Do NOT publish another processing event here.
         *
         * A previous request may already have reached Razorpay. Blindly
         * resubmitting could create a duplicate provider refund.
         */
        if (booking.getStatus()
                == BookingStatus.CANCELLED) {

            return alreadyCancelledResponse(
                    booking
            );
        }


        Instant now =
                Instant.now();


        /*
         * Re-evaluate cancellation policy while the booking row is locked.
         */
        CancellationPreviewResponse preview =
                cancellationPolicyService.evaluate(
                        booking,
                        now
                );


        if (!preview.cancellable()) {

            throw new IllegalStateException(
                    cancellationRejectedMessage(
                            preview
                    )
            );
        }


        if (booking.getStatus()
                != BookingStatus.CONFIRMED) {

            throw new IllegalStateException(
                    "Only confirmed bookings can be cancelled."
            );
        }


        /*
         * The browser never chooses which payment should be refunded.
         *
         * Lock the authoritative successful payment generated by the
         * verified payment lifecycle.
         */
        List<PaymentAttempt> successfulPayments =
                paymentAttemptRepository
                        .findSucceededByBookingForUpdate(
                                booking.getPublicId()
                        );


        if (successfulPayments.isEmpty()) {

            throw new IllegalStateException(
                    "Confirmed booking does not have a successful payment."
            );
        }


        if (successfulPayments.size() != 1) {
            throw new IllegalStateException(
                    "Booking must have exactly one successful original payment."
            );
        }

        PaymentAttempt successfulPayment = successfulPayments.get(0);


        validatePaymentSnapshot(
                booking,
                successfulPayment
        );


        BigDecimal refundAmount =
                preview.refundAmount();


        if (refundAmount == null
                || refundAmount.signum() <= 0) {

            /*
             * Current policy only permits cancellation when a positive
             * refund exists.
             *
             * If a later phase supports zero-refund cancellation, that
             * lifecycle should be handled explicitly.
             */
            throw new IllegalStateException(
                    "Cancellation does not produce a refundable amount."
            );
        }


        /*
         * Transition booking only after every authoritative validation
         * has succeeded.
         */
        booking.markCancelled(
                now
        );


        /*
         * Same database transaction:
         *
         * If Refund creation/save fails, Spring rolls this entire
         * transaction back and Booking remains CONFIRMED.
         */
        Refund refund =
                refundCreationService
                        .createPendingCancellationRefund(
                                booking,
                                successfulPayment,
                                preview.originalAmount(),
                                preview.cancellationFee(),
                                refundAmount,
                                preview.currency(),
                                now
                        );


        if (refund.getPublicId() == null) {

            throw new IllegalStateException(
                    "Created refund is missing its public ID."
            );
        }

        /* Split the cancellation obligation across every still-refundable
         * successful capture. Previous modification refunds are deducted
         * before any cancellation allocation is created. */
        refundAllocationService.createAllocations(
                refund, booking, refundAmount, preview.currency(), now
        );


        /*
         * Publishing this event does NOT call Razorpay here.
         *
         * RefundProcessingRequestedEventListener uses
         * TransactionPhase.AFTER_COMMIT.
         *
         * Therefore:
         *
         * - if this transaction rolls back, listener never runs;
         * - if this transaction commits, provider processing starts;
         * - no booking/refund DB locks are held during Razorpay network IO.
         */
        eventPublisher.publishEvent(
                new RefundProcessingRequestedEvent(
                        refund.getPublicId()
                )
        );


        return new CancelBookingResponse(
                booking.getPublicId(),
                booking.getBookingReference(),

                booking.getStatus(),
                booking.getCancelledAt(),

                refund.getOriginalAmount(),
                refund.getCancellationFee(),
                refund.getRefundAmount(),
                refund.getCurrency(),

                true,

                refund.getPublicId(),
                refund.getStatus(),

                "Booking cancelled successfully. "
                        + "Refund has been queued for secure processing."
        );
    }


    private CancelBookingResponse alreadyCancelledResponse(
            Booking booking
    ) {

        Refund refund =
                refundRepository
                        .findByBookingPublicId(
                                booking.getPublicId()
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Cancelled booking is missing its durable "
                                                + "refund record and requires reconciliation."
                                )
                        );


        return new CancelBookingResponse(
                booking.getPublicId(),
                booking.getBookingReference(),

                BookingStatus.CANCELLED,
                booking.getCancelledAt(),

                refund.getOriginalAmount(),
                refund.getCancellationFee(),
                refund.getRefundAmount(),
                refund.getCurrency(),

                refund.getRefundAmount().signum() > 0,

                refund.getPublicId(),
                refund.getStatus(),

                "Booking is already cancelled. "
                        + "Existing refund status returned."
        );
    }


    private void validatePaymentSnapshot(
            Booking booking,
            PaymentAttempt paymentAttempt
    ) {

        if (paymentAttempt.getBooking() == null
                || paymentAttempt.getBooking().getId() == null
                || booking.getId() == null
                || !paymentAttempt.getBooking()
                        .getId()
                        .equals(
                                booking.getId()
                        )) {

            throw new IllegalStateException(
                    "Successful payment does not belong to this booking."
            );
        }


        if (paymentAttempt.getUser() == null
                || booking.getUser() == null
                || paymentAttempt.getUser().getId() == null
                || booking.getUser().getId() == null
                || !paymentAttempt.getUser()
                        .getId()
                        .equals(
                                booking.getUser().getId()
                        )) {

            throw new IllegalStateException(
                    "Successful payment customer does not match booking customer."
            );
        }


        if (paymentAttempt.getExpectedAmount() == null
                || paymentAttempt.getExpectedAmount().signum() <= 0
                || booking.getTotalAmount() == null
                || booking.getTotalAmount().signum() <= 0) {

            throw new IllegalStateException(
                    "Booking/payment financial snapshot is invalid."
            );
        }


        if (paymentAttempt.getCurrency() == null
                || booking.getCurrency() == null
                || !paymentAttempt.getCurrency()
                        .equalsIgnoreCase(
                                booking.getCurrency()
                        )) {

            throw new IllegalStateException(
                    "Successful payment currency does not match booking currency."
            );
        }


        if (paymentAttempt.getProviderPaymentId() == null
                || paymentAttempt.getProviderPaymentId().isBlank()) {

            throw new IllegalStateException(
                    "Successful payment is missing provider payment ID."
            );
        }
    }


    private String cancellationRejectedMessage(
            CancellationPreviewResponse preview
    ) {

        if (preview.reason()
                == CancellationEligibilityReason.MANAGEMENT_COOLDOWN) {

            return "Booking cannot be cancelled yet. "
                    + preview.message();
        }


        return "Booking cannot be cancelled. "
                + preview.message();
    }
}