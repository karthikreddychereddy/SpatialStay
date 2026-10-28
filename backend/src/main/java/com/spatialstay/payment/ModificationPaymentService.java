package com.spatialstay.payment;

import com.spatialstay.booking.Booking;
import com.spatialstay.booking.BookingModification;
import com.spatialstay.booking.BookingModificationRepository;
import com.spatialstay.booking.BookingModificationStatus;
import com.spatialstay.booking.BookingStatus;
import com.spatialstay.booking.InventoryHold;
import com.spatialstay.booking.InventoryHoldStatus;
import com.spatialstay.booking.ModificationFinancialType;
import com.spatialstay.user.CurrentUser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

@Service
public class ModificationPaymentService {

    private static final EnumSet<PaymentStatus>
            LIVE_PAYMENT_STATUSES =
            EnumSet.of(
                    PaymentStatus.CREATED,
                    PaymentStatus.PENDING,
                    PaymentStatus.PROCESSING,
                    PaymentStatus.SUCCEEDED
            );

    private final BookingModificationRepository
            modificationRepository;

    private final PaymentAttemptRepository
            paymentAttemptRepository;

    private final CurrentUser currentUser;

    private final PaymentGateway paymentGateway;

    private final String razorpayKeyId;

    public ModificationPaymentService(
            BookingModificationRepository modificationRepository,
            PaymentAttemptRepository paymentAttemptRepository,
            CurrentUser currentUser,
            PaymentGateway paymentGateway,
            @Value("${spatialstay.payment.razorpay.key-id:}")
            String razorpayKeyId
    ) {
        this.modificationRepository =
                modificationRepository;

        this.paymentAttemptRepository =
                paymentAttemptRepository;

        this.currentUser =
                currentUser;

        this.paymentGateway =
                paymentGateway;

        this.razorpayKeyId =
                razorpayKeyId == null
                        ? ""
                        : razorpayKeyId.trim();
    }

    /*
     * Creates an additional-payment checkout for a booking
     * modification.
     *
     * The amount always comes from BookingModification.
     *
     * No amount supplied by the browser is trusted.
     */
    @Transactional
    public ModificationPaymentCheckoutResponse createCheckout(
            UUID modificationPublicId
    ) {
        if (modificationPublicId == null) {
            throw new IllegalArgumentException(
                    "Modification ID is required."
            );
        }

        UUID userPublicId =
                currentUser.publicId();

        BookingModification modification =
                modificationRepository
                        .findByPublicIdForUpdate(
                                modificationPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Modification was not found."
                                )
                        );

        validateOwnership(
                modification,
                userPublicId
        );

        validateModification(
                modification
        );

        if (paymentAttemptRepository
                .existsByBookingModificationPublicIdAndPurposeAndStatusIn(
                        modificationPublicId,
                        PaymentPurpose.MODIFICATION_ADDITIONAL,
                        LIVE_PAYMENT_STATUSES
                )) {

            throw new IllegalStateException(
                    "This modification already has an active or successful additional payment."
            );
        }

        Booking booking =
                modification.getBooking();

        InventoryHold replacementHold =
                modification.getReplacementHold();

        Instant now =
                Instant.now();

        if (booking == null
                || booking.getStatus()
                != BookingStatus.CONFIRMED) {

            throw new IllegalStateException(
                    "Original booking is no longer confirmed."
            );
        }

        if (replacementHold == null
                || replacementHold.getStatus()
                != InventoryHoldStatus.ACTIVE
                || !replacementHold.isActiveAt(
                        now
                )) {

            throw new IllegalStateException(
                    "Replacement inventory hold has expired."
            );
        }

        BigDecimal amount =
                modification
                        .getPriceDifference();

        if (amount == null
                || amount.signum() <= 0) {

            throw new IllegalStateException(
                    "Modification additional-payment amount is invalid."
            );
        }

        if (modification.getCurrency() == null
                || booking.getCurrency() == null
                || !modification
                        .getCurrency()
                        .equalsIgnoreCase(
                                booking.getCurrency()
                        )) {

            throw new IllegalStateException(
                    "Modification currency does not match the booking."
            );
        }

        PaymentAttempt paymentAttempt =
                PaymentAttempt
                        .createModificationAdditionalPayment(
                                modification.getUser(),
                                booking,
                                modification,
                                paymentGateway.provider(),
                                amount,
                                modification.getCurrency(),
                                now
                        );

        paymentAttempt =
                paymentAttemptRepository
                        .saveAndFlush(
                                paymentAttempt
                        );

        /*
         * The provider order is created only from the trusted
         * PaymentAttempt expected amount.
         */
        ProviderOrderResult providerOrder =
                paymentGateway.createOrder(
                        paymentAttempt
                );

        Instant afterProviderCall =
                Instant.now();

        /*
         * Provider communication may take time. Recheck the
         * replacement hold before exposing the checkout.
         */
        if (!replacementHold.isActiveAt(
                afterProviderCall
        )) {

            paymentAttempt.markExpired(
                    afterProviderCall
            );

            paymentAttemptRepository
                    .saveAndFlush(
                            paymentAttempt
                    );

            throw new IllegalStateException(
                    "Replacement inventory hold expired while creating the additional-payment checkout."
            );
        }

        paymentAttempt.markPending(
                providerOrder.providerOrderId(),
                replacementHold.getExpiresAt(),
                afterProviderCall
        );

        paymentAttemptRepository
                .saveAndFlush(
                        paymentAttempt
                );

        return new ModificationPaymentCheckoutResponse(
                paymentAttempt.getPublicId(),

                booking.getPublicId(),

                modification.getPublicId(),

                booking.getBookingReference(),

                paymentAttempt.getProvider(),

                paymentAttempt.getStatus(),

                paymentAttempt.getPurpose(),

                paymentAttempt.getExpectedAmount(),

                providerOrder.amountInSubunits(),

                paymentAttempt.getCurrency(),

                providerOrder.providerOrderId(),

                checkoutKeyId(),

                replacementHold.getExpiresAt()
        );
    }

    private void validateOwnership(
            BookingModification modification,
            UUID userPublicId
    ) {
        if (modification.getUser() == null
                || modification
                        .getUser()
                        .getPublicId() == null
                || !modification
                        .getUser()
                        .getPublicId()
                        .equals(
                                userPublicId
                        )) {

            /*
             * Do not reveal whether another customer's
             * modification exists.
             */
            throw new IllegalArgumentException(
                    "Modification was not found."
            );
        }
    }

    private void validateModification(
            BookingModification modification
    ) {
        if (modification.getFinancialType()
                != ModificationFinancialType
                .ADDITIONAL_PAYMENT) {

            throw new IllegalStateException(
                    "This modification does not require additional payment."
            );
        }

        if (modification.getStatus()
                != BookingModificationStatus
                .AWAITING_PAYMENT) {

            throw new IllegalStateException(
                    "Modification is not awaiting additional payment."
            );
        }

        if (modification.getPriceDifference()
                == null
                || modification
                        .getPriceDifference()
                        .signum() <= 0) {

            throw new IllegalStateException(
                    "Modification price difference is invalid."
            );
        }
    }

    private String checkoutKeyId() {
        if (razorpayKeyId.isBlank()) {
            throw new IllegalStateException(
                    "Razorpay checkout key ID is not configured."
            );
        }

        return razorpayKeyId;
    }
}