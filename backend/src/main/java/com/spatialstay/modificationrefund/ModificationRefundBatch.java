package com.spatialstay.modificationrefund;

import com.spatialstay.booking.Booking;
import com.spatialstay.booking.BookingModification;
import com.spatialstay.user.User;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "booking_modification_refund_batches",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_mod_refund_batch_public_id",
                        columnNames = "public_id"
                ),
                @UniqueConstraint(
                        name = "uk_mod_refund_batch_modification",
                        columnNames = "booking_modification_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_mod_refund_batch_booking",
                        columnList = "booking_id"
                ),
                @Index(
                        name = "idx_mod_refund_batch_status",
                        columnList = "status"
                )
        }
)
public class ModificationRefundBatch {

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
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private User user;

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

    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "booking_modification_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private BookingModification bookingModification;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "reason",
            nullable = false,
            updatable = false,
            length = 40
    )
    private ModificationRefundReason reason;

    @Column(
            name = "total_amount",
            nullable = false,
            updatable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal totalAmount;

    @Column(
            name = "currency",
            nullable = false,
            updatable = false,
            length = 3
    )
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 40
    )
    private ModificationRefundBatchStatus status;

    @Column(
            name = "failure_reason",
            length = 1000
    )
    private String failureReason;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @Column(name = "succeeded_at")
    private Instant succeededAt;

    @Column(name = "reconciliation_at")
    private Instant reconciliationAt;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private Long version;

    protected ModificationRefundBatch() {
        // JPA
    }

    private ModificationRefundBatch(
            User user,
            Booking booking,
            BookingModification bookingModification,
            ModificationRefundReason reason,
            BigDecimal totalAmount,
            String currency,
            Instant now
    ) {
        this.publicId = UUID.randomUUID();

        this.user =
                Objects.requireNonNull(
                        user,
                        "user is required"
                );

        this.booking =
                Objects.requireNonNull(
                        booking,
                        "booking is required"
                );

        this.bookingModification =
                Objects.requireNonNull(
                        bookingModification,
                        "bookingModification is required"
                );

        this.reason =
                Objects.requireNonNull(
                        reason,
                        "reason is required"
                );

        this.totalAmount =
                positiveMoney(
                        totalAmount
                );

        this.currency =
                normalizeCurrency(
                        currency
                );

        this.status =
                ModificationRefundBatchStatus.PENDING;

        this.createdAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        this.updatedAt = now;
    }

    public static ModificationRefundBatch create(
            User user,
            Booking booking,
            BookingModification bookingModification,
            ModificationRefundReason reason,
            BigDecimal totalAmount,
            String currency,
            Instant now
    ) {
        if (bookingModification.getBooking() == null
                || bookingModification
                        .getBooking()
                        .getPublicId() == null
                || booking.getPublicId() == null
                || !bookingModification
                        .getBooking()
                        .getPublicId()
                        .equals(
                                booking.getPublicId()
                        )) {

            throw new IllegalArgumentException(
                    "Refund batch booking does not match modification booking."
            );
        }

        return new ModificationRefundBatch(
                user,
                booking,
                bookingModification,
                reason,
                totalAmount,
                currency,
                now
        );
    }

    void markProcessing(
            Instant now
    ) {
        if (status == ModificationRefundBatchStatus.SUCCEEDED) {
            return;
        }

        if (status != ModificationRefundBatchStatus.PENDING
            && status
            != ModificationRefundBatchStatus.PROCESSING
            && status
            != ModificationRefundBatchStatus.REQUIRES_RECONCILIATION) {

            throw new IllegalStateException(
                    "Refund batch cannot enter PROCESSING from "
                            + status
            );
        }

        status =
                ModificationRefundBatchStatus.PROCESSING;

        updatedAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        failureReason = null;
    }

    void markSucceeded(
            Instant now
    ) {
        if (status == ModificationRefundBatchStatus.SUCCEEDED) {
            return;
        }

        status =
                ModificationRefundBatchStatus.SUCCEEDED;

        succeededAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        updatedAt = succeededAt;
        failureReason = null;
        reconciliationAt = null;
    }

    void markFailed(
            String reason,
            Instant now
    ) {
        if (status == ModificationRefundBatchStatus.SUCCEEDED) {
            throw new IllegalStateException(
                    "Successful refund batch cannot fail."
            );
        }

        status =
                ModificationRefundBatchStatus.FAILED;

        failureReason =
                normalizeReason(
                        reason
                );

        updatedAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );
    }

    void markRequiresReconciliation(
            String reason,
            Instant now
    ) {
        if (status == ModificationRefundBatchStatus.SUCCEEDED) {
            return;
        }

        status =
                ModificationRefundBatchStatus
                        .REQUIRES_RECONCILIATION;

        failureReason =
                normalizeReason(
                        reason
                );

        reconciliationAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        updatedAt = reconciliationAt;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();

        if (publicId == null) {
            publicId = UUID.randomUUID();
        }

        if (status == null) {
            status =
                    ModificationRefundBatchStatus.PENDING;
        }

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    private static BigDecimal positiveMoney(
            BigDecimal amount
    ) {
        if (amount == null
                || amount.signum() <= 0) {

            throw new IllegalArgumentException(
                    "Refund amount must be positive."
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

    private static String normalizeReason(
            String reason
    ) {
        if (reason == null
                || reason.isBlank()) {

            return "Unspecified refund processing failure.";
        }

        String normalized = reason.trim();

        return normalized.length() <= 1000
                ? normalized
                : normalized.substring(0, 1000);
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public User getUser() {
        return user;
    }

    public Booking getBooking() {
        return booking;
    }

    public BookingModification getBookingModification() {
        return bookingModification;
    }

    public ModificationRefundReason getReason() {
        return reason;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public ModificationRefundBatchStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getSucceededAt() {
        return succeededAt;
    }

    public Instant getReconciliationAt() {
        return reconciliationAt;
    }

    public Long getVersion() {
        return version;
    }
}