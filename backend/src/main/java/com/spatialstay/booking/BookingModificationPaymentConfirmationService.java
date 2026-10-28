package com.spatialstay.booking;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class BookingModificationPaymentConfirmationService {

    private final BookingModificationRepository
            modificationRepository;

    private final InventoryHoldRepository
            inventoryHoldRepository;

    private final BookingRepository
            bookingRepository;

    public BookingModificationPaymentConfirmationService(
            BookingModificationRepository modificationRepository,
            InventoryHoldRepository inventoryHoldRepository,
            BookingRepository bookingRepository
    ) {
        this.modificationRepository =
                modificationRepository;

        this.inventoryHoldRepository =
                inventoryHoldRepository;

        this.bookingRepository =
                bookingRepository;
    }

    public LockedModificationContext lockForPaymentConfirmation(
            UUID modificationPublicId,
            Instant now
    ) {
        Objects.requireNonNull(
                modificationPublicId,
                "modificationPublicId is required"
        );

        Objects.requireNonNull(
                now,
                "now is required"
        );

        BookingModification modification =
                modificationRepository
                        .findByPublicIdForUpdate(
                                modificationPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Booking modification was not found."
                                )
                        );

        boolean alreadyCompleted =
                modification.getStatus()
                        == BookingModificationStatus.COMPLETED;

        if (!alreadyCompleted
                && modification.getStatus()
                != BookingModificationStatus.AWAITING_PAYMENT) {

            throw new IllegalStateException(
                    "Booking modification is not awaiting additional payment."
            );
        }

        InventoryHold replacementHold =
                inventoryHoldRepository
                        .findByPublicIdForUpdate(
                                modification
                                        .getReplacementHold()
                                        .getPublicId()
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Replacement inventory hold was not found."
                                )
                        );

        Booking booking =
                bookingRepository
                        .findOwnedByPublicIdForUpdate(
                                modification
                                        .getBooking()
                                        .getPublicId(),
                                modification
                                        .getUser()
                                        .getPublicId()
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Original booking was not found."
                                )
                        );

        validateRelationships(
                modification,
                replacementHold,
                booking
        );

        if (alreadyCompleted) {
            if (replacementHold.getStatus()
                    != InventoryHoldStatus.CONSUMED) {

                throw new IllegalStateException(
                        "Completed modification does not have a consumed replacement hold."
                );
            }

            return new LockedModificationContext(
                    modification,
                    replacementHold,
                    booking
            );
        }

        if (modification.getFinancialType()
                != ModificationFinancialType.ADDITIONAL_PAYMENT) {

            throw new IllegalStateException(
                    "Modification does not require additional payment."
            );
        }

        if (booking.getStatus()
                != BookingStatus.CONFIRMED) {

            throw new IllegalStateException(
                    "Original booking is no longer confirmed."
            );
        }

        if (!replacementHold.isActiveAt(now)) {
            throw new ReplacementHoldUnavailableException(
                    "Replacement inventory hold is no longer active."
            );
        }

        validateOriginalSnapshot(
                modification,
                booking
        );

        return new LockedModificationContext(
                modification,
                replacementHold,
                booking
        );
    }

    public void confirmLocked(
            LockedModificationContext context,
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

        BookingModification modification =
                context.modification();

        InventoryHold replacementHold =
                context.replacementHold();

        Booking booking =
                context.booking();

        /*
         * Idempotent duplicate.
         */
        if (modification.getStatus()
                == BookingModificationStatus.COMPLETED) {

            if (replacementHold.getStatus()
                    != InventoryHoldStatus.CONSUMED) {

                throw new IllegalStateException(
                        "Completed modification does not have consumed replacement inventory."
                );
            }

            return;
        }

        if (modification.getFinancialType()
                != ModificationFinancialType.ADDITIONAL_PAYMENT) {

            throw new IllegalStateException(
                    "Modification is not an additional-payment modification."
            );
        }

        if (modification.getStatus()
                != BookingModificationStatus.AWAITING_PAYMENT) {

            throw new IllegalStateException(
                    "Modification is no longer awaiting payment."
            );
        }

        if (booking.getStatus()
                != BookingStatus.CONFIRMED) {

            throw new IllegalStateException(
                    "Original booking is no longer confirmed."
            );
        }

        if (!replacementHold.isActiveAt(now)) {
            throw new ReplacementHoldUnavailableException(
                    "Replacement inventory hold expired before modification confirmation."
            );
        }

        validateRelationships(
                modification,
                replacementHold,
                booking
        );

        validateOriginalSnapshot(
                modification,
                booking
        );

        modification.markReadyToApply(
                now
        );

        booking.applyModification(
                modification.getProposedRoom(),
                modification.getProposedCheckIn(),
                modification.getProposedCheckOut(),
                modification.getProposedGuests(),
                modification.getProposedUnits(),
                modification.getProposedPricePerNight(),
                modification.getProposedTotal(),
                modification.getCurrency()
        );

        replacementHold.markConsumed(
                now
        );

        modification.markCompleted(
                now
        );
    }

    private void validateRelationships(
            BookingModification modification,
            InventoryHold hold,
            Booking booking
    ) {
        if (modification.getBooking() == null
                || modification
                        .getBooking()
                        .getPublicId() == null
                || booking.getPublicId() == null
                || !modification
                        .getBooking()
                        .getPublicId()
                        .equals(
                                booking.getPublicId()
                        )) {

            throw new IllegalStateException(
                    "Modification booking relationship is invalid."
            );
        }

        if (modification.getReplacementHold() == null
                || modification
                        .getReplacementHold()
                        .getPublicId() == null
                || hold.getPublicId() == null
                || !modification
                        .getReplacementHold()
                        .getPublicId()
                        .equals(
                                hold.getPublicId()
                        )) {

            throw new IllegalStateException(
                    "Modification replacement-hold relationship is invalid."
            );
        }

        if (hold.getRoom() == null
                || modification.getProposedRoom() == null
                || hold.getRoom().getId() == null
                || modification
                        .getProposedRoom()
                        .getId() == null
                || !hold
                        .getRoom()
                        .getId()
                        .equals(
                                modification
                                        .getProposedRoom()
                                        .getId()
                        )) {

            throw new IllegalStateException(
                    "Replacement hold room does not match modification."
            );
        }

        if (!hold.getCheckIn()
                .equals(
                        modification.getProposedCheckIn()
                )
                || !hold.getCheckOut()
                .equals(
                        modification.getProposedCheckOut()
                )
                || !hold.getGuests()
                .equals(
                        modification.getProposedGuests()
                )
                || !hold.getUnits()
                .equals(
                        modification.getProposedUnits()
                )
                || hold.getPricePerNight()
                .compareTo(
                        modification
                                .getProposedPricePerNight()
                ) != 0
                || hold.getTotalAmount()
                .compareTo(
                        modification.getProposedTotal()
                ) != 0
                || hold.getCurrency() == null
                || modification.getCurrency() == null
                || !hold
                        .getCurrency()
                        .equalsIgnoreCase(
                                modification.getCurrency()
                        )) {

            throw new IllegalStateException(
                    "Replacement hold does not match modification snapshot."
            );
        }
    }

    private void validateOriginalSnapshot(
            BookingModification modification,
            Booking booking
    ) {
        if (booking.getRoom() == null
                || modification.getOriginalRoom() == null
                || booking.getRoom().getId() == null
                || modification
                        .getOriginalRoom()
                        .getId() == null
                || !booking
                        .getRoom()
                        .getId()
                        .equals(
                                modification
                                        .getOriginalRoom()
                                        .getId()
                        )
                || !booking.getCheckIn()
                        .equals(
                                modification
                                        .getOriginalCheckIn()
                        )
                || !booking.getCheckOut()
                        .equals(
                                modification
                                        .getOriginalCheckOut()
                        )
                || !booking.getGuestCount()
                        .equals(
                                modification
                                        .getOriginalGuests()
                        )
                || !booking.getRoomQuantity()
                        .equals(
                                modification
                                        .getOriginalUnits()
                        )
                || booking.getTotalAmount()
                        .compareTo(
                                modification
                                        .getOriginalTotal()
                        ) != 0) {

            throw new IllegalStateException(
                    "Original booking changed after the modification proposal was created."
            );
        }
    }

    public record LockedModificationContext(
            BookingModification modification,
            InventoryHold replacementHold,
            Booking booking
    ) {
        public LockedModificationContext {
            Objects.requireNonNull(
                    modification,
                    "modification is required"
            );

            Objects.requireNonNull(
                    replacementHold,
                    "replacementHold is required"
            );

            Objects.requireNonNull(
                    booking,
                    "booking is required"
            );
        }
    }

    public static class ReplacementHoldUnavailableException
            extends RuntimeException {

        public ReplacementHoldUnavailableException(
                String message
        ) {
            super(message);
        }
    }
}