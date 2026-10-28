package com.spatialstay.refund;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;

import com.spatialstay.payment.PaymentGatewayException;

import org.json.JSONObject;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

@Component
public class RazorpayRefundGateway {

    private static final BigDecimal PAISE =
            new BigDecimal("100");

    private static final String CANCELLATION_NOTE_KEY =
            "spatialstay_refund_id";

    private static final String MODIFICATION_ALLOCATION_NOTE_KEY =
            "spatialstay_modification_refund_allocation_id";

    private static final String CANCELLATION_ALLOCATION_NOTE_KEY =
            "spatialstay_refund_allocation_id";

    private final String keyId;
    private final String keySecret;

    public RazorpayRefundGateway(
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

    /*
     * Existing cancellation refund API.
     *
     * Keep this signature unchanged because the existing cancellation
     * refund processing service already depends on it.
     */
    public RazorpayRefundResult createRefund(
            UUID refundPublicId,
            String providerPaymentId,
            BigDecimal refundAmount,
            String currency
    ) {
        return createRefundInternal(
                refundPublicId,
                providerPaymentId,
                refundAmount,
                currency,
                CANCELLATION_NOTE_KEY,
                "SSR-"
        );
    }

    /*
     * Step 11.11 modification-refund API.
     *
     * A different Razorpay note key is critical. Modification refund
     * allocation UUIDs must never be interpreted as cancellation
     * Refund UUIDs by webhook processing.
     */
    public RazorpayRefundResult createCancellationAllocationRefund(
            UUID allocationPublicId,
            String providerPaymentId,
            BigDecimal refundAmount,
            String currency
    ) {
        return createRefundInternal(
                allocationPublicId, providerPaymentId, refundAmount, currency,
                CANCELLATION_ALLOCATION_NOTE_KEY, "SSCR-"
        );
    }

    public RazorpayRefundResult createModificationRefund(
            UUID allocationPublicId,
            String providerPaymentId,
            BigDecimal refundAmount,
            String currency
    ) {
        return createRefundInternal(
                allocationPublicId,
                providerPaymentId,
                refundAmount,
                currency,
                MODIFICATION_ALLOCATION_NOTE_KEY,
                "SSMR-"
        );
    }

    private RazorpayRefundResult createRefundInternal(
            UUID internalPublicId,
            String providerPaymentId,
            BigDecimal refundAmount,
            String currency,
            String noteKey,
            String receiptPrefix
    ) {
        if (internalPublicId == null) {
            throw new IllegalArgumentException(
                    "Refund correlation ID is required."
            );
        }

        if (providerPaymentId == null
                || providerPaymentId.isBlank()) {

            throw new IllegalArgumentException(
                    "Provider payment ID is required."
            );
        }

        requireConfigured();

        long amountInPaise =
                toPaise(
                        refundAmount,
                        currency
                );

        JSONObject notes =
                new JSONObject();

        notes.put(
                noteKey,
                internalPublicId.toString()
        );

        JSONObject request =
                new JSONObject();

        request.put(
                "amount",
                amountInPaise
        );

        request.put(
                "speed",
                "normal"
        );

        request.put(
                "notes",
                notes
        );

        String receipt =
                receiptPrefix
                        + internalPublicId
                        .toString()
                        .replace("-", "")
                        .substring(0, 24);

        request.put(
                "receipt",
                receipt
        );

        try {
            RazorpayClient client =
                    new RazorpayClient(
                            keyId,
                            keySecret
                    );

            com.razorpay.Refund providerRefund =
                    client.payments.refund(
                            providerPaymentId.trim(),
                            request
                    );

            String actualRefundId =
                    providerRefund.get("id");

            String actualPaymentId =
                    providerRefund.get("payment_id");

            Number actualAmount =
                    providerRefund.get("amount");

            String actualCurrency =
                    providerRefund.get("currency");

            String actualStatus =
                    providerRefund.get("status");

            validateResponse(
                    actualRefundId,
                    actualPaymentId,
                    actualAmount,
                    actualCurrency,
                    actualStatus,
                    providerPaymentId.trim(),
                    amountInPaise,
                    currency
            );

            return new RazorpayRefundResult(
                    actualRefundId.trim(),
                    actualPaymentId.trim(),
                    actualAmount.longValue(),
                    actualCurrency
                            .trim()
                            .toUpperCase(Locale.ROOT),
                    actualStatus
                            .trim()
                            .toLowerCase(Locale.ROOT)
            );

        } catch (RazorpayException exception) {

            /*
             * A provider/network exception does NOT prove that Razorpay
             * failed to create the refund.
             *
             * Therefore callers must move the durable obligation into
             * reconciliation rather than blindly retrying.
             */
            throw new PaymentGatewayException(
                    "Razorpay refund request outcome is uncertain.",
                    exception
            );
        }
    }

    public RazorpayRefundResult fetchRefund(
            String providerRefundId
    ) {
        if (providerRefundId == null
                || providerRefundId.isBlank()) {

            throw new IllegalArgumentException(
                    "Provider refund ID is required."
            );
        }

        requireConfigured();

        try {
            RazorpayClient client =
                    new RazorpayClient(
                            keyId,
                            keySecret
                    );

            com.razorpay.Refund providerRefund =
                    client.refunds.fetch(
                            providerRefundId.trim()
                    );

            String actualRefundId =
                    providerRefund.get("id");

            String paymentId =
                    providerRefund.get("payment_id");

            Number amount =
                    providerRefund.get("amount");

            String currency =
                    providerRefund.get("currency");

            String status =
                    providerRefund.get("status");

            if (actualRefundId == null
                    || !providerRefundId
                    .trim()
                    .equals(
                            actualRefundId.trim()
                    )
                    || paymentId == null
                    || paymentId.isBlank()
                    || amount == null
                    || amount.longValue() <= 0
                    || currency == null
                    || currency.isBlank()
                    || status == null
                    || status.isBlank()) {

                throw new PaymentGatewayException(
                        "Razorpay returned an invalid refund record."
                );
            }

            return new RazorpayRefundResult(
                    actualRefundId.trim(),
                    paymentId.trim(),
                    amount.longValue(),
                    currency
                            .trim()
                            .toUpperCase(Locale.ROOT),
                    status
                            .trim()
                            .toLowerCase(Locale.ROOT)
            );

        } catch (RazorpayException exception) {

            throw new PaymentGatewayException(
                    "Unable to fetch Razorpay refund.",
                    exception
            );
        }
    }

    private void validateResponse(
            String refundId,
            String paymentId,
            Number amount,
            String currency,
            String status,
            String expectedPaymentId,
            long expectedAmount,
            String expectedCurrency
    ) {
        if (refundId == null
                || refundId.isBlank()
                || !refundId.startsWith("rfnd_")) {

            throw new PaymentGatewayException(
                    "Razorpay returned an invalid refund identifier."
            );
        }

        if (paymentId == null
                || !paymentId
                .trim()
                .equals(
                        expectedPaymentId
                )) {

            throw new PaymentGatewayException(
                    "Razorpay refund payment ID mismatch."
            );
        }

        if (amount == null
                || amount.longValue()
                != expectedAmount) {

            throw new PaymentGatewayException(
                    "Razorpay refund amount mismatch."
            );
        }

        if (currency == null
                || !currency.equalsIgnoreCase(
                        expectedCurrency
                )) {

            throw new PaymentGatewayException(
                    "Razorpay refund currency mismatch."
            );
        }

        if (status == null
                || status.isBlank()) {

            throw new PaymentGatewayException(
                    "Razorpay refund status is missing."
            );
        }
    }

    private long toPaise(
            BigDecimal amount,
            String currency
    ) {
        if (!"INR".equalsIgnoreCase(currency)) {

            throw new IllegalArgumentException(
                    "Razorpay refunds currently support INR only."
            );
        }

        if (amount == null
                || amount.signum() <= 0) {

            throw new IllegalArgumentException(
                    "Refund amount must be positive."
            );
        }

        try {
            return amount
                    .multiply(PAISE)
                    .setScale(
                            0,
                            RoundingMode.UNNECESSARY
                    )
                    .longValueExact();

        } catch (ArithmeticException exception) {

            throw new IllegalArgumentException(
                    "Refund amount cannot be represented safely in paise.",
                    exception
            );
        }
    }

    private void requireConfigured() {
        if (keyId.isBlank()
                || keySecret.isBlank()) {

            throw new PaymentGatewayException(
                    "Razorpay refund gateway is not configured."
            );
        }
    }
}