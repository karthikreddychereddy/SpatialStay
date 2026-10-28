package com.spatialstay.booking;

import com.spatialstay.user.CurrentUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class BookingCancellationPolicyService {

    /*
     * A customer cannot cancel or modify immediately after payment
     * confirmation.
     *
     * The authoritative starting point is Booking.confirmedAt, which is
     * written only after the trusted payment webhook confirms payment.
     */
    private static final Duration MANAGEMENT_COOLDOWN =
            Duration.ofMinutes(30);

    /*
     * Temporary Step 11 platform policy.
     *
     * Step 12 will replace this with hotel-configurable cancellation
     * policies, including the hotel's timezone and check-in time.
     *
     * For now:
     *
     * 2+ calendar days before check-in -> 100% refund
     * 1 calendar day before check-in  -> 50% refund
     * check-in day or later           -> cancellation unavailable
     */
    private static final BigDecimal PARTIAL_REFUND_RATE =
            new BigDecimal("0.50");

    private static final String POLICY_DESCRIPTION =
            "Temporary platform cancellation policy: full refund at least "
                    + "2 calendar days before check-in, 50% refund 1 calendar "
                    + "day before check-in, and non-refundable from check-in day. "
                    + "Booking management becomes available 30 minutes "
                    + "after payment confirmation.";

    private final BookingRepository bookingRepository;
    private final CurrentUser currentUser;

    public BookingCancellationPolicyService(
            BookingRepository bookingRepository,
            CurrentUser currentUser
    ) {
        this.bookingRepository =
                bookingRepository;

        this.currentUser =
                currentUser;
    }

    /**
     * Returns an authoritative cancellation preview for a booking owned
     * by the currently authenticated customer.
     *
     * This method never changes booking state and never performs a refund.
     */
    @Transactional(readOnly = true)
    public CancellationPreviewResponse preview(
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
                                () -> new IllegalArgumentException(
                                        "Booking does not exist or is not accessible."
                                )
                        );

        return evaluate(
                booking,
                Instant.now()
        );
    }

    /**
     * Evaluates cancellation eligibility for an already loaded booking.
     *
     * Package-private intentionally.
     *
     * Step 11.5 uses this method after acquiring a pessimistic lock on
     * the booking. Therefore cancellation eligibility is recalculated
     * inside the same authoritative transaction that changes booking
     * state.
     *
     * The frontend preview is never trusted as cancellation authority.
     */
    CancellationPreviewResponse evaluate(
            Booking booking,
            Instant now
    ) {
        if (booking == null) {
            throw new IllegalArgumentException(
                    "Booking is required."
            );
        }

        if (now == null) {
            throw new IllegalArgumentException(
                    "Current time is required."
            );
        }

        BigDecimal originalAmount =
                money(
                        booking.getTotalAmount()
                );

        /*
         * Only confirmed bookings participate in customer cancellation.
         *
         * PENDING_PAYMENT, EXPIRED and CANCELLED bookings are rejected.
         */
        if (booking.getStatus()
                != BookingStatus.CONFIRMED) {

            return response(
                    booking,
                    false,
                    CancellationEligibilityReason.BOOKING_NOT_CONFIRMED,
                    "Only confirmed bookings can be cancelled.",
                    null,
                    originalAmount,
                    originalAmount,
                    BigDecimal.ZERO
            );
        }

        Instant confirmedAt =
                booking.getConfirmedAt();

        if (confirmedAt == null) {
            throw new IllegalStateException(
                    "Confirmed booking is missing its confirmation timestamp."
            );
        }

        Instant managementAvailableAt =
                confirmedAt.plus(
                        MANAGEMENT_COOLDOWN
                );

        /*
         * First enforce the agreed 30-minute management cooldown.
         */
        if (now.isBefore(
                managementAvailableAt
        )) {

            return response(
                    booking,
                    false,
                    CancellationEligibilityReason.MANAGEMENT_COOLDOWN,
                    "Booking management becomes available 30 minutes after confirmation.",
                    managementAvailableAt,
                    originalAmount,
                    originalAmount,
                    BigDecimal.ZERO
            );
        }

        /*
         * Booking currently stores LocalDate rather than an exact
         * hotel-local check-in timestamp.
         *
         * Until Step 12 adds hotel timezone/check-in-time configuration,
         * use a deterministic calendar-day policy rather than pretending
         * that we know the exact hotel check-in hour.
         */
        LocalDate today =
                now.atZone(
                        ZoneOffset.UTC
                ).toLocalDate();

        LocalDate checkIn =
                booking.getCheckIn();

        if (checkIn == null) {
            throw new IllegalStateException(
                    "Booking check-in date is missing."
            );
        }

        /*
         * Check-in day has started, or the stay is already in the past.
         */
        if (!today.isBefore(
                checkIn
        )) {

            return response(
                    booking,
                    false,
                    CancellationEligibilityReason.CHECK_IN_ALREADY_STARTED,
                    "This booking can no longer be cancelled because the check-in date has started.",
                    managementAvailableAt,
                    originalAmount,
                    originalAmount,
                    BigDecimal.ZERO
            );
        }

        long daysBeforeCheckIn =
                ChronoUnit.DAYS.between(
                        today,
                        checkIn
                );

        /*
         * Two or more calendar days before check-in:
         * full refund.
         */
        if (daysBeforeCheckIn >= 2) {

            return response(
                    booking,
                    true,
                    CancellationEligibilityReason.ELIGIBLE,
                    "This booking is eligible for a full refund.",
                    managementAvailableAt,
                    originalAmount,
                    BigDecimal.ZERO,
                    originalAmount
            );
        }

        /*
         * Exactly one calendar day before check-in:
         * 50% refund.
         */
        if (daysBeforeCheckIn == 1) {

            BigDecimal refundAmount =
                    originalAmount
                            .multiply(
                                    PARTIAL_REFUND_RATE
                            )
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );

            BigDecimal cancellationFee =
                    originalAmount
                            .subtract(
                                    refundAmount
                            )
                            .setScale(
                                    2,
                                    RoundingMode.HALF_UP
                            );

            return response(
                    booking,
                    true,
                    CancellationEligibilityReason.ELIGIBLE,
                    "This booking is eligible for a 50% refund.",
                    managementAvailableAt,
                    originalAmount,
                    cancellationFee,
                    refundAmount
            );
        }

        /*
         * Defensive fallback.
         *
         * With LocalDate-based calculation this branch should normally
         * not be reached because same-day/past bookings are handled
         * above. It remains explicit so the policy fails closed.
         */
        return response(
                booking,
                false,
                CancellationEligibilityReason.NON_REFUNDABLE_WINDOW,
                "This booking is inside the non-refundable cancellation window.",
                managementAvailableAt,
                originalAmount,
                originalAmount,
                BigDecimal.ZERO
        );
    }

    private CancellationPreviewResponse response(
            Booking booking,
            boolean cancellable,
            CancellationEligibilityReason reason,
            String message,
            Instant managementAvailableAt,
            BigDecimal originalAmount,
            BigDecimal cancellationFee,
            BigDecimal refundAmount
    ) {
        return new CancellationPreviewResponse(
                booking.getPublicId(),
                booking.getBookingReference(),

                cancellable,
                reason,
                message,

                booking.getConfirmedAt(),
                managementAvailableAt,

                booking.getCheckIn(),
                booking.getCheckOut(),

                money(originalAmount),
                money(cancellationFee),
                money(refundAmount),
                booking.getCurrency(),

                POLICY_DESCRIPTION
        );
    }

    private BigDecimal money(
            BigDecimal amount
    ) {
        if (amount == null) {
            throw new IllegalStateException(
                    "Booking amount is missing."
            );
        }

        if (amount.signum() < 0) {
            throw new IllegalStateException(
                    "Booking amount cannot be negative."
            );
        }

        return amount.setScale(
                2,
                RoundingMode.HALF_UP
        );
    }
}