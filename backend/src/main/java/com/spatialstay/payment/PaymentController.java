package com.spatialstay.payment;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    private final ModificationPaymentService
            modificationPaymentService;

    public PaymentController(
            PaymentService paymentService,
            ModificationPaymentService modificationPaymentService
    ) {
        this.paymentService =
                paymentService;

        this.modificationPaymentService =
                modificationPaymentService;
    }

    /*
     * Original booking checkout.
     */
    @PostMapping("/bookings/{bookingId}/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentCheckoutResponse createCheckout(
            @PathVariable
            UUID bookingId
    ) {
        return paymentService.createCheckout(
                bookingId
        );
    }

    /*
     * Step 11.11:
     *
     * Creates the Razorpay checkout for ONLY the positive price
     * difference of a booking modification.
     */
    @PostMapping(
            "/modifications/{modificationId}/checkout"
    )
    @ResponseStatus(HttpStatus.CREATED)
    public ModificationPaymentCheckoutResponse
    createModificationCheckout(
            @PathVariable
            UUID modificationId
    ) {
        return modificationPaymentService
                .createCheckout(
                        modificationId
                );
    }

    @GetMapping("/{paymentId}")
    public PaymentAttemptResponse getPayment(
            @PathVariable
            UUID paymentId
    ) {
        return paymentService
                .getOwnedPaymentAttempt(
                        paymentId
                );
    }

    @GetMapping("/bookings/{bookingId}")
    public List<PaymentAttemptResponse>
    getBookingPayments(
            @PathVariable
            UUID bookingId
    ) {
        return paymentService
                .getOwnedPaymentAttemptsForBooking(
                        bookingId
                );
    }
}