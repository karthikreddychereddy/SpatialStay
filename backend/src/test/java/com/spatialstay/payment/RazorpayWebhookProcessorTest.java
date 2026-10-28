package com.spatialstay.payment;

import com.spatialstay.booking.Booking;
import com.spatialstay.booking.BookingPaymentConfirmationService;
import com.spatialstay.booking.InventoryHold;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.spatialstay.user.User;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RazorpayWebhookProcessorTest {

    private PaymentWebhookEventRepository
            webhookEventRepository;

    private PaymentAttemptRepository
            paymentAttemptRepository;

    private RazorpayPaymentVerifier
            razorpayPaymentVerifier;

    private BookingPaymentConfirmationService
            bookingPaymentConfirmationService;

    private RazorpayWebhookProcessor processor;


    @BeforeEach
    void setUp() {

        webhookEventRepository =
                mock(
                        PaymentWebhookEventRepository.class
                );

        paymentAttemptRepository =
                mock(
                        PaymentAttemptRepository.class
                );

        razorpayPaymentVerifier =
                mock(
                        RazorpayPaymentVerifier.class
                );

        bookingPaymentConfirmationService =
                mock(
                        BookingPaymentConfirmationService.class
                );


        processor =
                new RazorpayWebhookProcessor(
                        webhookEventRepository,
                        paymentAttemptRepository,
                        razorpayPaymentVerifier,
                        bookingPaymentConfirmationService
                );
    }


    @Test
    void unsupportedValidEventIsSafelyIgnoredWithoutTouchingPaymentLifecycle() {

        PaymentWebhookEvent event =
                webhookEvent(
                        """
                        {
                          "event": "order.paid"
                        }
                        """
                );


        when(
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                event.getPayloadFingerprint()
                        )
        ).thenReturn(
                Optional.of(event)
        );


        RazorpayWebhookProcessor.ProcessingResult result =
                processor.process(
                        event.getPayloadFingerprint()
                );


        assertEquals(
                RazorpayWebhookProcessor
                        .ProcessingResult
                        .IGNORED,
                result
        );


        assertEquals(
                PaymentWebhookEventStatus.PROCESSED,
                event.getStatus()
        );


        assertNotNull(
                event.getProcessedAt()
        );


        verifyNoInteractions(
                paymentAttemptRepository,
                razorpayPaymentVerifier,
                bookingPaymentConfirmationService
        );
    }


    @Test
    void malformedCapturedEventIsRejectedBeforePaymentLookup() {

        PaymentWebhookEvent event =
                webhookEvent(
                        """
                        {
                          "event": "payment.captured",
                          "payload": {}
                        }
                        """
                );


        when(
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                event.getPayloadFingerprint()
                        )
        ).thenReturn(
                Optional.of(event)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> processor.process(
                            event.getPayloadFingerprint()
                    )
        );


        assertEquals(
                PaymentWebhookEventStatus.RECEIVED,
                event.getStatus()
        );


        verifyNoInteractions(
                paymentAttemptRepository,
                razorpayPaymentVerifier,
                bookingPaymentConfirmationService
        );
    }


    @Test
    void unknownProviderOrderIsRejectedBeforeProviderVerification() {

        PaymentWebhookEvent event =
                capturedEvent(
                        "pay_unknown",
                        "order_unknown"
                );


        when(
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                event.getPayloadFingerprint()
                        )
        ).thenReturn(
                Optional.of(event)
        );


        when(
                paymentAttemptRepository
                        .findByProviderAndProviderOrderId(
                                PaymentProvider.RAZORPAY,
                                "order_unknown"
                        )
        ).thenReturn(
                Optional.empty()
        );


        assertThrows(
                IllegalStateException.class,
                () -> processor.process(
                    event.getPayloadFingerprint()
            )
        );


        assertEquals(
                PaymentWebhookEventStatus.RECEIVED,
                event.getStatus()
        );


        verifyNoInteractions(
                razorpayPaymentVerifier,
                bookingPaymentConfirmationService
        );
    }


    @Test
    void expiredHoldMovesCapturedPaymentToReconciliationWithoutConfirmingBooking() {

        PaymentWebhookEvent event =
                capturedEvent(
                        "pay_late",
                        "order_late"
                );


        PaymentAttempt attempt =
                mock(
                        PaymentAttempt.class
                );

        Booking booking =
                mock(
                        Booking.class
                );

        InventoryHold hold =
                mock(
                        InventoryHold.class
                );

        UUID bookingPublicId =
                UUID.randomUUID();

        UUID holdPublicId =
                UUID.randomUUID();


        when(
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                event.getPayloadFingerprint()
                        )
        ).thenReturn(
                Optional.of(event)
        );


        when(
                paymentAttemptRepository
                        .findByProviderAndProviderOrderId(
                                PaymentProvider.RAZORPAY,
                                "order_late"
                        )
        ).thenReturn(
                Optional.of(attempt)
        );


        when(
                attempt.getStatus()
        ).thenReturn(
                PaymentStatus.PENDING
        );


        when(
                attempt.getBooking()
        ).thenReturn(
                booking
        );


        when(
                booking.getPublicId()
        ).thenReturn(
                bookingPublicId
        );


        when(
                booking.getInventoryHold()
        ).thenReturn(
                hold
        );


        when(
                hold.getPublicId()
        ).thenReturn(
                holdPublicId
        );


        when(
                bookingPaymentConfirmationService
                        .lockForPaymentConfirmation(
                                eq(holdPublicId),
                                eq(bookingPublicId),
                                any(Instant.class)
                        )
        ).thenThrow(
                new BookingPaymentConfirmationService
                        .HoldUnavailableForConfirmationException(
                        "Inventory hold expired."
                )
        );


        RazorpayWebhookProcessor.ProcessingResult result =
                processor.process(
                    event.getPayloadFingerprint()
            );


        assertEquals(
                RazorpayWebhookProcessor
                        .ProcessingResult
                        .REQUIRES_RECONCILIATION,
                result
        );


        assertEquals(
                PaymentWebhookEventStatus
                        .REQUIRES_RECONCILIATION,
                event.getStatus()
        );


        /*
         * Critical:
         * no provider success transition and no booking confirmation
         * can happen after the hold has become unavailable.
         */
        verifyNoInteractions(
                razorpayPaymentVerifier
        );


        verify(
                paymentAttemptRepository,
                never()
        ).findByProviderOrderForUpdate(
                any(),
                anyString()
        );


        verify(
                bookingPaymentConfirmationService,
                never()
        ).confirmLocked(
                any(),
                any()
        );
    }


    @Test
    void providerVerificationFailureStopsBeforeAnySuccessTransition() {

        PaymentWebhookEvent event =
                capturedEvent(
                        "pay_provider_failure",
                        "order_provider_failure"
                );


        PaymentAttempt discoveredAttempt =
                mock(
                        PaymentAttempt.class
                );

        PaymentAttempt lockedAttempt =
                mock(
                        PaymentAttempt.class
                );

        Booking booking =
                mock(
                        Booking.class
                );

        InventoryHold hold =
                mock(
                        InventoryHold.class
                );

        User user =
                mock(
                        User.class
                );

        Long userId =
                1001L;

        BookingPaymentConfirmationService
                .LockedConfirmationContext context =
                mock(
                        BookingPaymentConfirmationService
                                .LockedConfirmationContext.class
                );


        UUID bookingPublicId =
                UUID.randomUUID();

        UUID holdPublicId =
                UUID.randomUUID();


        when(
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                event.getPayloadFingerprint()
                        )
        ).thenReturn(
                Optional.of(event)
        );


        when(
                paymentAttemptRepository
                        .findByProviderAndProviderOrderId(
                                PaymentProvider.RAZORPAY,
                                "order_provider_failure"
                        )
        ).thenReturn(
                Optional.of(
                        discoveredAttempt
                )
        );


        when(
                discoveredAttempt.getStatus()
        ).thenReturn(
                PaymentStatus.PENDING
        );


        when(
                discoveredAttempt.getBooking()
        ).thenReturn(
                booking
        );


        when(
                booking.getPublicId()
        ).thenReturn(
                bookingPublicId
        );


        when(
                booking.getInventoryHold()
        ).thenReturn(
                hold
        );


        when(
                hold.getPublicId()
        ).thenReturn(
                holdPublicId
        );


        when(
                bookingPaymentConfirmationService
                        .lockForPaymentConfirmation(
                                eq(holdPublicId),
                                eq(bookingPublicId),
                                any(Instant.class)
                        )
        ).thenReturn(
                context
        );


        when(
                context.booking()
        ).thenReturn(
                booking
        );


        when(
                context.hold()
        ).thenReturn(
                hold
        );


        when(
                paymentAttemptRepository
                        .findByProviderOrderForUpdate(
                                PaymentProvider.RAZORPAY,
                                "order_provider_failure"
                        )
        ).thenReturn(
                Optional.of(
                        lockedAttempt
                )
        );


        /*
         * Relationship revalidation must succeed before reaching
         * provider verification.
         */
        when(
                lockedAttempt.getProvider()
        ).thenReturn(
                PaymentProvider.RAZORPAY
        );


        when(
                lockedAttempt.getBooking()
        ).thenReturn(
                booking
        );

        /*
        * Relationship validation must succeed so this test reaches
        * the Razorpay provider-verification failure that it is
        * specifically testing.
        */
        when(
                user.getId()
        ).thenReturn(
                userId
        );


        when(
                lockedAttempt.getUser()
        ).thenReturn(
                user
        );


        when(
                booking.getUser()
        ).thenReturn(
                user
        );


        when(
                razorpayPaymentVerifier
                        .fetchCapturedPayment(
                                "pay_provider_failure"
                        )
        ).thenThrow(
                new PaymentGatewayException(
                        "Temporary Razorpay API failure."
                )
        );


        assertThrows(
                PaymentGatewayException.class,
                () -> processor.process(
                    event.getPayloadFingerprint()
            )
        );


        assertEquals(
                PaymentWebhookEventStatus.RECEIVED,
                event.getStatus()
        );


        verify(
                bookingPaymentConfirmationService,
                never()
        ).confirmLocked(
                any(),
                any()
        );
    }


    @Test
    void wrongProviderAmountIsRejectedBeforeConfirmation() {

        PaymentWebhookEvent event =
                capturedEvent(
                        "pay_wrong_amount",
                        "order_amount"
                );


        ProcessingFixture fixture =
                successfulFixture(
                        event,
                        "order_amount"
                );


        when(
                razorpayPaymentVerifier
                        .fetchCapturedPayment(
                                "pay_wrong_amount"
                        )
        ).thenReturn(
                new VerifiedRazorpayPayment(
                        "pay_wrong_amount",
                        "order_amount",
                        12499L,
                        "INR",
                        "captured",
                        true
                )
        );


        assertThrows(
                IllegalStateException.class,
                () -> processor.process(
                    event.getPayloadFingerprint()
            )
        );


        assertEquals(
                PaymentWebhookEventStatus.RECEIVED,
                event.getStatus()
        );


        verify(
                fixture.lockedAttempt(),
                never()
        ).markSucceeded(
                anyString(),
                any()
        );


        verify(
                bookingPaymentConfirmationService,
                never()
        ).confirmLocked(
                any(),
                any()
        );
    }


    @Test
    void wrongProviderCurrencyIsRejectedBeforeConfirmation() {

        PaymentWebhookEvent event =
                capturedEvent(
                        "pay_wrong_currency",
                        "order_currency"
                );


        ProcessingFixture fixture =
                successfulFixture(
                        event,
                        "order_currency"
                );


        when(
                razorpayPaymentVerifier
                        .fetchCapturedPayment(
                                "pay_wrong_currency"
                        )
        ).thenReturn(
                new VerifiedRazorpayPayment(
                        "pay_wrong_currency",
                        "order_currency",
                        12500L,
                        "USD",
                        "captured",
                        true
                )
        );


        assertThrows(
                IllegalStateException.class,
                () -> processor.process(
                    event.getPayloadFingerprint()
            )
        );


        verify(
                fixture.lockedAttempt(),
                never()
        ).markSucceeded(
                anyString(),
                any()
        );


        verify(
                bookingPaymentConfirmationService,
                never()
        ).confirmLocked(
                any(),
                any()
        );
    }


    @Test
    void correctCapturedPaymentPerformsSuccessTransitions() {

        PaymentWebhookEvent event =
                capturedEvent(
                        "pay_success",
                        "order_success"
                );


        ProcessingFixture fixture =
                successfulFixture(
                        event,
                        "order_success"
                );


        when(
                razorpayPaymentVerifier
                        .fetchCapturedPayment(
                                "pay_success"
                        )
        ).thenReturn(
                new VerifiedRazorpayPayment(
                        "pay_success",
                        "order_success",
                        12500L,
                        "INR",
                        "captured",
                        true
                )
        );


        RazorpayWebhookProcessor.ProcessingResult result =
                processor.process(
                        event.getPayloadFingerprint()
                );


        assertEquals(
                RazorpayWebhookProcessor
                        .ProcessingResult
                        .PROCESSED,
                result
        );


        assertEquals(
                PaymentWebhookEventStatus.PROCESSED,
                event.getStatus()
        );


        verify(
                fixture.lockedAttempt(),
                times(1)
        ).markSucceeded(
                eq("pay_success"),
                any(Instant.class)
        );


        verify(
                bookingPaymentConfirmationService,
                times(1)
        ).confirmLocked(
                eq(fixture.context()),
                any(Instant.class)
        );
    }


    private ProcessingFixture successfulFixture(
            PaymentWebhookEvent event,
            String orderId
    ) {

        PaymentAttempt discoveredAttempt =
                mock(
                        PaymentAttempt.class
                );

        PaymentAttempt lockedAttempt =
                mock(
                        PaymentAttempt.class
                );

        Booking booking =
                mock(
                        Booking.class
                );

        InventoryHold hold =
                mock(
                        InventoryHold.class
                );

        User user =
                mock(
                        User.class
                );

        Long userId =
                2001L;

        BookingPaymentConfirmationService
                .LockedConfirmationContext context =
                mock(
                        BookingPaymentConfirmationService
                                .LockedConfirmationContext.class
                );


        UUID bookingPublicId =
                UUID.randomUUID();

        UUID holdPublicId =
                UUID.randomUUID();


        when(
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                event.getPayloadFingerprint()
                        )
        ).thenReturn(
                Optional.of(event)
        );


        when(
                paymentAttemptRepository
                        .findByProviderAndProviderOrderId(
                                PaymentProvider.RAZORPAY,
                                orderId
                        )
        ).thenReturn(
                Optional.of(
                        discoveredAttempt
                )
        );


        when(
                discoveredAttempt.getStatus()
        ).thenReturn(
                PaymentStatus.PENDING
        );


        when(
                discoveredAttempt.getBooking()
        ).thenReturn(
                booking
        );


        when(
                booking.getPublicId()
        ).thenReturn(
                bookingPublicId
        );


        when(
                booking.getInventoryHold()
        ).thenReturn(
                hold
        );


        when(
                hold.getPublicId()
        ).thenReturn(
                holdPublicId
        );


        when(
                bookingPaymentConfirmationService
                        .lockForPaymentConfirmation(
                                eq(holdPublicId),
                                eq(bookingPublicId),
                                any(Instant.class)
                        )
        ).thenReturn(
                context
        );


        when(
                context.booking()
        ).thenReturn(
                booking
        );


        when(
                context.hold()
        ).thenReturn(
                hold
        );


        when(
                paymentAttemptRepository
                        .findByProviderOrderForUpdate(
                                PaymentProvider.RAZORPAY,
                                orderId
                        )
        ).thenReturn(
                Optional.of(
                        lockedAttempt
                )
        );


        when(
                lockedAttempt.getProvider()
        ).thenReturn(
                PaymentProvider.RAZORPAY
        );


        when(
                lockedAttempt.getProviderOrderId()
        ).thenReturn(
                orderId
        );


        when(
                lockedAttempt.getStatus()
        ).thenReturn(
                PaymentStatus.PENDING
        );


        when(
                lockedAttempt.getBooking()
        ).thenReturn(
                booking
        );


        when(
                lockedAttempt.getExpectedAmount()
        ).thenReturn(
                new BigDecimal(
                        "125.00"
                )
        );


        when(
                lockedAttempt.getCurrency()
        ).thenReturn(
                "INR"
        );


        when(
                booking.getTotalAmount()
        ).thenReturn(
                new BigDecimal(
                        "125.00"
                )
        );


        when(
                booking.getCurrency()
        ).thenReturn(
                "INR"
        );


        when(
                hold.getTotalAmount()
        ).thenReturn(
                new BigDecimal(
                        "125.00"
                )
        );


        when(
                hold.getCurrency()
        ).thenReturn(
                "INR"
        );
        
        /*
        * PaymentAttempt and Booking must belong to the same
        * authoritative SpatialStay user.
        *
        * Production code validates this relationship after the
        * hold/payment locks are acquired.
        */
        when(
                user.getId()
        ).thenReturn(
                userId
        );


        when(
                lockedAttempt.getUser()
        ).thenReturn(
                user
        );


        when(
                booking.getUser()
        ).thenReturn(
                user
        );

        return new ProcessingFixture(
                lockedAttempt,
                context
        );
    }


    private PaymentWebhookEvent capturedEvent(
            String paymentId,
            String orderId
    ) {

        return webhookEvent(
                """
                {
                  "event": "payment.captured",
                  "payload": {
                    "payment": {
                      "entity": {
                        "id": "%s",
                        "order_id": "%s"
                      }
                    }
                  }
                }
                """.formatted(
                        paymentId,
                        orderId
                )
        );
    }


    private PaymentWebhookEvent webhookEvent(
            String json
    ) {

        PaymentWebhookEvent event =
                PaymentWebhookEvent.received(
                        PaymentProvider.RAZORPAY,
                        extractEventType(json),
                        WebhookFingerprint.sha256(
                                json.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        ),
                        json,
                        Instant.now()
                );


        /*
         * Processor normally receives an already-persisted event.
         *
         * Mockito repository stubbing only needs a stable ID for
         * findById(). Reflection is avoided; repository lookup is
         * therefore stubbed using anyLong() below.
         */
        when(
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                event.getPayloadFingerprint()
                        )
        ).thenReturn(
                Optional.of(event)
        );


        return event;
    }


    private String extractEventType(
            String json
    ) {

        if (json.contains(
                "\"payment.captured\""
        )) {
            return "payment.captured";
        }

        if (json.contains(
                "\"order.paid\""
        )) {
            return "order.paid";
        }

        return "unknown";
    }


    private record ProcessingFixture(
            PaymentAttempt lockedAttempt,
            BookingPaymentConfirmationService
                    .LockedConfirmationContext context
    ) {
    }
}