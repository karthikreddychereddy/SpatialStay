package com.spatialstay.booking;

import com.spatialstay.room.Room;
import com.spatialstay.user.User;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "booking_modifications",
        indexes = {
                @Index(
                        name = "idx_booking_modification_public_id",
                        columnList = "public_id",
                        unique = true
                ),
                @Index(
                        name = "idx_booking_modification_booking",
                        columnList = "booking_id"
                ),
                @Index(
                        name = "idx_booking_modification_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_booking_modification_hold",
                        columnList = "replacement_hold_id"
                )
        }
)
public class BookingModification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "public_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private UUID publicId;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "booking_id",
            nullable = false,
            updatable = false
    )
    private Booking booking;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private User user;

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "replacement_hold_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private InventoryHold replacementHold;

    /*
     * Immutable original snapshot.
     */

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "original_room_id",
            nullable = false,
            updatable = false
    )
    private Room originalRoom;

    @Column(
            name = "original_check_in",
            nullable = false,
            updatable = false
    )
    private LocalDate originalCheckIn;

    @Column(
            name = "original_check_out",
            nullable = false,
            updatable = false
    )
    private LocalDate originalCheckOut;

    @Column(
            name = "original_guests",
            nullable = false,
            updatable = false
    )
    private Integer originalGuests;

    @Column(
            name = "original_units",
            nullable = false,
            updatable = false
    )
    private Integer originalUnits;

    @Column(
            name = "original_total",
            nullable = false,
            precision = 14,
            scale = 2,
            updatable = false
    )
    private BigDecimal originalTotal;

    /*
     * Immutable proposed snapshot.
     */

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "proposed_room_id",
            nullable = false,
            updatable = false
    )
    private Room proposedRoom;

    @Column(
            name = "proposed_check_in",
            nullable = false,
            updatable = false
    )
    private LocalDate proposedCheckIn;

    @Column(
            name = "proposed_check_out",
            nullable = false,
            updatable = false
    )
    private LocalDate proposedCheckOut;

    @Column(
            name = "proposed_guests",
            nullable = false,
            updatable = false
    )
    private Integer proposedGuests;

    @Column(
            name = "proposed_units",
            nullable = false,
            updatable = false
    )
    private Integer proposedUnits;

    @Column(
            name = "proposed_price_per_night",
            nullable = false,
            precision = 12,
            scale = 2,
            updatable = false
    )
    private BigDecimal proposedPricePerNight;

    @Column(
            name = "proposed_total",
            nullable = false,
            precision = 14,
            scale = 2,
            updatable = false
    )
    private BigDecimal proposedTotal;

    /*
     * Signed:
     *
     * > 0 = customer owes additional payment
     * = 0 = no financial adjustment
     * < 0 = customer is owed a partial refund
     */
    @Column(
            name = "price_difference",
            nullable = false,
            precision = 14,
            scale = 2,
            updatable = false
    )
    private BigDecimal priceDifference;

    @Column(
            name = "currency",
            nullable = false,
            length = 3,
            updatable = false
    )
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "financial_type",
            nullable = false,
            length = 40,
            updatable = false
    )
    private ModificationFinancialType financialType;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 40
    )
    private BookingModificationStatus status;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    @Column(name = "reconciliation_at")
    private Instant reconciliationAt;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private Long version;

    protected BookingModification() {
    }

    public BookingModification(
            Booking booking,
            InventoryHold replacementHold,
            Room proposedRoom,
            BigDecimal priceDifference,
            ModificationFinancialType financialType
    ) {
        if (booking == null) {
            throw new IllegalArgumentException(
                    "Booking is required."
            );
        }

        if (booking.getUser() == null) {
            throw new IllegalArgumentException(
                    "Booking user is required."
            );
        }

        if (booking.getRoom() == null) {
            throw new IllegalArgumentException(
                    "Original booking room is required."
            );
        }

        if (replacementHold == null) {
            throw new IllegalArgumentException(
                    "Replacement inventory hold is required."
            );
        }

        if (proposedRoom == null) {
            throw new IllegalArgumentException(
                    "Proposed room is required."
            );
        }

        if (financialType == null) {
            throw new IllegalArgumentException(
                    "Modification financial type is required."
            );
        }

        if (replacementHold.getRoom() == null
                || replacementHold.getRoom().getId() == null
                || proposedRoom.getId() == null
                || !replacementHold
                        .getRoom()
                        .getId()
                        .equals(
                                proposedRoom.getId()
                        )) {

            throw new IllegalArgumentException(
                    "Replacement hold does not belong to the proposed room."
            );
        }

        this.booking =
                booking;

        this.user =
                booking.getUser();

        this.replacementHold =
                replacementHold;

        this.originalRoom =
                booking.getRoom();

        this.originalCheckIn =
                booking.getCheckIn();

        this.originalCheckOut =
                booking.getCheckOut();

        this.originalGuests =
                booking.getGuestCount();

        this.originalUnits =
                booking.getRoomQuantity();

        this.originalTotal =
                money(
                        booking.getTotalAmount()
                );

        this.proposedRoom =
                proposedRoom;

        this.proposedCheckIn =
                replacementHold.getCheckIn();

        this.proposedCheckOut =
                replacementHold.getCheckOut();

        this.proposedGuests =
                replacementHold.getGuests();

        this.proposedUnits =
                replacementHold.getUnits();

        this.proposedPricePerNight =
                money(
                        replacementHold.getPricePerNight()
                );

        this.proposedTotal =
                money(
                        replacementHold.getTotalAmount()
                );

        this.priceDifference =
                signedMoney(
                        priceDifference
                );

        this.currency =
                replacementHold.getCurrency();

        this.financialType =
                financialType;

        if (financialType
                == ModificationFinancialType.ADDITIONAL_PAYMENT) {

            if (this.priceDifference.signum() <= 0) {
                throw new IllegalArgumentException(
                        "Additional-payment modification must have a positive price difference."
                );
            }

            this.status =
                    BookingModificationStatus.AWAITING_PAYMENT;

        } else {

            if (financialType
                    == ModificationFinancialType.NO_ADJUSTMENT
                    && this.priceDifference.signum() != 0) {

                throw new IllegalArgumentException(
                        "No-adjustment modification must have zero price difference."
                );
            }

            if (financialType
                    == ModificationFinancialType.PARTIAL_REFUND
                    && this.priceDifference.signum() >= 0) {

                throw new IllegalArgumentException(
                        "Partial-refund modification must have a negative price difference."
                );
            }

            this.status =
                    BookingModificationStatus.READY_TO_APPLY;
        }
    }

    @PrePersist
    protected void onCreate() {
        if (publicId == null) {
            publicId =
                    UUID.randomUUID();
        }

        if (createdAt == null) {
            createdAt =
                    Instant.now();
        }

        if (status
                == BookingModificationStatus.READY_TO_APPLY
                && readyAt == null) {

            readyAt =
                    createdAt;
        }
    }

    void markReadyToApply(
            Instant at
    ) {
        if (at == null) {
            throw new IllegalArgumentException(
                    "Ready timestamp is required."
            );
        }

        if (status
                != BookingModificationStatus.AWAITING_PAYMENT) {

            throw new IllegalStateException(
                    "Modification is not awaiting payment."
            );
        }

        status =
                BookingModificationStatus.READY_TO_APPLY;

        readyAt =
                at;
    }

    void markCompleted(
            Instant at
    ) {
        if (at == null) {
            throw new IllegalArgumentException(
                    "Completion timestamp is required."
            );
        }

        if (status
                != BookingModificationStatus.READY_TO_APPLY) {

            throw new IllegalStateException(
                    "Modification is not ready to be applied."
            );
        }

        status =
                BookingModificationStatus.COMPLETED;

        completedAt =
                at;
    }

    void markExpired(
            Instant at
    ) {
        if (at == null) {
            throw new IllegalArgumentException(
                    "Expiration timestamp is required."
            );
        }

        if (status
                == BookingModificationStatus.COMPLETED) {

            throw new IllegalStateException(
                    "Completed modification cannot expire."
            );
        }

        if (status
                == BookingModificationStatus.CANCELLED) {

            return;
        }

        status =
                BookingModificationStatus.EXPIRED;

        expiredAt =
                at;
    }

    void markCancelled(
            Instant at
    ) {
        if (at == null) {
            throw new IllegalArgumentException(
                    "Cancellation timestamp is required."
            );
        }

        if (status
                == BookingModificationStatus.COMPLETED) {

            throw new IllegalStateException(
                    "Completed modification cannot be cancelled."
            );
        }

        if (status
                == BookingModificationStatus.CANCELLED) {

            return;
        }

        status =
                BookingModificationStatus.CANCELLED;

        cancelledAt =
                at;
    }

    void markRequiresReconciliation(
            Instant at
    ) {
        if (at == null) {
            throw new IllegalArgumentException(
                    "Reconciliation timestamp is required."
            );
        }

        if (status
                == BookingModificationStatus.COMPLETED) {

            throw new IllegalStateException(
                    "Completed modification cannot require reconciliation."
            );
        }

        status =
                BookingModificationStatus.REQUIRES_RECONCILIATION;

        reconciliationAt =
                at;
    }

    private static BigDecimal money(
            BigDecimal value
    ) {
        if (value == null
                || value.signum() < 0) {

            throw new IllegalArgumentException(
                    "Money value is invalid."
            );
        }

        return value.setScale(
                2,
                RoundingMode.UNNECESSARY
        );
    }

    private static BigDecimal signedMoney(
            BigDecimal value
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Price difference is required."
            );
        }

        return value.setScale(
                2,
                RoundingMode.UNNECESSARY
        );
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public Booking getBooking() {
        return booking;
    }

    public User getUser() {
        return user;
    }

    public InventoryHold getReplacementHold() {
        return replacementHold;
    }

    public Room getOriginalRoom() {
        return originalRoom;
    }

    public LocalDate getOriginalCheckIn() {
        return originalCheckIn;
    }

    public LocalDate getOriginalCheckOut() {
        return originalCheckOut;
    }

    public Integer getOriginalGuests() {
        return originalGuests;
    }

    public Integer getOriginalUnits() {
        return originalUnits;
    }

    public BigDecimal getOriginalTotal() {
        return originalTotal;
    }

    public Room getProposedRoom() {
        return proposedRoom;
    }

    public LocalDate getProposedCheckIn() {
        return proposedCheckIn;
    }

    public LocalDate getProposedCheckOut() {
        return proposedCheckOut;
    }

    public Integer getProposedGuests() {
        return proposedGuests;
    }

    public Integer getProposedUnits() {
        return proposedUnits;
    }

    public BigDecimal getProposedPricePerNight() {
        return proposedPricePerNight;
    }

    public BigDecimal getProposedTotal() {
        return proposedTotal;
    }

    public BigDecimal getPriceDifference() {
        return priceDifference;
    }

    public String getCurrency() {
        return currency;
    }

    public ModificationFinancialType getFinancialType() {
        return financialType;
    }

    public BookingModificationStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadyAt() {
        return readyAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }

    public Instant getReconciliationAt() {
        return reconciliationAt;
    }

    public Long getVersion() {
        return version;
    }
}