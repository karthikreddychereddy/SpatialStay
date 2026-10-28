package com.spatialstay.payment;

import com.spatialstay.booking.Booking;
import com.spatialstay.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import com.spatialstay.booking.BookingModification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;


@Entity
@Table(
        name = "payment_attempts",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_attempt_public_id",
                        columnNames = "public_id"
                ),
                @UniqueConstraint(
                        name = "uk_payment_attempt_provider_order",
                        columnNames = {
                                "provider",
                                "provider_order_id"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_payment_attempt_booking",
                        columnList = "booking_id"
                ),
                @Index(
                        name = "idx_payment_attempt_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_payment_attempt_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_payment_attempt_provider_payment",
                        columnList = "provider_payment_id"
                )
        }
)
public class PaymentAttempt {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @Column(
            name = "public_id",
            nullable = false,
            updatable = false
    )
    private UUID publicId;

    /*
     * Keep the authenticated customer directly attached to the
     * payment attempt.
     *
     * Ownership must never depend on a client-supplied user ID.
     */
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
     * A booking may have multiple payment attempts.
     *
     * Example:
     *
     * Booking
     *   ├── attempt #1 FAILED
     *   └── attempt #2 SUCCEEDED
     */
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

    @Enumerated(EnumType.STRING)
    @Column(
            name = "provider",
            nullable = false,
            updatable = false,
            length = 30
    )
    private PaymentProvider provider;

    /*
     * Provider order/session identifier.
     *
     * For Razorpay this will later contain the Razorpay order ID.
     *
     * It is null until the provider order has actually been created.
     */
    @Column(
            name = "provider_order_id",
            length = 120
    )
    private String providerOrderId;

    /*
     * Provider payment identifier.
     *
     * This must not be treated as proof of successful payment by
     * itself. Step 10 webhook verification establishes success.
     */
    @Column(
            name = "provider_payment_id",
            length = 120
    )
    private String providerPaymentId;

    /*
     * Amount expected by SpatialStay.
     *
     * This value comes from the authoritative booking/hold data,
     * never from the frontend.
     */
    @Column(
            name = "expected_amount",
            nullable = false,
            updatable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal expectedAmount;

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
            length = 30
    )
    private PaymentStatus status;

    /*
     * Checkout expiration is separate from the inventory hold
     * expiration.
     *
     * The hold remains authoritative for whether the booking can
     * still be confirmed.
     */
    @Column(
            name = "expires_at"
    )
    private Instant expiresAt;

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

    @Column(
            name = "succeeded_at"
    )
    private Instant succeededAt;

    @Column(
            name = "failed_at"
    )
    private Instant failedAt;

    /*
     * Optimistic versioning protects ordinary concurrent updates.
     *
     * Critical booking confirmation will additionally use the
     * InventoryHold pessimistic lock established in Step 9.
     */
    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private Long version;

        @Enumerated(EnumType.STRING)
        @Column(
                name = "purpose",
                nullable = false,
                length = 40
        )
        private PaymentPurpose purpose =
                PaymentPurpose.ORIGINAL_BOOKING;

        /*
        * Present only for a modification additional-payment attempt.
        *
        * Original booking payments keep this null.
        */
        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(
                name = "booking_modification_id"
        )
        private BookingModification bookingModification;


    protected PaymentAttempt() {
        // Required by JPA.
    }


    private PaymentAttempt(
            User user,
            Booking booking,
            PaymentProvider provider,
            BigDecimal expectedAmount,
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
        
        this.purpose =
                PaymentPurpose.ORIGINAL_BOOKING;

        this.bookingModification =
                null;

        this.provider =
                Objects.requireNonNull(
                        provider,
                        "provider is required"
                );

        this.expectedAmount =
                requirePositiveAmount(
                        expectedAmount
                );

        this.currency =
                normalizeCurrency(
                        currency
                );

        this.status =
                PaymentStatus.CREATED;

        this.createdAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        this.updatedAt =
                now;
    }


    public static PaymentAttempt create(
            User user,
            Booking booking,
            PaymentProvider provider,
            BigDecimal expectedAmount,
            String currency,
            Instant now
    ) {

        return new PaymentAttempt(
                user,
                booking,
                provider,
                expectedAmount,
                currency,
                now
        );
    }

        public static PaymentAttempt createModificationAdditionalPayment(
                User user,
                Booking booking,
                BookingModification bookingModification,
                PaymentProvider provider,
                BigDecimal expectedAmount,
                String currency,
                Instant now
        ) {
        if (bookingModification == null) {
                throw new IllegalArgumentException(
                        "Booking modification is required."
                );
        }

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
                        "Payment booking does not match the modification booking."
                );
        }

        if (bookingModification.getFinancialType()
                != com.spatialstay.booking.ModificationFinancialType
                .ADDITIONAL_PAYMENT) {

                throw new IllegalArgumentException(
                        "Modification does not require additional payment."
                );
        }

        if (expectedAmount == null
                || expectedAmount.signum() <= 0
                || bookingModification
                        .getPriceDifference()
                        .compareTo(
                                expectedAmount
                        ) != 0) {

                throw new IllegalArgumentException(
                        "Additional payment amount does not match the modification."
                );
        }

        PaymentAttempt paymentAttempt =
                new PaymentAttempt(
                        user,
                        booking,
                        provider,
                        expectedAmount,
                        currency,
                        now
                );

        paymentAttempt.purpose =
                PaymentPurpose.MODIFICATION_ADDITIONAL;

        paymentAttempt.bookingModification =
                bookingModification;

        return paymentAttempt;
        }


    /*
     * Called only after a provider order/session has actually
     * been created successfully.
     */
    void markPending(
            String providerOrderId,
            Instant expiresAt,
            Instant now
    ) {

        if (status != PaymentStatus.CREATED) {
            throw new IllegalStateException(
                    "Payment attempt is not in CREATED state"
            );
        }

        if (providerOrderId == null
                || providerOrderId.isBlank()) {

            throw new IllegalArgumentException(
                    "Provider order ID is required"
            );
        }

        Objects.requireNonNull(
                now,
                "now is required"
        );

        if (expiresAt != null
                && !expiresAt.isAfter(now)) {

            throw new IllegalArgumentException(
                    "Payment expiration must be in the future"
            );
        }

        this.providerOrderId =
                providerOrderId.trim();

        this.expiresAt =
                expiresAt;

        this.status =
                PaymentStatus.PENDING;

        this.updatedAt =
                now;
    }


    void markProcessing(
            String providerPaymentId,
            Instant now
    ) {

        if (status != PaymentStatus.PENDING
                && status != PaymentStatus.PROCESSING) {

            throw new IllegalStateException(
                    "Payment attempt cannot enter PROCESSING state"
            );
        }

        if (providerPaymentId == null
                || providerPaymentId.isBlank()) {

            throw new IllegalArgumentException(
                    "Provider payment ID is required"
            );
        }

        this.providerPaymentId =
                providerPaymentId.trim();

        this.status =
                PaymentStatus.PROCESSING;

        this.updatedAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );
    }


    /*
     * This method must only be called by trusted backend payment
     * processing after provider verification.
     *
     * A frontend redirect/callback must never call this directly.
     */
    void markSucceeded(
            String providerPaymentId,
            Instant now
    ) {

        if (status == PaymentStatus.SUCCEEDED) {

            /*
             * Idempotent lifecycle handling.
             */
            return;
        }

        if (status != PaymentStatus.PENDING
                && status != PaymentStatus.PROCESSING) {

            throw new IllegalStateException(
                    "Payment attempt cannot be marked successful"
            );
        }

        if (providerPaymentId == null
                || providerPaymentId.isBlank()) {

            throw new IllegalArgumentException(
                    "Provider payment ID is required"
            );
        }

        Instant successTime =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        this.providerPaymentId =
                providerPaymentId.trim();

        this.status =
                PaymentStatus.SUCCEEDED;

        this.succeededAt =
                successTime;

        this.updatedAt =
                successTime;
    }


    void markFailed(
            String providerPaymentId,
            Instant now
    ) {

        if (status == PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException(
                    "Successful payment cannot be marked failed"
            );
        }

        if (status == PaymentStatus.FAILED) {
            return;
        }

        if (status == PaymentStatus.EXPIRED
                || status == PaymentStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Terminal payment attempt cannot be marked failed"
            );
        }

        Instant failureTime =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        if (providerPaymentId != null
                && !providerPaymentId.isBlank()) {

            this.providerPaymentId =
                    providerPaymentId.trim();
        }

        this.status =
                PaymentStatus.FAILED;

        this.failedAt =
                failureTime;

        this.updatedAt =
                failureTime;
    }


    void markExpired(
            Instant now
    ) {

        if (status == PaymentStatus.EXPIRED) {
            return;
        }

        if (status == PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException(
                    "Successful payment cannot expire"
            );
        }

        if (status == PaymentStatus.CANCELLED) {
            return;
        }

        this.status =
                PaymentStatus.EXPIRED;

        this.updatedAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );
    }


    void markCancelled(
            Instant now
    ) {

        if (status == PaymentStatus.CANCELLED) {
            return;
        }

        if (status == PaymentStatus.SUCCEEDED) {
            throw new IllegalStateException(
                    "Successful payment cannot be cancelled"
            );
        }

        if (status == PaymentStatus.EXPIRED) {
            return;
        }

        this.status =
                PaymentStatus.CANCELLED;

        this.updatedAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );
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
                    PaymentStatus.CREATED;
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


    private static BigDecimal requirePositiveAmount(
            BigDecimal amount
    ) {

        if (amount == null
                || amount.signum() <= 0) {

            throw new IllegalArgumentException(
                    "Expected payment amount must be positive"
            );
        }

        return amount;
    }


    private static String normalizeCurrency(
            String currency
    ) {

        if (currency == null
                || currency.isBlank()) {

            throw new IllegalArgumentException(
                    "Currency is required"
            );
        }

        String normalized =
                currency
                        .trim()
                        .toUpperCase();

        if (normalized.length() != 3) {
            throw new IllegalArgumentException(
                    "Currency must be a 3-letter code"
            );
        }

        return normalized;
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


    public PaymentProvider getProvider() {
        return provider;
    }


    public String getProviderOrderId() {
        return providerOrderId;
    }


    public String getProviderPaymentId() {
        return providerPaymentId;
    }


    public BigDecimal getExpectedAmount() {
        return expectedAmount;
    }


    public String getCurrency() {
        return currency;
    }


    public PaymentStatus getStatus() {
        return status;
    }


    public Instant getExpiresAt() {
        return expiresAt;
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


    public Instant getFailedAt() {
        return failedAt;
    }


    public Long getVersion() {
        return version;
    }

        public PaymentPurpose getPurpose() {
                return purpose;
        }

        public BookingModification getBookingModification() {
                return bookingModification;
        }
}