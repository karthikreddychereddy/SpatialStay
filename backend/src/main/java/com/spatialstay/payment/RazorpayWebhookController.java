package com.spatialstay.payment;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments/webhooks")
public class RazorpayWebhookController {

    private final RazorpayWebhookSignatureVerifier
            signatureVerifier;

    private final RazorpayWebhookEventRecorder
            eventRecorder;

    private final RazorpayWebhookProcessor
            webhookProcessor;


    public RazorpayWebhookController(
            RazorpayWebhookSignatureVerifier signatureVerifier,
            RazorpayWebhookEventRecorder eventRecorder,
            RazorpayWebhookProcessor webhookProcessor
    ) {
        this.signatureVerifier =
                signatureVerifier;

        this.eventRecorder =
                eventRecorder;

        this.webhookProcessor =
                webhookProcessor;
    }


    @PostMapping("/razorpay")
    public ResponseEntity<Void> receiveWebhook(
            @RequestBody byte[] rawBody,

            @RequestHeader(
                    value = "X-Razorpay-Signature",
                    required = false
            )
            String signature
    ) {

        /*
         * Fail closed if webhook verification has not been
         * configured.
         */
        if (!signatureVerifier.isConfigured()) {

            return ResponseEntity
                    .status(
                            HttpStatus.SERVICE_UNAVAILABLE
                    )
                    .build();
        }


        /*
         * The HMAC must be checked against the exact raw request
         * bytes before parsing or persisting anything.
         */
        if (!signatureVerifier.isValid(
                rawBody,
                signature
        )) {

            return ResponseEntity
                    .status(
                            HttpStatus.UNAUTHORIZED
                    )
                    .build();
        }


        try {

            /*
             * First create/find the durable RECEIVED event.
             *
             * The recorder uses REQUIRES_NEW for insertion, so the
             * receipt survives if later processing encounters a
             * retryable failure.
             */
            eventRecorder.record(
                    rawBody
            );


            /*
             * The same exact verified body determines the durable
             * idempotency fingerprint.
             */
            String fingerprint =
                    WebhookFingerprint.sha256(
                            rawBody
                    );


            /*
             * Processor starts its own transaction and reloads the
             * durable event by provider + fingerprint inside that
             * transaction.
             */
            webhookProcessor.process(
                    fingerprint
            );


            /*
             * PROCESSED, IGNORED and
             * REQUIRES_RECONCILIATION are all intentional terminal
             * outcomes for this provider delivery.
             */
            return ResponseEntity.ok().build();

        } catch (IllegalArgumentException exception) {

            /*
             * Valid signature, but malformed/unprocessable payload.
             */
            return ResponseEntity
                    .badRequest()
                    .build();

        } catch (Exception exception) {

            /*
             * Provider API, database, locking or other processing
             * failure.
             *
             * Return retryable 5xx. The durable RECEIVED event
             * remains available for the next delivery.
             */
            return ResponseEntity
                    .status(
                            HttpStatus.SERVICE_UNAVAILABLE
                    )
                    .build();
        }
    }
}