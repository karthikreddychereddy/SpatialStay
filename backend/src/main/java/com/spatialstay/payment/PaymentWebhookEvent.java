package com.spatialstay.payment;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(
        name = "payment_webhook_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_payment_webhook_provider_fingerprint",
                        columnNames = {
                                "provider",
                                "payload_fingerprint"
                        }
                )
        },
        indexes = {
                @Index(
                        name = "idx_payment_webhook_status",
                        columnList = "status"
                ),
                @Index(
                        name = "idx_payment_webhook_event_type",
                        columnList = "event_type"
                ),
                @Index(
                        name = "idx_payment_webhook_received_at",
                        columnList = "received_at"
                )
        }
)
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "provider",
            nullable = false,
            updatable = false,
            length = 30
    )
    private PaymentProvider provider;

    /*
     * Razorpay event name.
     *
     * Examples:
     * payment.captured
     * order.paid
     */
    @Column(
            name = "event_type",
            nullable = false,
            updatable = false,
            length = 100
    )
    private String eventType;

    /*
     * SHA-256 of the exact verified raw webhook body.
     *
     * The database unique constraint on:
     *
     *     provider + payload_fingerprint
     *
     * provides durable duplicate-event protection across
     * application restarts and multiple deliveries.
     */
    @Column(
            name = "payload_fingerprint",
            nullable = false,
            updatable = false,
            length = 64
    )
    private String payloadFingerprint;

    /*
     * Keep the original provider payload for:
     *
     * - Step 10.8 authoritative processing
     * - payment investigation
     * - reconciliation
     *
     * Never trust browser payment data instead.
     */
    @Column(
            name = "raw_payload",
            nullable = false,
            columnDefinition = "TEXT"
    )
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 40
    )
    private PaymentWebhookEventStatus status;

    @Column(
            name = "received_at",
            nullable = false,
            updatable = false
    )
    private Instant receivedAt;

    @Column(
            name = "processed_at"
    )
    private Instant processedAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @Version
    @Column(
            name = "version",
            nullable = false
    )
    private Long version;


    protected PaymentWebhookEvent() {
        // Required by JPA.
    }


    private PaymentWebhookEvent(
            PaymentProvider provider,
            String eventType,
            String payloadFingerprint,
            String rawPayload,
            Instant now
    ) {
        this.provider =
                Objects.requireNonNull(
                        provider,
                        "provider is required"
                );

        this.eventType =
                requireText(
                        eventType,
                        "eventType"
                );

        this.payloadFingerprint =
                requireText(
                        payloadFingerprint,
                        "payloadFingerprint"
                );

        this.rawPayload =
                Objects.requireNonNull(
                        rawPayload,
                        "rawPayload is required"
                );

        this.status =
                PaymentWebhookEventStatus.RECEIVED;

        this.receivedAt =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        this.updatedAt = now;
    }


    public static PaymentWebhookEvent received(
            PaymentProvider provider,
            String eventType,
            String payloadFingerprint,
            String rawPayload,
            Instant now
    ) {
        return new PaymentWebhookEvent(
                provider,
                eventType,
                payloadFingerprint,
                rawPayload,
                now
        );
    }


    public Long getId() {
        return id;
    }

    public PaymentProvider getProvider() {
        return provider;
    }

    public String getEventType() {
        return eventType;
    }

    public String getPayloadFingerprint() {
        return payloadFingerprint;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public PaymentWebhookEventStatus getStatus() {
        return status;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }


    private static String requireText(
            String value,
            String field
    ) {
        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    field + " is required"
            );
        }

        return value.trim();
    }

    void markProcessed(
            Instant now
    ) {
        if (status == PaymentWebhookEventStatus.PROCESSED) {
            return;
        }

        if (status
                == PaymentWebhookEventStatus.REQUIRES_RECONCILIATION) {

            throw new IllegalStateException(
                    "Reconciliation webhook event cannot be marked processed."
            );
        }

        Instant processedTime =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        status =
                PaymentWebhookEventStatus.PROCESSED;

        processedAt =
                processedTime;

        updatedAt =
                processedTime;
    }


    void markRequiresReconciliation(
            Instant now
    ) {
        if (status
                == PaymentWebhookEventStatus.REQUIRES_RECONCILIATION) {

            return;
        }

        if (status
                == PaymentWebhookEventStatus.PROCESSED) {

            throw new IllegalStateException(
                    "Processed webhook event cannot require reconciliation."
            );
        }

        Instant reconciliationTime =
                Objects.requireNonNull(
                        now,
                        "now is required"
                );

        status =
                PaymentWebhookEventStatus.REQUIRES_RECONCILIATION;

        processedAt =
                reconciliationTime;

        updatedAt =
                reconciliationTime;
    }

}