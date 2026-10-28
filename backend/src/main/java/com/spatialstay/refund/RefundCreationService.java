package com.spatialstay.refund;

import com.spatialstay.booking.Booking;
import com.spatialstay.payment.PaymentAttempt;
import com.spatialstay.payment.PaymentStatus;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Service
public class RefundCreationService {

    private final RefundRepository refundRepository;

    public RefundCreationService(
            RefundRepository refundRepository
    ) {
        this.refundRepository =
                refundRepository;
    }

    /**
     * Creates the durable refund obligation.
     *
     * The caller owns the surrounding transaction.
     *
     * Step 11 cancellation invokes this after locking the booking and
     * successful payment attempt. If this method fails, the surrounding
     * cancellation transaction must roll back.
     */
    public Refund createPendingCancellationRefund(
            Booking booking,
            PaymentAttempt paymentAttempt,
            BigDecimal originalAmount,
            BigDecimal cancellationFee,
            BigDecimal refundAmount,
            String currency,
            Instant now
    ) {
        Objects.requireNonNull(
                booking,
                "booking is required"
        );

        Objects.requireNonNull(
                paymentAttempt,
                "paymentAttempt is required"
        );

        Objects.requireNonNull(
                now,
                "now is required"
        );

        Refund existing =
                refundRepository
                        .findByBookingPublicId(
                                booking.getPublicId()
                        )
                        .orElse(null);

        if (existing != null) {
            return existing;
        }

        validateSuccessfulPayment(
                booking,
                paymentAttempt,
                originalAmount,
                currency
        );

        Refund refund =
                Refund.pendingCancellationRefund(
                        booking.getUser(),
                        booking,
                        paymentAttempt,
                        originalAmount,
                        cancellationFee,
                        refundAmount,
                        currency,
                        now
                );

        return refundRepository.saveAndFlush(
                refund
        );
    }

    private void validateSuccessfulPayment(
            Booking booking,
            PaymentAttempt paymentAttempt,
            BigDecimal originalAmount,
            String currency
    ) {
        if (paymentAttempt.getStatus()
                != PaymentStatus.SUCCEEDED) {

            throw new IllegalStateException(
                    "Refund requires a successful payment attempt."
            );
        }

        if (paymentAttempt.getProviderPaymentId() == null
                || paymentAttempt.getProviderPaymentId().isBlank()) {

            throw new IllegalStateException(
                    "Successful payment attempt is missing provider payment ID."
            );
        }

        if (paymentAttempt.getBooking() == null
                || paymentAttempt.getBooking().getId() == null
                || booking.getId() == null
                || !paymentAttempt.getBooking()
                        .getId()
                        .equals(
                                booking.getId()
                        )) {

            throw new IllegalStateException(
                    "Payment attempt does not belong to the booking."
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
                    "Payment attempt customer does not match booking customer."
            );
        }

        if (paymentAttempt.getExpectedAmount() == null
                || paymentAttempt.getExpectedAmount().signum() <= 0
                || originalAmount == null
                || originalAmount.signum() <= 0) {
            throw new IllegalStateException(
                    "Refund financial snapshot is invalid."
            );
        }

        if (paymentAttempt.getCurrency() == null
                || currency == null
                || !paymentAttempt.getCurrency()
                        .equalsIgnoreCase(
                                currency
                        )) {

            throw new IllegalStateException(
                    "Successful payment currency does not match booking currency."
            );
        }
    }
}