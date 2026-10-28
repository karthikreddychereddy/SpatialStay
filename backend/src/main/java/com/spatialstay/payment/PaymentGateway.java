package com.spatialstay.payment;

public interface PaymentGateway {

    PaymentProvider provider();

    ProviderOrderResult createOrder(
            PaymentAttempt paymentAttempt
    );
}