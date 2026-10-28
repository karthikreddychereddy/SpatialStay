package com.spatialstay.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentWebhookEventRegistrar {

    private final PaymentWebhookEventRepository
            webhookEventRepository;


    public PaymentWebhookEventRegistrar(
            PaymentWebhookEventRepository webhookEventRepository
    ) {
        this.webhookEventRepository =
                webhookEventRepository;
    }


    /*
     * Use an independent transaction for the INSERT.
     *
     * If two identical Razorpay webhook deliveries race,
     * the database unique constraint:
     *
     *     provider + payload_fingerprint
     *
     * allows only one INSERT to succeed.
     *
     * A duplicate-key failure rolls back only this small
     * transaction and does not poison the caller transaction.
     */
    @Transactional(
            propagation = Propagation.REQUIRES_NEW
    )
    public void insert(
            PaymentWebhookEvent event
    ) {
        webhookEventRepository.saveAndFlush(
                event
        );
    }
}