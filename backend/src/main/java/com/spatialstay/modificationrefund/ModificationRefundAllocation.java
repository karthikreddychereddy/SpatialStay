package com.spatialstay.modificationrefund;

import com.spatialstay.payment.PaymentAttempt;
import com.spatialstay.payment.PaymentProvider;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "booking_modification_refund_allocations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_mod_refund_allocation_public_id",
                        columnNames = "public_id"
                ),
                @UniqueConstraint(
                        name = "uk_mod_refund_batch_payment",
                        columnNames = {
                                "batch_id",
                                "payment_attempt_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_mod_refund_allocation_batch",
                        columnList = "batch_id"
                ),
                @Index(
                        name = "idx_mod_refund_allocation_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_mod_refund_allocation_provider_refund",
                        columnList = "provider,provider_refund_id"
                )
        }
)
public class ModificationRefundAllocation {

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
            name = "batch_id",
            nullable = false,
            updatable = false
    )
    private ModificationRefundBatch batch;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "payment_attempt_id",
            nullable = false,
            updatable = false
    )
    private PaymentAttempt paymentAttempt;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "provider",
            nullable = false,
            updatable = false,
            length = 30
    )
    private PaymentProvider provider;

    @Column(
            name = "provider_payment_id",
            nullable = false,
            updatable = false,
            length = 120
    )
    private String providerPaymentId;

    @Column(
            name = "amount",
            nullable = false,
            updatable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal amount;

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
    private ModificationRefundAllocationStatus status;

    @Column(
            name = "provider_refund_id",
            length = 120
    )
    private String providerRefundId;

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

    @Column(name = "processing_at")
    private Instant processingAt;

    @Column(name = "succeeded_at")
    private Instant succeededAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "reconciliation_at")
    private Instant reconciliationAt;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private Long version;

    protected ModificationRefundAllocation() {
        // JPA
    }

    private ModificationRefundAllocation(
            ModificationRefundBatch batch,
            PaymentAttempt paymentAttempt,
            BigDecimal amount,
            String currency,
            Instant now
    ) {
        this.publicId = UUID.randomUUID();

        this.batch =
                Objects.requireNonNull(
                        batch,
                        "batch is required"
                );

        this.paymentAttempt =
                Objects.requireNonNull(
                        paymentAttempt,
                        "paymentAttempt is required"
                );

        this.provider =
                Objects.requireNonNull(
                        paymentAttempt.getProvider(),
                        "payment provider is required"
                );

        this.providerPaymentId =
                requireText(
                        paymentAttempt.getProviderPaymentId(),
                        "providerPaymentId"
                );

        this.amount =
                positiveMoney(
                        amount
                );

        this.currency =
                normalizeCurrency(
                        currency
                );

        if (paymentAttempt.getCurrency() == null
                || !this.currency.equalsIgnoreCase(
                        paymentAttempt.getCurrency()
                )) {

            throw new IllegalArgumentException(
                    "Refund allocation currency does not match payment."
            );
        }

        if (paymentAttempt.getExpectedAmount() == null
                || this.amount.compareTo(
                        paymentAttempt.getExpectedAmount()
                ) > 0) {

            throw new IllegalArgumentException(
                    "Refund allocation exceeds captured payment amount."
            );
        }

        this.status =
                ModificationRefundAllocationStatus.PENDING;

        this.createdAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        this.updatedAt = now;
    }

    public static ModificationRefundAllocation create(
            ModificationRefundBatch batch,
            PaymentAttempt paymentAttempt,
            BigDecimal amount,
            String currency,
            Instant now
    ) {
        return new ModificationRefundAllocation(
                batch,
                paymentAttempt,
                amount,
                currency,
                now
        );
    }

    void markProcessing(
            String providerRefundId,
            Instant now
    ) {
        if (status
                == ModificationRefundAllocationStatus.SUCCEEDED) {
            return;
        }

        if (status
                != ModificationRefundAllocationStatus.PENDING
                && status
                != ModificationRefundAllocationStatus.PROCESSING
                && status
                != ModificationRefundAllocationStatus
                        .REQUIRES_RECONCILIATION) {

            throw new IllegalStateException(
                    "Refund allocation cannot enter PROCESSING from "
                            + status
            );
        }

        if (providerRefundId != null
                && !providerRefundId.isBlank()) {

            if (this.providerRefundId != null
                    && !this.providerRefundId.equals(
                            providerRefundId.trim()
                    )) {

                throw new IllegalStateException(
                        "Provider refund ID cannot change."
                );
            }

            this.providerRefundId =
                    providerRefundId.trim();
        }

        status =
                ModificationRefundAllocationStatus.PROCESSING;

        processingAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        updatedAt = processingAt;
        failureReason = null;
        reconciliationAt = null;
    }

    void markSucceeded(
            String providerRefundId,
            Instant now
    ) {
        if (status
                == ModificationRefundAllocationStatus.SUCCEEDED) {
            return;
        }

        String normalizedId =
                requireText(
                        providerRefundId,
                        "providerRefundId"
                );

        if (this.providerRefundId != null
                && !this.providerRefundId.equals(
                        normalizedId
                )) {

            throw new IllegalStateException(
                    "Provider refund ID cannot change."
            );
        }

        this.providerRefundId = normalizedId;

        status =
                ModificationRefundAllocationStatus.SUCCEEDED;

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
        if (status
                == ModificationRefundAllocationStatus.SUCCEEDED) {

            throw new IllegalStateException(
                    "Successful refund allocation cannot fail."
            );
        }

        if (status
                == ModificationRefundAllocationStatus
                        .REQUIRES_RECONCILIATION) {

            throw new IllegalStateException(
                    "Allocation requiring reconciliation cannot be marked failed."
            );
        }

        status =
                ModificationRefundAllocationStatus.FAILED;

        failureReason =
                normalizeReason(
                        reason
                );

        failedAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        updatedAt = failedAt;
    }

    void markRequiresReconciliation(
            String reason,
            Instant now
    ) {
        if (status
                == ModificationRefundAllocationStatus.SUCCEEDED) {
            return;
        }

        status =
                ModificationRefundAllocationStatus
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
                    ModificationRefundAllocationStatus.PENDING;
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
                    "Refund allocation amount must be positive."
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

    private static String requireText(
            String value,
            String field
    ) {
        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    field + " is required."
            );
        }

        return value.trim();
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

    public ModificationRefundBatch getBatch() {
        return batch;
    }

    public PaymentAttempt getPaymentAttempt() {
        return paymentAttempt;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public ModificationRefundAllocationStatus getStatus() {
        return status;
    }

    public String getProviderRefundId() {
        return providerRefundId;
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

    public Instant getProcessingAt() {
        return processingAt;
    }

    public Instant getSucceededAt() {
        return succeededAt;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public Instant getReconciliationAt() {
        return reconciliationAt;
    }

    public Long getVersion() {
        return version;
    }
}