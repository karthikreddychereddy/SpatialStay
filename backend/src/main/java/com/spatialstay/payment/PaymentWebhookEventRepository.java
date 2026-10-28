package com.spatialstay.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentWebhookEventRepository
        extends JpaRepository<PaymentWebhookEvent, Long> {

    Optional<PaymentWebhookEvent>
    findByProviderAndPayloadFingerprint(
            PaymentProvider provider,
            String payloadFingerprint
    );
}