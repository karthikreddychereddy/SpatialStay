package com.spatialstay.modificationrefund;

import com.spatialstay.booking.Booking;
import com.spatialstay.booking.BookingModification;
import com.spatialstay.payment.PaymentAttempt;
import com.spatialstay.payment.PaymentAttemptRepository;
import com.spatialstay.payment.PaymentProvider;
import com.spatialstay.payment.PaymentStatus;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class ModificationRefundAllocationService {

    private final PaymentAttemptRepository
            paymentAttemptRepository;

    private final ModificationRefundAllocationRepository
            allocationRepository;

    public ModificationRefundAllocationService(
            PaymentAttemptRepository paymentAttemptRepository,
            ModificationRefundAllocationRepository allocationRepository
    ) {
        this.paymentAttemptRepository =
                paymentAttemptRepository;

        this.allocationRepository =
                allocationRepository;
    }

    public void createAllocations(
            ModificationRefundBatch batch,
            Booking booking,
            BookingModification modification,
            BigDecimal requiredRefund,
            String currency,
            Instant now
    ) {
        Objects.requireNonNull(
                batch,
                "batch is required"
        );

        Objects.requireNonNull(
                booking,
                "booking is required"
        );

        Objects.requireNonNull(
                modification,
                "modification is required"
        );

        BigDecimal remaining =
                money(
                        requiredRefund
                );

        if (remaining.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Required refund must be positive."
            );
        }

        String normalizedCurrency =
                normalizeCurrency(
                        currency
                );

        /*
         * Repository returns newest successful capture first.
         *
         * That means additional modification payments are naturally
         * unwound before older money whenever possible.
         */
        List<PaymentAttempt> payments =
                paymentAttemptRepository
                        .findAllSucceededByBookingForUpdate(
                                booking.getPublicId()
                        );

        if (payments.isEmpty()) {
            throw new IllegalStateException(
                    "Booking has no successful captured payments."
            );
        }

        for (PaymentAttempt payment : payments) {
            if (remaining.signum() <= 0) {
                break;
            }

            validatePayment(
                    booking,
                    payment,
                    normalizedCurrency
            );

            BigDecimal alreadyCommitted =
                    allocationRepository
                            .sumCommittedRefundAmountForPayment(
                                    payment.getPublicId()
                            );

            if (alreadyCommitted == null) {
                alreadyCommitted =
                        BigDecimal.ZERO
                                .setScale(2);
            }

            BigDecimal available =
                    payment.getExpectedAmount()
                            .subtract(
                                    alreadyCommitted
                            )
                            .setScale(
                                    2,
                                    RoundingMode.UNNECESSARY
                            );

            if (available.signum() <= 0) {
                continue;
            }

            BigDecimal allocationAmount =
                    available.min(
                            remaining
                    );

            ModificationRefundAllocation allocation =
                    ModificationRefundAllocation.create(
                            batch,
                            payment,
                            allocationAmount,
                            normalizedCurrency,
                            now
                    );

            allocationRepository.save(
                    allocation
            );

            remaining =
                    remaining.subtract(
                            allocationAmount
                    );
        }

        if (remaining.signum() != 0) {
            throw new IllegalStateException(
                    "Insufficient refundable captured balance for modification."
            );
        }
    }

    private void validatePayment(
            Booking booking,
            PaymentAttempt payment,
            String currency
    ) {
        if (payment.getStatus()
                != PaymentStatus.SUCCEEDED) {

            throw new IllegalStateException(
                    "Refund allocation encountered a non-successful payment."
            );
        }

        if (payment.getProvider()
                != PaymentProvider.RAZORPAY) {

            throw new IllegalStateException(
                    "Unsupported refund payment provider."
            );
        }

        if (payment.getProviderPaymentId() == null
                || payment.getProviderPaymentId().isBlank()) {

            throw new IllegalStateException(
                    "Successful payment is missing provider payment ID."
            );
        }

        if (payment.getBooking() == null
                || payment.getBooking().getPublicId() == null
                || booking.getPublicId() == null
                || !payment.getBooking()
                        .getPublicId()
                        .equals(
                                booking.getPublicId()
                        )) {

            throw new IllegalStateException(
                    "Payment does not belong to refund booking."
            );
        }

        if (payment.getCurrency() == null
                || !currency.equalsIgnoreCase(
                        payment.getCurrency()
                )) {

            throw new IllegalStateException(
                    "Payment currency does not match modification refund."
            );
        }

        if (payment.getExpectedAmount() == null
                || payment.getExpectedAmount()
                        .signum() <= 0) {

            throw new IllegalStateException(
                    "Successful payment has invalid captured amount."
            );
        }
    }

    private static BigDecimal money(
            BigDecimal amount
    ) {
        if (amount == null) {
            throw new IllegalArgumentException(
                    "Amount is required."
            );
        }

        return amount.setScale(
                2,
                RoundingMode.UNNECESSARY
        );
    }

    private static String normalizeCurrency(
            String currency
    ) {
        if (currency == null
                || currency.isBlank()) {

            throw new IllegalArgumentException(
                    "Currency is required."
            );
        }

        String normalized =
                currency.trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (normalized.length() != 3) {
            throw new IllegalArgumentException(
                    "Currency must be a 3-letter code."
            );
        }

        return normalized;
    }
}