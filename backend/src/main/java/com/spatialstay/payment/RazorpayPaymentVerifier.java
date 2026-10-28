package com.spatialstay.payment;

import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class RazorpayPaymentVerifier {

    private final String keyId;
    private final String keySecret;


    public RazorpayPaymentVerifier(
            @Value("${spatialstay.payment.razorpay.key-id:}")
            String keyId,

            @Value("${spatialstay.payment.razorpay.key-secret:}")
            String keySecret
    ) {
        this.keyId =
                keyId == null
                        ? ""
                        : keyId.trim();

        this.keySecret =
                keySecret == null
                        ? ""
                        : keySecret.trim();
    }


    /**
     * Fetches the payment directly from Razorpay.
     *
     * Webhook JSON is the trigger, but this provider response is
     * the trusted source used before SpatialStay changes booking
     * state.
     */
    public VerifiedRazorpayPayment fetchCapturedPayment(
            String expectedPaymentId
    ) {
        String paymentId =
                requireText(
                        expectedPaymentId,
                        "Razorpay payment ID"
                );

        requireConfigured();

        try {
            RazorpayClient client =
                    new RazorpayClient(
                            keyId,
                            keySecret
                    );

            Payment payment =
                    client.payments.fetch(
                            paymentId
                    );


            String actualPaymentId =
                    payment.get("id");

            String orderId =
                    payment.get("order_id");

            Number amount =
                    payment.get("amount");

            String currency =
                    payment.get("currency");

            String status =
                    payment.get("status");

            Boolean captured =
                    payment.get("captured");


            validateProviderPayment(
                    paymentId,
                    actualPaymentId,
                    orderId,
                    amount,
                    currency,
                    status,
                    captured
            );


            return new VerifiedRazorpayPayment(
                    actualPaymentId.trim(),
                    orderId.trim(),
                    amount.longValue(),
                    currency
                            .trim()
                            .toUpperCase(
                                    Locale.ROOT
                            ),
                    status
                            .trim()
                            .toLowerCase(
                                    Locale.ROOT
                            ),
                    Boolean.TRUE.equals(
                            captured
                    )
            );

        } catch (RazorpayException exception) {

            /*
             * Provider/network/API failure is not equivalent to
             * payment failure.
             *
             * The webhook request should later return a retryable
             * 5xx rather than incorrectly rejecting a real payment.
             */
            throw new PaymentGatewayException(
                    "Unable to verify Razorpay payment.",
                    exception
            );
        }
    }


    private void validateProviderPayment(
            String expectedPaymentId,
            String actualPaymentId,
            String orderId,
            Number amount,
            String currency,
            String status,
            Boolean captured
    ) {
        if (actualPaymentId == null
                || actualPaymentId.isBlank()
                || !expectedPaymentId.equals(
                        actualPaymentId.trim()
                )) {

            throw new PaymentGatewayException(
                    "Razorpay returned an unexpected payment identifier."
            );
        }


        if (orderId == null
                || orderId.isBlank()) {

            throw new PaymentGatewayException(
                    "Razorpay payment does not reference an order."
            );
        }


        if (amount == null
                || amount.longValue() <= 0) {

            throw new PaymentGatewayException(
                    "Razorpay returned an invalid payment amount."
            );
        }


        if (currency == null
                || currency.isBlank()) {

            throw new PaymentGatewayException(
                    "Razorpay returned an invalid payment currency."
            );
        }


        if (status == null
                || !"captured".equalsIgnoreCase(
                        status.trim()
                )) {

            throw new PaymentGatewayException(
                    "Razorpay payment is not captured."
            );
        }


        if (!Boolean.TRUE.equals(
                captured
        )) {

            throw new PaymentGatewayException(
                    "Razorpay payment is not marked as captured."
            );
        }
    }


    private void requireConfigured() {
        if (keyId.isBlank()
                || keySecret.isBlank()) {

            throw new PaymentGatewayException(
                    "Razorpay payment verification is not configured."
            );
        }
    }


    private String requireText(
            String value,
            String field
    ) {
        if (value == null
                || value.isBlank()) {

            throw new IllegalArgumentException(
                    field + " is required."
            );
        }

        return value.trim();
    }
}