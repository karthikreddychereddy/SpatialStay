package com.spatialstay.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class RazorpayWebhookSignatureVerifier {

    private final String webhookSecret;

    public RazorpayWebhookSignatureVerifier(
            @Value("${spatialstay.payment.razorpay.webhook-secret:}")
            String webhookSecret
    ) {
        this.webhookSecret =
                webhookSecret == null
                        ? ""
                        : webhookSecret.trim();
    }

    public boolean isConfigured() {
        return !webhookSecret.isBlank();
    }

    public boolean isValid(
            byte[] rawBody,
            String signature
    ) {
        if (!isConfigured()
                || rawBody == null
                || signature == null
                || signature.isBlank()) {

            return false;
        }

        try {
            Mac mac =
                    Mac.getInstance(
                            "HmacSHA256"
                    );

            SecretKeySpec secretKey =
                    new SecretKeySpec(
                            webhookSecret.getBytes(
                                    StandardCharsets.UTF_8
                            ),
                            "HmacSHA256"
                    );

            mac.init(secretKey);

            byte[] expectedSignature =
                    mac.doFinal(rawBody);

            byte[] receivedSignature =
                    HexFormat.of()
                            .parseHex(
                                    signature.trim()
                            );

            return MessageDigest.isEqual(
                    expectedSignature,
                    receivedSignature
            );

        } catch (Exception exception) {
            return false;
        }
    }
}