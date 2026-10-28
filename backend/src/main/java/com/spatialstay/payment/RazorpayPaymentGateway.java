package com.spatialstay.payment;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Date;

@Component
public class RazorpayPaymentGateway
        implements PaymentGateway {

    private static final BigDecimal SUBUNITS_PER_INR =
            new BigDecimal("100");

    private final String keyId;

    private final String keySecret;


    public RazorpayPaymentGateway(

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


    @Override
    public PaymentProvider provider() {

        return PaymentProvider.RAZORPAY;
    }


    @Override
    public ProviderOrderResult createOrder(
            PaymentAttempt paymentAttempt
    ) {

        validatePaymentAttempt(
                paymentAttempt
        );

        requireConfigured();

        long amountInSubunits =
                toRazorpaySubunits(
                        paymentAttempt.getExpectedAmount(),
                        paymentAttempt.getCurrency()
                );

        String receipt =
                createReceipt(
                        paymentAttempt
                );

        JSONObject notes =
                new JSONObject();

        notes.put(
                "spatialstay_payment_id",
                paymentAttempt
                        .getPublicId()
                        .toString()
        );

        notes.put(
                "spatialstay_booking_id",
                paymentAttempt
                        .getBooking()
                        .getPublicId()
                        .toString()
        );

        notes.put(
                "booking_reference",
                paymentAttempt
                        .getBooking()
                        .getBookingReference()
        );

        JSONObject orderRequest =
                new JSONObject();

        orderRequest.put(
                "amount",
                amountInSubunits
        );

        orderRequest.put(
                "currency",
                paymentAttempt
                        .getCurrency()
                        .toUpperCase(
                                Locale.ROOT
                        )
        );

        orderRequest.put(
                "receipt",
                receipt
        );

        orderRequest.put(
                "notes",
                notes
        );

        try {

            RazorpayClient client =
                    new RazorpayClient(
                            keyId,
                            keySecret
                    );

            Order order =
                    client.orders.create(
                            orderRequest
                    );

            String providerOrderId =
                    order.get("id");

            Integer providerAmount =
                    order.get("amount");

            String providerCurrency =
                    order.get("currency");

            String providerStatus =
                    order.get("status");

            Object createdAtValue =
                order.get("created_at");

            validateProviderResponse(
                    providerOrderId,
                    providerAmount,
                    providerCurrency,
                    amountInSubunits,
                    paymentAttempt.getCurrency()
            );

            Instant providerCreatedAt =
                toProviderCreatedAt(
                        createdAtValue
                );

            return new ProviderOrderResult(
                    providerOrderId,
                    providerAmount.longValue(),
                    providerCurrency,
                    providerStatus,
                    providerCreatedAt
            );

        } catch (RazorpayException exception) {

            /*
             * Do not leak API credentials or raw provider
             * responses into customer-facing messages.
             */
            throw new PaymentGatewayException(
                    "Unable to create Razorpay payment order.",
                    exception
            );
        }
    }


    private void validatePaymentAttempt(
            PaymentAttempt paymentAttempt
    ) {

        if (paymentAttempt == null) {

            throw new IllegalArgumentException(
                    "Payment attempt is required."
            );
        }

        if (paymentAttempt.getProvider()
                != PaymentProvider.RAZORPAY) {

            throw new IllegalArgumentException(
                    "Payment attempt is not configured for Razorpay."
            );
        }

        if (paymentAttempt.getStatus()
                != PaymentStatus.CREATED) {

            throw new IllegalStateException(
                    "Only a CREATED payment attempt can create a provider order."
            );
        }

        if (paymentAttempt.getPublicId()
                == null) {

            throw new IllegalStateException(
                    "Payment attempt must be persisted before provider order creation."
            );
        }

        if (paymentAttempt.getBooking()
                == null
                || paymentAttempt
                        .getBooking()
                        .getPublicId()
                == null) {

            throw new IllegalStateException(
                    "Payment attempt must reference a persisted booking."
            );
        }

        if (paymentAttempt.getExpectedAmount()
                == null
                || paymentAttempt
                        .getExpectedAmount()
                        .signum() <= 0) {

            throw new IllegalStateException(
                    "Payment amount must be positive."
            );
        }

        if (paymentAttempt.getCurrency()
                == null
                || paymentAttempt
                        .getCurrency()
                        .isBlank()) {

            throw new IllegalStateException(
                    "Payment currency is required."
            );
        }
    }


    /*
     * Step 10 currently supports Razorpay INR checkout.
     *
     * We fail closed for another currency rather than silently
     * applying INR's 100-subunit conversion to an unsupported
     * currency.
     */
    private long toRazorpaySubunits(
            BigDecimal amount,
            String currency
    ) {

        if (!"INR".equalsIgnoreCase(
                currency
        )) {

            throw new IllegalArgumentException(
                    "Razorpay payment currently supports INR only."
            );
        }

        try {

            BigDecimal subunits =
                    amount
                            .multiply(
                                    SUBUNITS_PER_INR
                            )
                            .setScale(
                                    0,
                                    RoundingMode.UNNECESSARY
                            );

            return subunits
                    .longValueExact();

        } catch (ArithmeticException exception) {

            throw new IllegalArgumentException(
                    "Payment amount cannot be represented safely in paise.",
                    exception
            );
        }
    }


    /*
     * Razorpay receipts have provider constraints, so keep this
     * deterministic, short and based on our own payment ID.
     */
    private String createReceipt(
            PaymentAttempt paymentAttempt
    ) {

        String compactId =
                paymentAttempt
                        .getPublicId()
                        .toString()
                        .replace(
                                "-",
                                ""
                        );

        return "SS-"
                + compactId.substring(
                        0,
                        24
                );
    }


    private void validateProviderResponse(

            String providerOrderId,

            Integer providerAmount,

            String providerCurrency,

            long expectedAmountInSubunits,

            String expectedCurrency

    ) {

        if (providerOrderId == null
                || providerOrderId.isBlank()) {

            throw new PaymentGatewayException(
                    "Razorpay returned an invalid order identifier."
            );
        }

        if (providerAmount == null
                || providerAmount.longValue()
                != expectedAmountInSubunits) {

            throw new PaymentGatewayException(
                    "Razorpay returned an unexpected order amount."
            );
        }

        if (providerCurrency == null
                || !providerCurrency.equalsIgnoreCase(
                        expectedCurrency
                )) {

            throw new PaymentGatewayException(
                    "Razorpay returned an unexpected order currency."
            );
        }
    }

        /*
        * Razorpay Java SDK versions may expose created_at as
        * java.util.Date, while some provider representations use
        * a numeric Unix timestamp.
        *
        * Keep this conversion isolated from the payment flow so a
        * harmless SDK representation difference cannot break an
        * otherwise valid provider order.
        */
        private Instant toProviderCreatedAt(
                Object createdAtValue
        ) {

        if (createdAtValue == null) {
                return Instant.now();
        }

        if (createdAtValue instanceof Date date) {
                return date.toInstant();
        }

        if (createdAtValue instanceof Number number) {
                return Instant.ofEpochSecond(
                        number.longValue()
                );
        }

        throw new PaymentGatewayException(
                "Razorpay returned an unsupported order creation timestamp."
        );
        }
    private void requireConfigured() {

        if (keyId.isBlank()
                || keySecret.isBlank()) {

            throw new PaymentGatewayException(
                    "Razorpay payment gateway is not configured."
            );
        }
    }
}