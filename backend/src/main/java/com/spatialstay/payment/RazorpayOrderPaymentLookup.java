package com.spatialstay.payment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * Read-only Razorpay order reconciliation lookup.
 * Never changes a PaymentAttempt, Booking, InventoryHold, or Refund.
 */
@Component
public class RazorpayOrderPaymentLookup {
    private final String keyId;
    private final String keySecret;
    private final RazorpayPaymentVerifier verifier;
    private final HttpClient httpClient;

    public RazorpayOrderPaymentLookup(
            @Value("${spatialstay.payment.razorpay.key-id:}") String keyId,
            @Value("${spatialstay.payment.razorpay.key-secret:}") String keySecret,
            RazorpayPaymentVerifier verifier
    ) {
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
        this.verifier = verifier;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * Returns zero or one captured payment verified against the provider.
     * Multiple captures for the same order require manual reconciliation.
     */
    public List<VerifiedRazorpayPayment> findCapturedPayments(
            String providerOrderId,
            BigDecimal expectedAmount,
            String expectedCurrency
    ) {
        if (providerOrderId == null || !providerOrderId.matches("order_[A-Za-z0-9]+")) {
            throw new IllegalArgumentException("Invalid Razorpay order ID.");
        }
        if (expectedAmount == null || expectedAmount.signum() <= 0
                || expectedCurrency == null || expectedCurrency.isBlank()) {
            throw new IllegalArgumentException("Expected payment amount and currency are required.");
        }
        if (keyId.isBlank() || keySecret.isBlank()) {
            throw new PaymentGatewayException("Razorpay verification is not configured.");
        }
        final long expectedSubunits;
        try {
            expectedSubunits = expectedAmount.multiply(new BigDecimal("100"))
                    .setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Invalid expected payment amount.", exception);
        }
        String credentials = Base64.getEncoder().encodeToString(
                (keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        String url = "https://api.razorpay.com/v1/orders/"
                + URLEncoder.encode(providerOrderId, StandardCharsets.UTF_8) + "/payments";
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Basic " + credentials)
                .header("Accept", "application/json")
                .GET().build();
        try {
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new PaymentGatewayException(
                        "Razorpay order payment lookup failed (HTTP " + response.statusCode() + ").");
            }
            JSONObject result = new JSONObject(response.body());
            JSONArray items = result.getJSONArray("items");
            if (result.optInt("count", -1) != items.length()) {
                throw new PaymentGatewayException("Incomplete Razorpay order payment list; reconciliation deferred.");
            }
            List<VerifiedRazorpayPayment> captures = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (!"captured".equalsIgnoreCase(item.optString("status"))) {
                    continue;
                }
                String paymentId = item.getString("id");
                VerifiedRazorpayPayment verified = verifier.fetchCapturedPayment(paymentId);
                if (!providerOrderId.equals(verified.orderId())
                        || verified.amountInSubunits() != expectedSubunits
                        || !expectedCurrency.trim().toUpperCase(Locale.ROOT).equals(verified.currency())
                        || !verified.captured()) {
                    throw new PaymentGatewayException("Captured payment does not match authoritative order, amount, or currency.");
                }
                captures.add(verified);
            }
            if (captures.size() > 1) {
                throw new PaymentGatewayException("Multiple captured payments for one order; manual reconciliation required.");
            }
            return List.copyOf(captures);
        } catch (IOException exception) {
            throw new PaymentGatewayException("Razorpay payment lookup network failure.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("Razorpay payment lookup interrupted.", exception);
        } catch (org.json.JSONException exception) {
            throw new PaymentGatewayException("Unexpected Razorpay payment lookup response.", exception);
        }
    }
}
