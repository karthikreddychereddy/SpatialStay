package com.spatialstay.payment;

import com.spatialstay.booking.Booking;
import com.spatialstay.booking.BookingRepository;
import com.spatialstay.booking.BookingStatus;
import com.spatialstay.booking.InventoryHold;
import com.spatialstay.user.CurrentUser;
import com.spatialstay.user.User;
import com.spatialstay.user.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;


@Service
public class PaymentService {

    private final BookingRepository bookingRepository;

    private final PaymentAttemptRepository paymentAttemptRepository;

    private final UserRepository userRepository;

    private final CurrentUser currentUser;

    private final PaymentGateway paymentGateway;

    private final String razorpayKeyId;


    public PaymentService(

            BookingRepository bookingRepository,

            PaymentAttemptRepository paymentAttemptRepository,

            UserRepository userRepository,

            CurrentUser currentUser,

            PaymentGateway paymentGateway,

            @Value("${spatialstay.payment.razorpay.key-id:}")
            String razorpayKeyId

    ) {

        this.bookingRepository =
                bookingRepository;

        this.paymentAttemptRepository =
                paymentAttemptRepository;

        this.userRepository =
                userRepository;

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
     * Creates an internal payment attempt only.
     *
     * This method intentionally does not contact Razorpay.
     * It remains useful for internal flows and tests.
     */
    @Transactional
    public PaymentAttemptResponse createPaymentAttempt(
            UUID bookingPublicId
    ) {

        PaymentAttempt paymentAttempt =
                createPaymentAttemptEntity(
                        bookingPublicId
                );

        return PaymentAttemptResponse.from(
                paymentAttempt
        );
    }


    /*
     * Step 10.4:
     *
     * Creates the SpatialStay payment attempt and then creates
     * the corresponding provider order using the authoritative
     * booking amount.
     *
     * IMPORTANT:
     * Creating a Razorpay order is NOT proof of payment.
     *
     * Booking remains PENDING_PAYMENT.
     * Inventory hold remains ACTIVE.
     * Payment becomes PENDING only because a provider order now
     * exists.
     */
    @Transactional
    public PaymentCheckoutResponse createCheckout(
            UUID bookingPublicId
    ) {

        PaymentAttempt paymentAttempt =
                createPaymentAttemptEntity(
                        bookingPublicId
                );

        ProviderOrderResult providerOrder =
                paymentGateway.createOrder(
                        paymentAttempt
                );

        Instant now =
                Instant.now();

        InventoryHold hold =
                paymentAttempt
                        .getBooking()
                        .getInventoryHold();

        /*
         * The provider call may take time.
         *
         * Re-check the authoritative hold before persisting the
         * checkout session. We must not hand the customer a new
         * checkout order for an already-expired reservation.
         */
        if (hold == null
                || !hold.isActiveAt(now)) {

            paymentAttempt.markExpired(
                    now
            );

            throw new IllegalStateException(
                    "The inventory hold expired while creating the payment checkout."
            );
        }

        paymentAttempt.markPending(
                providerOrder.providerOrderId(),
                hold.getExpiresAt(),
                now
        );

        paymentAttemptRepository.saveAndFlush(
                paymentAttempt
        );

        return new PaymentCheckoutResponse(
                paymentAttempt.getPublicId(),
                paymentAttempt
                        .getBooking()
                        .getPublicId(),
                paymentAttempt
                        .getBooking()
                        .getBookingReference(),
                paymentAttempt.getProvider(),
                paymentAttempt.getStatus(),
                paymentAttempt.getExpectedAmount(),
                providerOrder.amountInSubunits(),
                paymentAttempt.getCurrency(),
                providerOrder.providerOrderId(),
                checkoutKeyId(),
                hold.getExpiresAt()
        );
    }


    @Transactional(readOnly = true)
    public PaymentAttemptResponse getOwnedPaymentAttempt(
            UUID paymentPublicId
    ) {

        if (paymentPublicId == null) {

            throw new IllegalArgumentException(
                    "Payment ID is required."
            );
        }

        UUID userPublicId =
                currentUser.publicId();

        PaymentAttempt paymentAttempt =
                paymentAttemptRepository
                        .findOwnedByPublicId(
                                paymentPublicId,
                                userPublicId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Payment attempt not found."
                                        )
                        );

        return PaymentAttemptResponse.from(
                paymentAttempt
        );
    }


    @Transactional(readOnly = true)
    public List<PaymentAttemptResponse>
    getOwnedPaymentAttemptsForBooking(
            UUID bookingPublicId
    ) {

        if (bookingPublicId == null) {

            throw new IllegalArgumentException(
                    "Booking ID is required."
            );
        }

        UUID userPublicId =
                currentUser.publicId();

        bookingRepository
                .findByPublicIdAndUserPublicId(
                        bookingPublicId,
                        userPublicId
                )
                .orElseThrow(
                        () ->
                                new IllegalArgumentException(
                                        "Booking not found."
                                )
                );

        return paymentAttemptRepository
                .findAllByBookingPublicIdOrderByCreatedAtDesc(
                        bookingPublicId
                )
                .stream()
                .map(
                        PaymentAttemptResponse::from
                )
                .toList();
    }


    private PaymentAttempt createPaymentAttemptEntity(
            UUID bookingPublicId
    ) {

        if (bookingPublicId == null) {

            throw new IllegalArgumentException(
                    "Booking ID is required."
            );
        }

        UUID userPublicId =
                currentUser.publicId();

        Booking booking =
                bookingRepository
                        .findByPublicIdAndUserPublicId(
                                bookingPublicId,
                                userPublicId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Booking not found."
                                        )
                        );

        Instant now =
                Instant.now();

        validateBookingForPayment(
                booking,
                now
        );

        User user =
                userRepository
                        .findByPublicId(
                                userPublicId
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Authenticated user no longer exists."
                                        )
                        );

        PaymentAttempt paymentAttempt =
                PaymentAttempt.create(
                        user,
                        booking,
                        paymentGateway.provider(),
                        booking.getTotalAmount(),
                        booking.getCurrency(),
                        now
                );

        return paymentAttemptRepository
                .saveAndFlush(
                        paymentAttempt
                );
    }


    private void validateBookingForPayment(
            Booking booking,
            Instant now
    ) {

        if (booking.getStatus()
                != BookingStatus.PENDING_PAYMENT) {

            throw new IllegalStateException(
                    "Only a pending-payment booking can start payment."
            );
        }

        InventoryHold hold =
                booking.getInventoryHold();

        if (hold == null) {

            throw new IllegalStateException(
                    "Booking does not have an inventory hold."
            );
        }

        if (!hold.isActiveAt(now)) {

            throw new IllegalStateException(
                    "The inventory hold is no longer active."
            );
        }

        if (booking.getUser() == null
                || hold.getUser() == null
                || booking
                        .getUser()
                        .getPublicId()
                == null
                || hold
                        .getUser()
                        .getPublicId()
                == null
                || !booking
                        .getUser()
                        .getPublicId()
                        .equals(
                                hold
                                        .getUser()
                                        .getPublicId()
                        )) {

            throw new IllegalStateException(
                    "Booking and inventory hold ownership do not match."
            );
        }

        if (booking.getTotalAmount() == null
                || hold.getTotalAmount() == null
                || booking
                        .getTotalAmount()
                        .compareTo(
                                hold.getTotalAmount()
                        ) != 0) {

            throw new IllegalStateException(
                    "Booking and inventory hold amounts do not match."
            );
        }

        if (booking.getCurrency() == null
                || hold.getCurrency() == null
                || !booking
                        .getCurrency()
                        .equalsIgnoreCase(
                                hold.getCurrency()
                        )) {

            throw new IllegalStateException(
                    "Booking and inventory hold currencies do not match."
            );
        }

        if (booking
                .getTotalAmount()
                .signum() <= 0) {

            throw new IllegalStateException(
                    "Booking payment amount must be positive."
            );
        }
    }


    private String checkoutKeyId() {

        if (razorpayKeyId.isBlank()) {

            throw new PaymentGatewayException(
                    "Razorpay checkout key is not configured."
            );
        }

        return razorpayKeyId;
    }
}