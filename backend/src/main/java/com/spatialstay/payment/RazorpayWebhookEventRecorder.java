package com.spatialstay.payment;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RazorpayWebhookEventRecorder {

    private static final Pattern EVENT_PATTERN =
            Pattern.compile(
                    "\"event\"\\s*:\\s*\"([^\"]+)\""
            );


    private final PaymentWebhookEventRepository
            webhookEventRepository;

    private final PaymentWebhookEventRegistrar
            webhookEventRegistrar;


    public RazorpayWebhookEventRecorder(
            PaymentWebhookEventRepository webhookEventRepository,
            PaymentWebhookEventRegistrar webhookEventRegistrar
    ) {
        this.webhookEventRepository =
                webhookEventRepository;

        this.webhookEventRegistrar =
                webhookEventRegistrar;
    }


    public RecordResult record(
            byte[] rawBody
    ) {
        if (rawBody == null
                || rawBody.length == 0) {

            throw new IllegalArgumentException(
                    "Webhook body is required."
            );
        }


        String fingerprint =
            WebhookFingerprint.sha256(
                    rawBody
            );


        /*
         * Fast duplicate path.
         *
         * This avoids attempting another INSERT for ordinary
         * sequential Razorpay retries.
         *
         * This check alone is NOT the concurrency guarantee.
         * The database unique constraint remains authoritative.
         */
        if (webhookEventRepository
                .findByProviderAndPayloadFingerprint(
                        PaymentProvider.RAZORPAY,
                        fingerprint
                )
                .isPresent()) {

            return RecordResult.DUPLICATE;
        }


        String rawPayload =
                new String(
                        rawBody,
                        StandardCharsets.UTF_8
                );


        String eventType =
                readEventType(
                        rawPayload
                );


        PaymentWebhookEvent event =
                PaymentWebhookEvent.received(
                        PaymentProvider.RAZORPAY,
                        eventType,
                        fingerprint,
                        rawPayload,
                        Instant.now()
                );


        try {

            /*
             * The actual INSERT runs in REQUIRES_NEW.
             *
             * If another request inserted the same fingerprint
             * after our fast duplicate check, the database unique
             * constraint rejects this INSERT safely.
             */
            webhookEventRegistrar.insert(
                    event
            );

            return RecordResult.RECORDED;

        } catch (DataIntegrityViolationException exception) {

            /*
             * A concurrent identical webhook won the race.
             *
             * The registrar transaction has already rolled back,
             * so this caller remains clean and the event can be
             * treated as a duplicate.
             */
            if (webhookEventRepository
                    .findByProviderAndPayloadFingerprint(
                            PaymentProvider.RAZORPAY,
                            fingerprint
                    )
                    .isPresent()) {

                return RecordResult.DUPLICATE;
            }

            /*
             * Do not hide unrelated database integrity failures.
             */
            throw exception;
        }
    }


    private String readEventType(
            String rawPayload
    ) {
        if (rawPayload == null
                || rawPayload.isBlank()) {

            throw new IllegalArgumentException(
                    "Invalid Razorpay webhook payload."
            );
        }


        Matcher matcher =
                EVENT_PATTERN.matcher(
                        rawPayload
                );


        if (!matcher.find()) {

            throw new IllegalArgumentException(
                    "Razorpay webhook event type is missing."
            );
        }


        String eventType =
                matcher.group(1);


        if (eventType == null
                || eventType.isBlank()) {

            throw new IllegalArgumentException(
                    "Razorpay webhook event type is missing."
            );
        }


        return eventType.trim();
    }


    public enum RecordResult {
        RECORDED,
        DUPLICATE
    }
}