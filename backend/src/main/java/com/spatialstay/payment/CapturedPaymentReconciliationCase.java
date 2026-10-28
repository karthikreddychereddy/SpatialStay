package com.spatialstay.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

/**
 * Audit-only record for captured-payment reconciliation.
 *
 * This entity never authorizes booking confirmation or refunds.
 */
@Entity
@Table(
        name = "captured_payment_reconciliation_cases",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_capture_case_attempt",
                        columnNames = "payment_attempt_public_id"
                ),
                @UniqueConstraint(
                        name = "uk_capture_case_provider_payment",
                        columnNames = {
                                "provider",
                                "provider_payment_id"
                        }
                )
        }
)
public class CapturedPaymentReconciliationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "payment_attempt_public_id",
            nullable = false,
            updatable = false
    )
    private UUID paymentAttemptPublicId;

    @Column(
            name = "booking_public_id",
            nullable = false,
            updatable = false
    )
    private UUID bookingPublicId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "provider",
            nullable = false,
            updatable = false,
            length = 30
    )
    private PaymentProvider provider;

    @Column(
            name = "provider_order_id",
            nullable = false,
            updatable = false
    )
    private String providerOrderId;

    @Column(
            name = "provider_payment_id",
            nullable = false,
            updatable = false
    )
    private String providerPaymentId;

    @Column(
            name = "booking_status_at_discovery",
            nullable = false,
            updatable = false,
            length = 40
    )
    private String bookingStatusAtDiscovery;

    @Column(
            name = "status",
            nullable = false,
            length = 40
    )
    private String status;

    @Column(
            name = "discovered_at",
            nullable = false,
            updatable = false
    )
    private Instant discoveredAt;

    protected CapturedPaymentReconciliationCase() {
    }

    public CapturedPaymentReconciliationCase(
            UUID attemptId,
            UUID bookingId,
            String orderId,
            String paymentId,
            String bookingStatus
    ) {
        this.paymentAttemptPublicId = attemptId;
        this.bookingPublicId = bookingId;
        this.provider = PaymentProvider.RAZORPAY;
        this.providerOrderId = orderId;
        this.providerPaymentId = paymentId;
        this.bookingStatusAtDiscovery = bookingStatus;
        this.status = "REVIEW_REQUIRED";
        this.discoveredAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getPaymentAttemptPublicId() {
        return paymentAttemptPublicId;
    }

    public UUID getBookingPublicId() {
        return bookingPublicId;
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

    public String getBookingStatusAtDiscovery() {
        return bookingStatusAtDiscovery;
    }

    public String getStatus() {
        return status;
    }

    public Instant getDiscoveredAt() {
        return discoveredAt;
    }
}