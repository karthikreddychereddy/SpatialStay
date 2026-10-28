package com.spatialstay.booking;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class BookingPaymentConfirmationService {

    private final InventoryHoldRepository
            inventoryHoldRepository;

    private final BookingRepository
            bookingRepository;


    public BookingPaymentConfirmationService(
            InventoryHoldRepository inventoryHoldRepository,
            BookingRepository bookingRepository
    ) {
        this.inventoryHoldRepository =
                inventoryHoldRepository;

        this.bookingRepository =
                bookingRepository;
    }


    /**
     * Acquires the authoritative Step 9 lifecycle lock.
     *
     * Payment confirmation and hold expiration must serialize
     * through the same InventoryHold row.
     */
    public LockedConfirmationContext lockForPaymentConfirmation(
            UUID holdPublicId,
            UUID expectedBookingPublicId,
            Instant now
    ) {
        Objects.requireNonNull(
                holdPublicId,
                "holdPublicId is required"
        );

        Objects.requireNonNull(
                expectedBookingPublicId,
                "expectedBookingPublicId is required"
        );

        Objects.requireNonNull(
                now,
                "now is required"
        );


        InventoryHold hold =
                inventoryHoldRepository
                        .findByPublicIdForUpdate(
                                holdPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Inventory hold was not found."
                                )
                        );


        Booking booking =
                bookingRepository
                        .findByInventoryHoldPublicId(
                                holdPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Booking for inventory hold was not found."
                                )
                        );


        if (!expectedBookingPublicId.equals(
                booking.getPublicId()
        )) {

            throw new IllegalStateException(
                    "Payment booking does not match inventory hold booking."
            );
        }


        if (booking.getInventoryHold() == null
                || !holdPublicId.equals(
                        booking
                                .getInventoryHold()
                                .getPublicId()
                )) {

            throw new IllegalStateException(
                    "Booking inventory hold relationship is invalid."
            );
        }


        /*
        * A concurrent/duplicate payment webhook may observe the
        * lifecycle after another transaction already completed it.
        *
        * Returning the locked context is safe here. The payment
        * processor will subsequently lock PaymentAttempt and verify
        * that the completed payment/order/payment-id relationships
        * are exactly the expected ones.
        */
        if (booking.getStatus()
                == BookingStatus.CONFIRMED) {

            if (hold.getStatus()
                    != InventoryHoldStatus.CONSUMED) {

                throw new IllegalStateException(
                        "Confirmed booking does not have a consumed inventory hold."
                );
            }

            return new LockedConfirmationContext(
                    hold,
                    booking
            );
        }


        if (booking.getStatus()
                != BookingStatus.PENDING_PAYMENT) {

            throw new IllegalStateException(
                    "Booking is not awaiting payment."
            );
        }


        if (!hold.isActiveAt(now)) {

            throw new HoldUnavailableForConfirmationException(
                    "Inventory hold is no longer active."
            );
        }


        if (booking.getUser() == null
                || hold.getUser() == null
                || booking.getUser().getId() == null
                || hold.getUser().getId() == null
                || !booking
                        .getUser()
                        .getId()
                        .equals(
                                hold.getUser().getId()
                        )) {

            throw new IllegalStateException(
                    "Booking and inventory hold owners do not match."
            );
        }


        return new LockedConfirmationContext(
                hold,
                booking
        );
    }


    /**
     * Performs the package-private domain transitions after
     * provider/payment validation has completed.
     *
     * This method deliberately does not acquire another lock.
     * The caller must still be inside the same transaction that
     * obtained the hold lock through lockForPaymentConfirmation().
     */
    public void confirmLocked(
            LockedConfirmationContext context,
            Instant now
    ) {
        Objects.requireNonNull(
                context,
                "context is required"
        );

        Objects.requireNonNull(
                now,
                "now is required"
        );


        InventoryHold hold =
                context.hold();

        Booking booking =
                context.booking();


        /*
         * Revalidate immediately before mutation.
         */
        if (!hold.isActiveAt(now)) {

            throw new HoldUnavailableForConfirmationException(
                    "Inventory hold expired before confirmation."
            );
        }


        if (booking.getStatus()
                != BookingStatus.PENDING_PAYMENT) {

            throw new IllegalStateException(
                    "Booking is no longer awaiting payment."
            );
        }


        /*
         * Both transitions participate in the caller's transaction.
         *
         * If either transition fails, the outer payment transaction
         * must roll back all payment/booking/hold changes.
         */
        booking.markConfirmed(
                now
        );

        hold.markConsumed(
                now
        );
    }


    public record LockedConfirmationContext(
            InventoryHold hold,
            Booking booking
    ) {

        public LockedConfirmationContext {
            Objects.requireNonNull(
                    hold,
                    "hold is required"
            );

            Objects.requireNonNull(
                    booking,
                    "booking is required"
            );
        }
    }


    public static class
            HoldUnavailableForConfirmationException
            extends RuntimeException {

        public HoldUnavailableForConfirmationException(
                String message
        ) {
            super(message);
        }
    }
}