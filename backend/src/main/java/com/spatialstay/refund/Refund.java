package com.spatialstay.refund;

import com.spatialstay.booking.Booking;
import com.spatialstay.payment.PaymentAttempt;
import com.spatialstay.payment.PaymentProvider;
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
        name = "refunds",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_refund_public_id",
                        columnNames = "public_id"
                ),
                @UniqueConstraint(
                        name = "uk_refund_booking",
                        columnNames = "booking_id"
                )
        },
        indexes = {
                @Index(
                        name = "idx_refund_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_refund_payment_attempt",
                        columnList = "payment_attempt_id"
                ),
                @Index(
                        name = "idx_refund_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_refund_provider_refund",
                        columnList = "provider,provider_refund_id"
                )
        }
)
public class Refund {

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

    /*
     * One customer cancellation currently creates at most one refund
     * obligation for the booking.
     *
     * Modification-related adjustment refunds will receive their own
     * model in Step 11.11 rather than abusing this cancellation record.
     */
    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "booking_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private Booking booking;

    /*
     * The exact successful payment from which this refund originates.
     */
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

    /*
     * Snapshot of the verified provider payment identifier.
     *
     * This comes from the successful PaymentAttempt, never the browser.
     */
    @Column(
            name = "provider_payment_id",
            nullable = false,
            updatable = false,
            length = 120
    )
    private String providerPaymentId;

    @Column(
            name = "original_amount",
            nullable = false,
            updatable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal originalAmount;

    @Column(
            name = "cancellation_fee",
            nullable = false,
            updatable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal cancellationFee;

    @Column(
            name = "refund_amount",
            nullable = false,
            updatable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal refundAmount;

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
    private RefundStatus status;

    /*
     * Filled by Step 11.7 after Razorpay accepts/creates the refund.
     */
    @Column(
            name = "provider_refund_id",
            length = 120
    )
    private String providerRefundId;

    /*
     * Safe internal/provider failure description.
     *
     * Do not store secrets or raw credentials here.
     */
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

    protected Refund() {
        // Required by JPA.
    }

    private Refund(
            User user,
            Booking booking,
            PaymentAttempt paymentAttempt,
            PaymentProvider provider,
            String providerPaymentId,
            BigDecimal originalAmount,
            BigDecimal cancellationFee,
            BigDecimal refundAmount,
            String currency,
            Instant now
    ) {
        this.publicId =
                UUID.randomUUID();

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

        this.paymentAttempt =
                Objects.requireNonNull(
                        paymentAttempt,
                        "paymentAttempt is required"
                );

        this.provider =
                Objects.requireNonNull(
                        provider,
                        "provider is required"
                );

        this.providerPaymentId =
                requireText(
                        providerPaymentId,
                        "providerPaymentId"
                );

        this.originalAmount =
                money(
                        originalAmount,
                        "originalAmount"
                );

        this.cancellationFee =
                money(
                        cancellationFee,
                        "cancellationFee"
                );

        this.refundAmount =
                positiveMoney(
                        refundAmount,
                        "refundAmount"
                );

        if (this.cancellationFee
                .add(this.refundAmount)
                .compareTo(this.originalAmount) != 0) {

            throw new IllegalArgumentException(
                    "Cancellation fee plus refund amount must equal original amount."
            );
        }

        this.currency =
                normalizeCurrency(
                        currency
                );

        this.status =
                RefundStatus.PENDING;

        this.createdAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        this.updatedAt =
                now;
    }

    public static Refund pendingCancellationRefund(
            User user,
            Booking booking,
            PaymentAttempt paymentAttempt,
            BigDecimal originalAmount,
            BigDecimal cancellationFee,
            BigDecimal refundAmount,
            String currency,
            Instant now
    ) {
        if (paymentAttempt == null) {
            throw new IllegalArgumentException(
                    "Successful payment attempt is required."
            );
        }

        return new Refund(
                user,
                booking,
                paymentAttempt,
                paymentAttempt.getProvider(),
                paymentAttempt.getProviderPaymentId(),
                originalAmount,
                cancellationFee,
                refundAmount,
                currency,
                now
        );
    }

    /*
     * Step 11.7 will use these package-private lifecycle transitions
     * from services in this refund package.
     */

    void markProcessing(
            String providerRefundId,
            Instant now
    ) {
        if (status == RefundStatus.SUCCEEDED) {
            return;
        }

        /*
        * REQUIRES_RECONCILIATION may legitimately recover to
        * PROCESSING when a later signed Razorpay webhook proves that
        * the provider actually created the refund.
        */
        if (status != RefundStatus.PENDING
                && status != RefundStatus.FAILED
                && status != RefundStatus.PROCESSING
                && status != RefundStatus.REQUIRES_RECONCILIATION) {

            throw new IllegalStateException(
                    "Refund cannot enter PROCESSING state from "
                            + status
                            + "."
            );
        }

        if (providerRefundId != null
                && !providerRefundId.isBlank()) {

            String normalizedProviderRefundId =
                    providerRefundId.trim();

            /*
            * Once a provider refund ID exists, never silently replace
            * it with another provider refund.
            */
            if (this.providerRefundId != null
                    && !this.providerRefundId.isBlank()
                    && !this.providerRefundId.equals(
                            normalizedProviderRefundId
                    )) {

                throw new IllegalStateException(
                        "Refund is already associated with another provider refund."
                );
            }

            this.providerRefundId =
                    normalizedProviderRefundId;
        }

        Instant processingTime =
                requireInstant(now);

        this.status =
                RefundStatus.PROCESSING;

        this.processingAt =
                processingTime;

        this.updatedAt =
                processingTime;

        this.failureReason =
                null;

        this.reconciliationAt =
                null;
    }

    void markSucceeded(
            String providerRefundId,
            Instant now
    ) {
        String normalizedProviderRefundId =
                requireText(
                        providerRefundId,
                        "providerRefundId"
                );

        if (status == RefundStatus.SUCCEEDED) {

            /*
            * Idempotent only when the same provider refund is being
            * acknowledged again.
            */
            if (this.providerRefundId == null
                    || !this.providerRefundId.equals(
                            normalizedProviderRefundId
                    )) {

                throw new IllegalStateException(
                        "Successful refund is associated with another provider refund."
                );
            }

            return;
        }

        /*
        * REQUIRES_RECONCILIATION is intentionally accepted here.
        *
        * Example:
        *
        * SpatialStay sends refund request
        * -> network response is lost
        * -> local refund becomes REQUIRES_RECONCILIATION
        * -> Razorpay later sends authenticated refund.processed
        * -> we can safely recover to SUCCEEDED.
        */
        if (status != RefundStatus.PENDING
                && status != RefundStatus.PROCESSING
                && status != RefundStatus.REQUIRES_RECONCILIATION) {

            throw new IllegalStateException(
                    "Refund cannot be marked successful from "
                            + status
                            + "."
            );
        }

        if (this.providerRefundId != null
                && !this.providerRefundId.isBlank()
                && !this.providerRefundId.equals(
                        normalizedProviderRefundId
                )) {

            throw new IllegalStateException(
                    "Refund is associated with another provider refund."
            );
        }

        Instant successTime =
                requireInstant(now);

        this.providerRefundId =
                normalizedProviderRefundId;

        this.status =
                RefundStatus.SUCCEEDED;

        this.succeededAt =
                successTime;

        this.updatedAt =
                successTime;

        this.failureReason =
                null;

        this.reconciliationAt =
                null;
    }

    void markFailed(
            String reason,
            Instant now
    ) {
        if (status == RefundStatus.SUCCEEDED) {
            throw new IllegalStateException(
                    "Successful refund cannot be marked failed."
            );
        }

        if (status == RefundStatus.REQUIRES_RECONCILIATION) {
            throw new IllegalStateException(
                    "Refund requiring reconciliation cannot be marked failed."
            );
        }

        Instant failureTime =
                requireInstant(now);

        this.status =
                RefundStatus.FAILED;

        this.failureReason =
                normalizeReason(reason);

        this.failedAt =
                failureTime;

        this.updatedAt =
                failureTime;
    }

    void markRequiresReconciliation(
            String reason,
            Instant now
    ) {
        if (status == RefundStatus.SUCCEEDED) {
            return;
        }

        Instant reconciliationTime =
                requireInstant(now);

        this.status =
                RefundStatus.REQUIRES_RECONCILIATION;

        this.failureReason =
                normalizeReason(reason);

        this.reconciliationAt =
                reconciliationTime;

        this.updatedAt =
                reconciliationTime;
    }

    void markAggregateProcessing(Instant now) {
        if (status != RefundStatus.SUCCEEDED) {
            status = RefundStatus.PROCESSING;
            failureReason = null;
            reconciliationAt = null;
            processingAt = requireInstant(now);
            updatedAt = processingAt;
        }
    }

    void markAggregateSucceeded(Instant now) {
        status = RefundStatus.SUCCEEDED;
        failureReason = null;
        reconciliationAt = null;
        succeededAt = requireInstant(now);
        updatedAt = succeededAt;
    }

    void markAggregateFailed(String reason, Instant now) {
        if (status != RefundStatus.SUCCEEDED) {
            status = RefundStatus.FAILED;
            failureReason = normalizeReason(reason);
            failedAt = requireInstant(now);
            updatedAt = failedAt;
        }
    }

    @PrePersist
    void prePersist() {
        Instant now =
                Instant.now();

        if (publicId == null) {
            publicId =
                    UUID.randomUUID();
        }

        if (status == null) {
            status =
                    RefundStatus.PENDING;
        }

        if (createdAt == null) {
            createdAt =
                    now;
        }

        if (updatedAt == null) {
            updatedAt =
                    createdAt;
        }
    }

    private static BigDecimal money(
            BigDecimal amount,
            String field
    ) {
        if (amount == null) {
            throw new IllegalArgumentException(
                    field + " is required."
            );
        }

        if (amount.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative."
            );
        }

        return amount.setScale(
                2,
                RoundingMode.UNNECESSARY
        );
    }

    private static BigDecimal positiveMoney(
            BigDecimal amount,
            String field
    ) {
        BigDecimal normalized =
                money(
                        amount,
                        field
                );

        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(
                    field + " must be greater than zero."
            );
        }

        return normalized;
    }

    private static String normalizeCurrency(
            String currency
    ) {
        if (currency == null
                || !currency.trim().matches("[A-Za-z]{3}")) {

            throw new IllegalArgumentException(
                    "Currency must be a 3-letter code."
            );
        }

        return currency
                .trim()
                .toUpperCase(Locale.ROOT);
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

        String normalized =
                reason.trim();

        return normalized.length() <= 1000
                ? normalized
                : normalized.substring(
                        0,
                        1000
                );
    }

    private static Instant requireInstant(
            Instant value
    ) {
        return Objects.requireNonNull(
                value,
                "timestamp is required"
        );
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

    public PaymentAttempt getPaymentAttempt() {
        return paymentAttempt;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public BigDecimal getOriginalAmount() {
        return originalAmount;
    }

    public BigDecimal getCancellationFee() {
        return cancellationFee;
    }

    public BigDecimal getRefundAmount() {
        return refundAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public RefundStatus getStatus() {
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