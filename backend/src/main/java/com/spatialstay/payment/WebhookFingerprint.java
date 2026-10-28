package com.spatialstay.payment;

import java.security.MessageDigest;
import java.util.HexFormat;

final class WebhookFingerprint {

    private WebhookFingerprint() {
    }


    static String sha256(
            byte[] rawBody
    ) {
        if (rawBody == null
                || rawBody.length == 0) {

            throw new IllegalArgumentException(
                    "Webhook body is required."
            );
        }

        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            return HexFormat.of()
                    .formatHex(
                            digest.digest(
                                    rawBody
                            )
                    );

        } catch (Exception exception) {

            throw new IllegalStateException(
                    "Unable to fingerprint webhook.",
                    exception
            );
        }
    }
}