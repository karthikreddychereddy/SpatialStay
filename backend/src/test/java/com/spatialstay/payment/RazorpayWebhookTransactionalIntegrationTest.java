package com.spatialstay.payment;

import com.spatialstay.booking.Booking;
import com.spatialstay.booking.BookingRepository;
import com.spatialstay.booking.BookingStatus;
import com.spatialstay.booking.InventoryHold;
import com.spatialstay.booking.InventoryHoldRepository;
import com.spatialstay.booking.InventoryHoldStatus;

import com.spatialstay.hotel.Hotel;
import com.spatialstay.hotel.HotelRepository;

import com.spatialstay.room.Room;
import com.spatialstay.room.RoomRepository;

import com.spatialstay.user.AccountTokenRepository;
import com.spatialstay.user.User;
import com.spatialstay.user.UserRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.*;


@SpringBootTest
@ActiveProfiles("test")
class RazorpayWebhookTransactionalIntegrationTest {

    private static final String PASSWORD =
            "SpatialStay2026";

    private static final BigDecimal PRICE_PER_NIGHT =
            new BigDecimal("5000.00");

    private static final BigDecimal TOTAL_AMOUNT =
            new BigDecimal("10000.00");

    private static final String CURRENCY =
            "INR";


    @Autowired
    private RazorpayWebhookProcessor processor;

    @Autowired
    private PaymentWebhookEventRepository webhookEventRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private InventoryHoldRepository inventoryHoldRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountTokenRepository accountTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;


    /*
     * Razorpay is the only external boundary in this integration
     * test.
     *
     * Everything else:
     *
     * - PaymentAttempt
     * - Booking
     * - InventoryHold
     * - PaymentWebhookEvent
     * - repositories
     * - pessimistic locks
     * - transaction rollback
     *
     * uses the real Spring/JPA/PostgreSQL implementation.
     */
    @MockitoBean
    private RazorpayPaymentVerifier razorpayPaymentVerifier;


    private User customer;
    private Hotel hotel;
    private Room room;

    private LocalDate checkIn;
    private LocalDate checkOut;


    @BeforeEach
    void setUp() {

        reset(
                razorpayPaymentVerifier
        );

        /*
         * Delete child records before parent records.
         */
        webhookEventRepository.deleteAll();
        paymentAttemptRepository.deleteAll();
        bookingRepository.deleteAll();
        inventoryHoldRepository.deleteAll();

        accountTokenRepository.deleteAll();

        roomRepository.deleteAll();
        hotelRepository.deleteAll();
        userRepository.deleteAll();


        customer =
                createCustomer(
                        "payment-webhook-integration@example.com"
                );

        hotel =
                createHotel();

        room =
                createRoom(
                        hotel
                );


        checkIn =
                LocalDate.now()
                        .plusDays(10);

        checkOut =
                checkIn.plusDays(2);
    }


    /*
     * ------------------------------------------------------------
     * SUCCESSFUL CAPTURE
     * ------------------------------------------------------------
     */

    @Test
    void capturedPaymentCommitsEntireAuthoritativeLifecycle() {

        String orderId =
                "order_tx_success";

        String paymentId =
                "pay_tx_success";


        LifecycleFixture fixture =
                createPendingLifecycle(
                        orderId,
                        false
                );


        PaymentWebhookEvent event =
                createCapturedWebhook(
                        paymentId,
                        orderId,
                        ""
                );


        when(
                razorpayPaymentVerifier
                        .fetchCapturedPayment(
                                paymentId
                        )
        ).thenReturn(
                new VerifiedRazorpayPayment(
                        paymentId,
                        orderId,
                        1_000_000L,
                        CURRENCY,
                        "captured",
                        true
                )
        );


        RazorpayWebhookProcessor.ProcessingResult result =
                processor.process(
                        event.getPayloadFingerprint()
                );


        assertThat(result)
                .isEqualTo(
                        RazorpayWebhookProcessor
                                .ProcessingResult
                                .PROCESSED
                );


        clearPersistenceContext();


        PaymentAttempt refreshedAttempt =
                paymentAttemptRepository
                        .findByPublicId(
                                fixture.paymentAttempt()
                                        .getPublicId()
                        )
                        .orElseThrow();


        Booking refreshedBooking =
                bookingRepository
                        .findByPublicId(
                                fixture.booking()
                                        .getPublicId()
                        )
                        .orElseThrow();


        InventoryHold refreshedHold =
                inventoryHoldRepository
                        .findByPublicId(
                                fixture.hold()
                                        .getPublicId()
                        )
                        .orElseThrow();


        PaymentWebhookEvent refreshedEvent =
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                event.getPayloadFingerprint()
                        )
                        .orElseThrow();


        assertThat(
                refreshedAttempt.getStatus()
        ).isEqualTo(
                PaymentStatus.SUCCEEDED
        );


        assertThat(
                refreshedAttempt.getProviderPaymentId()
        ).isEqualTo(
                paymentId
        );


        assertThat(
                refreshedAttempt.getSucceededAt()
        ).isNotNull();


        assertThat(
                refreshedBooking.getStatus()
        ).isEqualTo(
                BookingStatus.CONFIRMED
        );


        assertThat(
                refreshedBooking.getConfirmedAt()
        ).isNotNull();


        assertThat(
                refreshedHold.getStatus()
        ).isEqualTo(
                InventoryHoldStatus.CONSUMED
        );


        assertThat(
                refreshedHold.getConsumedAt()
        ).isNotNull();


        assertThat(
                refreshedEvent.getStatus()
        ).isEqualTo(
                PaymentWebhookEventStatus.PROCESSED
        );


        assertThat(
                refreshedEvent.getProcessedAt()
        ).isNotNull();


        verify(
                razorpayPaymentVerifier,
                times(1)
        ).fetchCapturedPayment(
                paymentId
        );
    }


    /*
     * ------------------------------------------------------------
     * TRANSACTION ROLLBACK
     * ------------------------------------------------------------
     */

    @Test
    void providerVerificationFailureRollsBackEntireWebhookTransaction() {

        String orderId =
                "order_tx_failure";

        String paymentId =
                "pay_tx_failure";


        LifecycleFixture fixture =
                createPendingLifecycle(
                        orderId,
                        false
                );


        PaymentWebhookEvent event =
                createCapturedWebhook(
                        paymentId,
                        orderId,
                        ""
                );


        when(
                razorpayPaymentVerifier
                        .fetchCapturedPayment(
                                paymentId
                        )
        ).thenThrow(
                new PaymentGatewayException(
                        "Temporary Razorpay verification failure."
                )
        );


        assertThatThrownBy(
                () ->
                        processor.process(
                                event.getPayloadFingerprint()
                        )
        )
                .isInstanceOf(
                        PaymentGatewayException.class
                );


        clearPersistenceContext();


        PaymentAttempt refreshedAttempt =
                paymentAttemptRepository
                        .findByPublicId(
                                fixture.paymentAttempt()
                                        .getPublicId()
                        )
                        .orElseThrow();


        Booking refreshedBooking =
                bookingRepository
                        .findByPublicId(
                                fixture.booking()
                                        .getPublicId()
                        )
                        .orElseThrow();


        InventoryHold refreshedHold =
                inventoryHoldRepository
                        .findByPublicId(
                                fixture.hold()
                                        .getPublicId()
                        )
                        .orElseThrow();


        PaymentWebhookEvent refreshedEvent =
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                event.getPayloadFingerprint()
                        )
                        .orElseThrow();


        /*
         * Nothing in the authoritative lifecycle may partially
         * succeed when provider verification fails.
         */
        assertThat(
                refreshedAttempt.getStatus()
        ).isEqualTo(
                PaymentStatus.PENDING
        );


        assertThat(
                refreshedAttempt.getProviderPaymentId()
        ).isNull();


        assertThat(
                refreshedBooking.getStatus()
        ).isEqualTo(
                BookingStatus.PENDING_PAYMENT
        );


        assertThat(
                refreshedHold.getStatus()
        ).isEqualTo(
                InventoryHoldStatus.ACTIVE
        );


        /*
         * The webhook was durably recorded before processing.
         *
         * Because processing failed, it remains RECEIVED so
         * Razorpay can retry it.
         */
        assertThat(
                refreshedEvent.getStatus()
        ).isEqualTo(
                PaymentWebhookEventStatus.RECEIVED
        );


        assertThat(
                refreshedEvent.getProcessedAt()
        ).isNull();
    }


    /*
     * ------------------------------------------------------------
     * LATE CAPTURE / EXPIRED HOLD
     * ------------------------------------------------------------
     */

    @Test
    void capturedPaymentAfterHoldExpiryRequiresReconciliationAndNeverConfirmsBooking() {

        String orderId =
                "order_tx_late";

        String paymentId =
                "pay_tx_late";


        LifecycleFixture fixture =
                createPendingLifecycle(
                        orderId,
                        true
                );


        PaymentWebhookEvent event =
                createCapturedWebhook(
                        paymentId,
                        orderId,
                        ""
                );


        RazorpayWebhookProcessor.ProcessingResult result =
                processor.process(
                        event.getPayloadFingerprint()
                );


        assertThat(result)
                .isEqualTo(
                        RazorpayWebhookProcessor
                                .ProcessingResult
                                .REQUIRES_RECONCILIATION
                );


        clearPersistenceContext();


        PaymentAttempt refreshedAttempt =
                paymentAttemptRepository
                        .findByPublicId(
                                fixture.paymentAttempt()
                                        .getPublicId()
                        )
                        .orElseThrow();


        Booking refreshedBooking =
                bookingRepository
                        .findByPublicId(
                                fixture.booking()
                                        .getPublicId()
                        )
                        .orElseThrow();


        InventoryHold refreshedHold =
                inventoryHoldRepository
                        .findByPublicId(
                                fixture.hold()
                                        .getPublicId()
                        )
                        .orElseThrow();


        PaymentWebhookEvent refreshedEvent =
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                event.getPayloadFingerprint()
                        )
                        .orElseThrow();


        /*
         * Money arriving late must never resurrect inventory.
         */
        assertThat(
                refreshedAttempt.getStatus()
        ).isEqualTo(
                PaymentStatus.PENDING
        );


        assertThat(
                refreshedBooking.getStatus()
        ).isEqualTo(
                BookingStatus.PENDING_PAYMENT
        );


        assertThat(
                refreshedHold.getStatus()
        ).isEqualTo(
                InventoryHoldStatus.ACTIVE
        );


        assertThat(
                refreshedHold.isActiveAt(
                        Instant.now()
                )
        ).isFalse();


        assertThat(
                refreshedEvent.getStatus()
        ).isEqualTo(
                PaymentWebhookEventStatus
                        .REQUIRES_RECONCILIATION
        );


        /*
         * Current processor deliberately stops before provider
         * verification once the authoritative inventory hold is
         * unavailable.
         */
        verifyNoInteractions(
                razorpayPaymentVerifier
        );
    }


    /*
     * ------------------------------------------------------------
     * SEMANTIC DUPLICATE
     * ------------------------------------------------------------
     */

    @Test
    void secondWebhookForAlreadyCompletedPaymentIsIdempotent() {

        String orderId =
                "order_tx_duplicate";

        String paymentId =
                "pay_tx_duplicate";


        LifecycleFixture fixture =
                createPendingLifecycle(
                        orderId,
                        false
                );


        PaymentWebhookEvent firstEvent =
                createCapturedWebhook(
                        paymentId,
                        orderId,
                        ""
                );


        when(
                razorpayPaymentVerifier
                        .fetchCapturedPayment(
                                paymentId
                        )
        ).thenReturn(
                new VerifiedRazorpayPayment(
                        paymentId,
                        orderId,
                        1_000_000L,
                        CURRENCY,
                        "captured",
                        true
                )
        );


        RazorpayWebhookProcessor.ProcessingResult firstResult =
                processor.process(
                        firstEvent.getPayloadFingerprint()
                );


        assertThat(firstResult)
                .isEqualTo(
                        RazorpayWebhookProcessor
                                .ProcessingResult
                                .PROCESSED
                );


        /*
         * Different raw bytes -> different durable fingerprint,
         * but same Razorpay payment/order relationship.
         *
         * This exercises semantic idempotency rather than only
         * byte-identical webhook deduplication.
         */
        PaymentWebhookEvent secondEvent =
                createCapturedWebhook(
                        paymentId,
                        orderId,
                        "\n"
                );


        RazorpayWebhookProcessor.ProcessingResult secondResult =
                processor.process(
                        secondEvent.getPayloadFingerprint()
                );


        assertThat(secondResult)
                .isEqualTo(
                        RazorpayWebhookProcessor
                                .ProcessingResult
                                .PROCESSED
                );


        clearPersistenceContext();


        PaymentAttempt refreshedAttempt =
                paymentAttemptRepository
                        .findByPublicId(
                                fixture.paymentAttempt()
                                        .getPublicId()
                        )
                        .orElseThrow();


        Booking refreshedBooking =
                bookingRepository
                        .findByPublicId(
                                fixture.booking()
                                        .getPublicId()
                        )
                        .orElseThrow();


        InventoryHold refreshedHold =
                inventoryHoldRepository
                        .findByPublicId(
                                fixture.hold()
                                        .getPublicId()
                        )
                        .orElseThrow();


        PaymentWebhookEvent refreshedFirstEvent =
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                firstEvent.getPayloadFingerprint()
                        )
                        .orElseThrow();


        PaymentWebhookEvent refreshedSecondEvent =
                webhookEventRepository
                        .findByProviderAndPayloadFingerprint(
                                PaymentProvider.RAZORPAY,
                                secondEvent.getPayloadFingerprint()
                        )
                        .orElseThrow();


        assertThat(
                refreshedAttempt.getStatus()
        ).isEqualTo(
                PaymentStatus.SUCCEEDED
        );


        assertThat(
                refreshedAttempt.getProviderPaymentId()
        ).isEqualTo(
                paymentId
        );


        assertThat(
                refreshedBooking.getStatus()
        ).isEqualTo(
                BookingStatus.CONFIRMED
        );


        assertThat(
                refreshedHold.getStatus()
        ).isEqualTo(
                InventoryHoldStatus.CONSUMED
        );


        assertThat(
                refreshedFirstEvent.getStatus()
        ).isEqualTo(
                PaymentWebhookEventStatus.PROCESSED
        );


        assertThat(
                refreshedSecondEvent.getStatus()
        ).isEqualTo(
                PaymentWebhookEventStatus.PROCESSED
        );


        /*
         * Provider verification happened only for the first
         * lifecycle transition.
         *
         * The second delivery was recognized from SpatialStay's
         * already-completed authoritative state.
         */
        verify(
                razorpayPaymentVerifier,
                times(1)
        ).fetchCapturedPayment(
                paymentId
        );
    }


    /*
     * ------------------------------------------------------------
     * FIXTURE
     * ------------------------------------------------------------
     */

    private LifecycleFixture createPendingLifecycle(
            String providerOrderId,
            boolean expiredHold
    ) {

        Instant now =
                Instant.now();


        Instant holdExpiresAt =
                expiredHold
                        ? now.minusSeconds(5)
                        : now.plusSeconds(600);


        InventoryHold hold =
                new InventoryHold(
                        customer,
                        hotel,
                        room,
                        checkIn,
                        checkOut,
                        1,
                        1,
                        PRICE_PER_NIGHT,
                        TOTAL_AMOUNT,
                        CURRENCY,
                        holdExpiresAt
                );


        hold =
                inventoryHoldRepository
                        .saveAndFlush(
                                hold
                        );


        Booking booking =
                Booking.pendingPayment(
                        hold
                );


        booking =
                bookingRepository
                        .saveAndFlush(
                                booking
                        );


        PaymentAttempt paymentAttempt =
                PaymentAttempt.create(
                        customer,
                        booking,
                        PaymentProvider.RAZORPAY,
                        booking.getTotalAmount(),
                        booking.getCurrency(),
                        now
                );


        /*
         * Provider checkout creation already happened before a
         * payment.captured webhook can exist.
         *
         * For the expired-hold case, use a future payment-attempt
         * expiry because PaymentAttempt.markPending correctly
         * rejects an already-expired checkout timestamp. The
         * InventoryHold remains the authoritative booking
         * confirmation boundary.
         */
        paymentAttempt.markPending(
                providerOrderId,
                now.plusSeconds(600),
                now
        );


        paymentAttempt =
                paymentAttemptRepository
                        .saveAndFlush(
                                paymentAttempt
                        );


        return new LifecycleFixture(
                hold,
                booking,
                paymentAttempt
        );
    }


    private PaymentWebhookEvent createCapturedWebhook(
            String paymentId,
            String orderId,
            String suffix
    ) {

        String json =
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
                }%s
                """.formatted(
                        paymentId,
                        orderId,
                        suffix == null
                                ? ""
                                : suffix
                );


        String fingerprint =
                WebhookFingerprint.sha256(
                        json.getBytes(
                                StandardCharsets.UTF_8
                        )
                );


        PaymentWebhookEvent event =
                PaymentWebhookEvent.received(
                        PaymentProvider.RAZORPAY,
                        "payment.captured",
                        fingerprint,
                        json,
                        Instant.now()
                );


        return webhookEventRepository
                .saveAndFlush(
                        event
                );
    }


    /*
     * ------------------------------------------------------------
     * DATABASE ENTITIES
     * ------------------------------------------------------------
     */

    private User createCustomer(
            String email
    ) {

        User user =
                new User(
                        email,
                        passwordEncoder.encode(
                                PASSWORD
                        ),
                        "Payment",
                        "Tester"
                );


        return userRepository
                .saveAndFlush(
                        user
                );
    }


    private Hotel createHotel() {

        Hotel value =
                newTestEntity(
                        Hotel.class
                );


        value.setSlug(
                "payment-webhook-test-hotel"
        );

        value.setName(
                "Payment Webhook Test Hotel"
        );

        value.setDescription(
                "Payment webhook transactional integration test hotel"
        );

        value.setCity(
                "Hyderabad"
        );

        value.setState(
                "Telangana"
        );

        value.setCountry(
                "India"
        );

        value.setAddress(
                "Integration Test Address"
        );

        value.setRating(
                new BigDecimal("4.8")
        );

        value.setReviewCount(
                100
        );

        value.setStartingPrice(
                PRICE_PER_NIGHT
        );

        value.setVerified(
                true
        );

        value.setStatus(
                "ACTIVE"
        );


        return hotelRepository
                .saveAndFlush(
                        value
                );
    }


    private Room createRoom(
            Hotel owner
    ) {

        Room value =
                newTestEntity(
                        Room.class
                );


        value.setHotel(
                owner
        );

        value.setSlug(
                "payment-webhook-test-room"
        );

        value.setName(
                "Payment Webhook Test Room"
        );

        value.setDescription(
                "Payment webhook transactional integration test room"
        );

        value.setRoomType(
                "DELUXE"
        );

        value.setMaxGuests(
                2
        );

        value.setTotalUnits(
                1
        );

        value.setBedType(
                "KING"
        );

        value.setRoomSizeSqFt(
                400
        );

        value.setPricePerNight(
                PRICE_PER_NIGHT
        );

        value.setCurrency(
                CURRENCY
        );

        value.setSpatialAvailable(
                false
        );

        value.setStatus(
                "ACTIVE"
        );


        return roomRepository
                .saveAndFlush(
                        value
                );
    }


    private void clearPersistenceContext() {

        entityManager.clear();
    }


    private <T> T newTestEntity(
            Class<T> entityType
    ) {

        try {

            var constructor =
                    entityType
                            .getDeclaredConstructor();


            constructor.setAccessible(
                    true
            );


            return constructor
                    .newInstance();

        } catch (ReflectiveOperationException exception) {

            throw new IllegalStateException(
                    "Unable to create test entity: "
                            + entityType.getSimpleName(),
                    exception
            );
        }
    }


    private record LifecycleFixture(
            InventoryHold hold,
            Booking booking,
            PaymentAttempt paymentAttempt
    ) {
    }
}