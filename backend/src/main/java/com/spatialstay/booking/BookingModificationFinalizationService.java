package com.spatialstay.booking;

import com.spatialstay.user.CurrentUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.spatialstay.modificationrefund.ModificationRefundAllocationService;
import com.spatialstay.modificationrefund.ModificationRefundBatch;
import com.spatialstay.modificationrefund.ModificationRefundBatchRepository;
import com.spatialstay.modificationrefund.ModificationRefundProcessingRequestedEvent;
import com.spatialstay.modificationrefund.ModificationRefundReason;

import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;

import java.time.Instant;
import java.util.UUID;

@Service
public class BookingModificationFinalizationService {

    private final BookingModificationRepository
            modificationRepository;

    private final BookingRepository
            bookingRepository;

    private final InventoryHoldRepository
            inventoryHoldRepository;

    private final CurrentUser currentUser;

    private final ModificationRefundBatchRepository
            modificationRefundBatchRepository;

    private final ModificationRefundAllocationService
            modificationRefundAllocationService;

    private final ApplicationEventPublisher
            applicationEventPublisher;

    public BookingModificationFinalizationService(
            BookingModificationRepository modificationRepository,
            BookingRepository bookingRepository,
            InventoryHoldRepository inventoryHoldRepository,
            CurrentUser currentUser,
            ModificationRefundBatchRepository modificationRefundBatchRepository,
            ModificationRefundAllocationService modificationRefundAllocationService,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.modificationRepository =
                modificationRepository;

        this.bookingRepository =
                bookingRepository;

        this.inventoryHoldRepository =
                inventoryHoldRepository;

        this.currentUser =
                currentUser;

        this.modificationRefundBatchRepository =
                modificationRefundBatchRepository;

        this.modificationRefundAllocationService =
                modificationRefundAllocationService;

        this.applicationEventPublisher =
                applicationEventPublisher;
    }

    /*
     * Customer-facing finalization currently permits only the
     * NO_ADJUSTMENT path.
     *
     * ADDITIONAL_PAYMENT must first be verified by the payment
     * webhook.
     *
     * PARTIAL_REFUND will be finalized through the financial
     * transaction service added in the next 11.11 batch so the
     * durable refund obligation and booking mutation are atomic.
     */
    @Transactional
    public BookingModificationResponse finalizeNoAdjustment(
            UUID modificationPublicId
    ) {
        if (modificationPublicId == null) {
            throw new IllegalArgumentException(
                    "Modification ID is required."
            );
        }

        UUID userPublicId =
                currentUser.publicId();

        Instant now =
                Instant.now();

        /*
        * Lock the modification first.
        *
        * Only one finalization path may operate on the proposal at a
        * time.
        */
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

        /*
        * Customer retry after a committed finalization.
        *
        * No second refund obligation is created.
        */
        if (modification.getStatus()
                == BookingModificationStatus.COMPLETED) {

            return toResponse(
                    modification,
                    "Booking modification is already complete."
            );
        }

        ModificationFinancialType financialType =
                modification.getFinancialType();

        /*
        * ADDITIONAL_PAYMENT is intentionally forbidden here.
        *
        * That path is finalized only by the trusted verified Razorpay
        * payment.captured webhook.
        */
        if (financialType
                == ModificationFinancialType.ADDITIONAL_PAYMENT) {

            throw new IllegalStateException(
                    "This modification requires verified additional payment before it can be completed."
            );
        }

        if (financialType
                != ModificationFinancialType.NO_ADJUSTMENT
                && financialType
                != ModificationFinancialType.PARTIAL_REFUND) {

            throw new IllegalStateException(
                    "Unsupported modification financial type."
            );
        }

        if (modification.getStatus()
                != BookingModificationStatus.READY_TO_APPLY) {

            throw new IllegalStateException(
                    "Modification is not ready to be applied."
            );
        }

        /*
        * Lock the authoritative booking.
        */
        Booking booking =
                bookingRepository
                        .findOwnedByPublicIdForUpdate(
                                modification
                                        .getBooking()
                                        .getPublicId(),
                                userPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Original booking is no longer available."
                                )
                        );

        /*
        * Lock the replacement inventory.
        */
        InventoryHold replacementHold =
                inventoryHoldRepository
                        .findOwnedByPublicIdForUpdate(
                                modification
                                        .getReplacementHold()
                                        .getPublicId(),
                                userPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Replacement inventory hold is no longer available."
                                )
                        );

        /*
        * Revalidate both snapshots while all authoritative rows are
        * locked.
        */
        validateOriginalBooking(
                modification,
                booking
        );

        validateReplacementHold(
                modification,
                replacementHold,
                now
        );

        ModificationRefundBatch refundBatch =
                null;

        if (financialType
                == ModificationFinancialType.PARTIAL_REFUND) {

            /*
            * priceDifference is signed.
            *
            * Example:
            *
            * original = 10,000
            * proposed = 8,000
            * difference = -2,000
            *
            * refund = 2,000
            */
            BigDecimal priceDifference =
                    modification.getPriceDifference();

            if (priceDifference == null
                    || priceDifference.signum() >= 0) {

                throw new IllegalStateException(
                        "Partial-refund modification must have a negative price difference."
                );
            }

            BigDecimal refundAmount =
                    priceDifference
                            .negate();

            if (refundAmount.signum() <= 0) {
                throw new IllegalStateException(
                        "Modification refund amount must be positive."
                );
            }

            /*
            * Defensive idempotency check.
            *
            * The modification row is already pessimistically locked and
            * the DB also has a unique constraint on modification_id.
            */
            if (modificationRefundBatchRepository
                    .findByBookingModificationPublicId(
                            modificationPublicId
                    )
                    .isPresent()) {

                throw new IllegalStateException(
                        "A refund obligation already exists for this modification."
                );
            }

            /*
            * Create the durable aggregate BEFORE changing the booking.
            *
            * If allocation cannot be satisfied, the transaction fails
            * and neither the booking nor inventory is changed.
            */
            refundBatch =
                    ModificationRefundBatch.create(
                            modification.getUser(),
                            booking,
                            modification,
                            ModificationRefundReason.PRICE_DECREASE,
                            refundAmount,
                            modification.getCurrency(),
                            now
                    );

            refundBatch =
                    modificationRefundBatchRepository.saveAndFlush(
                            refundBatch
                    );

            /*
            * Allocate the refund against actual successful captures.
            *
            * Example:
            *
            * ₹3,000 -> latest modification capture
            * ₹2,000 -> original booking capture
            *
            * The browser never chooses provider payment IDs.
            */
            modificationRefundAllocationService
                    .createAllocations(
                            refundBatch,
                            booking,
                            modification,
                            refundAmount,
                            modification.getCurrency(),
                            now
                    );
        }

        /*
        * Every financial invariant has now succeeded.
        *
        * Apply the authoritative reservation snapshot.
        */
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

        bookingRepository.save(
                booking
        );

        inventoryHoldRepository.save(
                replacementHold
        );

        modificationRepository.save(
                modification
        );

        /*
        * Spring publishes the event now, but our processor will use
        * AFTER_COMMIT.
        *
        * Therefore Razorpay will NEVER be called while this database
        * transaction is holding booking/inventory locks.
        */
        if (refundBatch != null) {
            applicationEventPublisher.publishEvent(
                    new ModificationRefundProcessingRequestedEvent(
                            refundBatch.getPublicId()
                    )
            );
        }

        if (financialType
                == ModificationFinancialType.PARTIAL_REFUND) {

            return toResponse(
                    modification,
                    "Booking modification completed successfully. Refund has been queued for secure processing."
            );
        }

        return toResponse(
                modification,
                "Booking modification completed successfully."
        );
    }

    private void validateOwnership(
            BookingModification modification,
            UUID userPublicId
    ) {
        if (modification.getUser() == null
                || modification.getUser().getPublicId() == null
                || !modification
                        .getUser()
                        .getPublicId()
                        .equals(
                                userPublicId
                        )) {

            throw new IllegalArgumentException(
                    "Modification was not found."
            );
        }
    }

    private void validateOriginalBooking(
            BookingModification modification,
            Booking booking
    ) {
        if (booking.getStatus()
                != BookingStatus.CONFIRMED) {

            throw new IllegalStateException(
                    "Original booking is no longer confirmed."
            );
        }

        /*
         * Detect a stale proposal.
         *
         * Another successfully completed modification must never be
         * overwritten by an older proposal.
         */
        if (booking.getRoom() == null
                || modification.getOriginalRoom() == null
                || booking.getRoom().getId() == null
                || modification.getOriginalRoom().getId() == null
                || !booking
                        .getRoom()
                        .getId()
                        .equals(
                                modification
                                        .getOriginalRoom()
                                        .getId()
                        )
                || !booking
                        .getCheckIn()
                        .equals(
                                modification
                                        .getOriginalCheckIn()
                        )
                || !booking
                        .getCheckOut()
                        .equals(
                                modification
                                        .getOriginalCheckOut()
                        )
                || !booking
                        .getGuestCount()
                        .equals(
                                modification
                                        .getOriginalGuests()
                        )
                || !booking
                        .getRoomQuantity()
                        .equals(
                                modification
                                        .getOriginalUnits()
                        )
                || booking
                        .getTotalAmount()
                        .compareTo(
                                modification
                                        .getOriginalTotal()
                        ) != 0) {

            throw new IllegalStateException(
                    "Booking changed after this modification proposal was created."
            );
        }
    }

    private void validateReplacementHold(
            BookingModification modification,
            InventoryHold hold,
            Instant now
    ) {
        if (hold.getStatus()
                != InventoryHoldStatus.ACTIVE) {

            throw new IllegalStateException(
                    "Replacement inventory hold is no longer active."
            );
        }

        if (hold.getExpiresAt() == null
            || !hold.getExpiresAt()
                    .isAfter(
                            now
                    )) {

        modification.markExpired(
                now
        );

        throw new IllegalStateException(
                "Replacement inventory hold has expired. Create a new modification proposal."
        );
    }

        if (hold.getRoom() == null
                || modification.getProposedRoom() == null
                || hold.getRoom().getId() == null
                || modification.getProposedRoom().getId() == null
                || !hold
                        .getRoom()
                        .getId()
                        .equals(
                                modification
                                        .getProposedRoom()
                                        .getId()
                        )
                || !hold
                        .getCheckIn()
                        .equals(
                                modification
                                        .getProposedCheckIn()
                        )
                || !hold
                        .getCheckOut()
                        .equals(
                                modification
                                        .getProposedCheckOut()
                        )
                || !hold
                        .getGuests()
                        .equals(
                                modification
                                        .getProposedGuests()
                        )
                || !hold
                        .getUnits()
                        .equals(
                                modification
                                        .getProposedUnits()
                        )
                || hold
                        .getPricePerNight()
                        .compareTo(
                                modification
                                        .getProposedPricePerNight()
                        ) != 0
                || hold
                        .getTotalAmount()
                        .compareTo(
                                modification
                                        .getProposedTotal()
                        ) != 0
                || hold.getCurrency() == null
                || modification.getCurrency() == null
                || !hold
                        .getCurrency()
                        .equalsIgnoreCase(
                                modification
                                        .getCurrency()
                        )) {

            throw new IllegalStateException(
                    "Replacement inventory hold does not match the modification proposal."
            );
        }
    }

    private BookingModificationResponse toResponse(
            BookingModification modification,
            String message
    ) {
        InventoryHold hold =
                modification.getReplacementHold();

        ModificationFinancialType financialType =
                modification.getFinancialType();

        return new BookingModificationResponse(
                modification.getPublicId(),

                modification
                        .getBooking()
                        .getPublicId(),

                modification
                        .getBooking()
                        .getBookingReference(),

                modification.getStatus(),

                financialType,

                modification
                        .getOriginalRoom()
                        .getSlug(),

                modification.getOriginalCheckIn(),

                modification.getOriginalCheckOut(),

                modification.getOriginalGuests(),

                modification.getOriginalUnits(),

                modification.getOriginalTotal(),

                modification
                        .getProposedRoom()
                        .getSlug(),

                modification.getProposedCheckIn(),

                modification.getProposedCheckOut(),

                modification.getProposedGuests(),

                modification.getProposedUnits(),

                modification.getProposedPricePerNight(),

                modification.getProposedTotal(),

                modification.getPriceDifference(),

                modification.getCurrency(),

                hold.getPublicId(),

                hold.getExpiresAt(),

                financialType
                        == ModificationFinancialType.ADDITIONAL_PAYMENT,

                financialType
                        == ModificationFinancialType.PARTIAL_REFUND,

                message
        );
    }
}