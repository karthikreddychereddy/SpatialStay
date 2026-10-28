package com.spatialstay.payment;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RazorpayWebhookSecurityTest {

    private static final String SECRET =
            "test-webhook-secret-not-for-production";

    private static final byte[] BODY =
            "{\"event\":\"payment.captured\"}"
                    .getBytes(StandardCharsets.UTF_8);


    private String signature(
            byte[] payload
    ) throws Exception {

        Mac mac =
                Mac.getInstance(
                        "HmacSHA256"
                );

        mac.init(
                new SecretKeySpec(
                        SECRET.getBytes(
                                StandardCharsets.UTF_8
                        ),
                        "HmacSHA256"
                )
        );

        return HexFormat.of()
                .formatHex(
                        mac.doFinal(
                                payload
                        )
                );
    }


    @Test
    void validSignatureIsRecordedProcessedAndAcknowledged()
            throws Exception {

        RazorpayWebhookSignatureVerifier verifier =
                new RazorpayWebhookSignatureVerifier(
                        SECRET
                );

        RazorpayWebhookEventRecorder eventRecorder =
                mock(
                        RazorpayWebhookEventRecorder.class
                );

        RazorpayWebhookProcessor webhookProcessor =
                mock(
                        RazorpayWebhookProcessor.class
                );


        assertTrue(
                verifier.isValid(
                        BODY,
                        signature(BODY)
                )
        );


        when(
                webhookProcessor.process(
                        anyString()
                )
        ).thenReturn(
                RazorpayWebhookProcessor
                        .ProcessingResult
                        .PROCESSED
        );


        RazorpayWebhookController controller =
                new RazorpayWebhookController(
                        verifier,
                        eventRecorder,
                        webhookProcessor
                );


        ResponseEntity<Void> response =
                controller.receiveWebhook(
                        BODY,
                        signature(BODY)
                );


        assertEquals(
                HttpStatus.OK,
                response.getStatusCode()
        );


        verify(
                eventRecorder,
                times(1)
        ).record(
                BODY
        );


        verify(
                webhookProcessor,
                times(1)
        ).process(
                WebhookFingerprint.sha256(
                        BODY
                )
        );
    }


    @Test
    void missingMalformedAndTamperedSignaturesAreRejected()
            throws Exception {

        RazorpayWebhookSignatureVerifier verifier =
                new RazorpayWebhookSignatureVerifier(
                        SECRET
                );

        RazorpayWebhookEventRecorder eventRecorder =
                mock(
                        RazorpayWebhookEventRecorder.class
                );

        RazorpayWebhookProcessor webhookProcessor =
                mock(
                        RazorpayWebhookProcessor.class
                );


        assertFalse(
                verifier.isValid(
                        BODY,
                        null
                )
        );


        assertFalse(
                verifier.isValid(
                        BODY,
                        "not-hex"
                )
        );


        assertFalse(
                verifier.isValid(
                        "different".getBytes(
                                StandardCharsets.UTF_8
                        ),
                        signature(BODY)
                )
        );


        RazorpayWebhookController controller =
                new RazorpayWebhookController(
                        verifier,
                        eventRecorder,
                        webhookProcessor
                );


        assertEquals(
                HttpStatus.UNAUTHORIZED,
                controller.receiveWebhook(
                        BODY,
                        "bad"
                ).getStatusCode()
        );


        /*
         * Authentication failure must occur before persistence or
         * business processing.
         */
        verifyNoInteractions(
                eventRecorder,
                webhookProcessor
        );
    }


    @Test
    void missingWebhookSecretFailsClosed()
            throws Exception {

        RazorpayWebhookSignatureVerifier verifier =
                new RazorpayWebhookSignatureVerifier(
                        ""
                );

        RazorpayWebhookEventRecorder eventRecorder =
                mock(
                        RazorpayWebhookEventRecorder.class
                );

        RazorpayWebhookProcessor webhookProcessor =
                mock(
                        RazorpayWebhookProcessor.class
                );


        assertFalse(
                verifier.isConfigured()
        );


        assertFalse(
                verifier.isValid(
                        BODY,
                        signature(BODY)
                )
        );


        RazorpayWebhookController controller =
                new RazorpayWebhookController(
                        verifier,
                        eventRecorder,
                        webhookProcessor
                );


        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                controller.receiveWebhook(
                        BODY,
                        signature(BODY)
                ).getStatusCode()
        );


        verifyNoInteractions(
                eventRecorder,
                webhookProcessor
        );
    }


    @Test
    void processorFailureReturnsRetryableServiceUnavailable()
            throws Exception {

        RazorpayWebhookSignatureVerifier verifier =
                new RazorpayWebhookSignatureVerifier(
                        SECRET
                );

        RazorpayWebhookEventRecorder eventRecorder =
                mock(
                        RazorpayWebhookEventRecorder.class
                );

        RazorpayWebhookProcessor webhookProcessor =
                mock(
                        RazorpayWebhookProcessor.class
                );


        when(
                webhookProcessor.process(
                        anyString()
                )
        ).thenThrow(
                new IllegalStateException(
                        "Temporary processing failure"
                )
        );


        RazorpayWebhookController controller =
                new RazorpayWebhookController(
                        verifier,
                        eventRecorder,
                        webhookProcessor
                );


        ResponseEntity<Void> response =
                controller.receiveWebhook(
                        BODY,
                        signature(BODY)
                );


        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );


        verify(
                eventRecorder,
                times(1)
        ).record(
                BODY
        );


        verify(
                webhookProcessor,
                times(1)
        ).process(
                WebhookFingerprint.sha256(
                        BODY
                )
        );
    }


    @Test
    void malformedRecordedPayloadReturnsBadRequest()
            throws Exception {

        RazorpayWebhookSignatureVerifier verifier =
                new RazorpayWebhookSignatureVerifier(
                        SECRET
                );

        RazorpayWebhookEventRecorder eventRecorder =
                mock(
                        RazorpayWebhookEventRecorder.class
                );

        RazorpayWebhookProcessor webhookProcessor =
                mock(
                        RazorpayWebhookProcessor.class
                );


        when(
                webhookProcessor.process(
                        anyString()
                )
        ).thenThrow(
                new IllegalArgumentException(
                        "Invalid Razorpay webhook payload."
                )
        );


        RazorpayWebhookController controller =
                new RazorpayWebhookController(
                        verifier,
                        eventRecorder,
                        webhookProcessor
                );


        ResponseEntity<Void> response =
                controller.receiveWebhook(
                        BODY,
                        signature(BODY)
                );


        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );
    }
}