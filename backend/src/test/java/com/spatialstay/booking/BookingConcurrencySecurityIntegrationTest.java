package com.spatialstay.booking;

import com.spatialstay.hotel.Hotel;
import com.spatialstay.hotel.HotelRepository;
import com.spatialstay.room.Room;
import com.spatialstay.room.RoomAvailabilityResponse;
import com.spatialstay.room.RoomRepository;
import com.spatialstay.room.RoomService;
import com.spatialstay.user.AccountTokenRepository;
import com.spatialstay.user.SpatialStayUserPrincipal;
import com.spatialstay.user.User;
import com.spatialstay.user.UserRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class BookingConcurrencySecurityIntegrationTest {

    private static final String PASSWORD =
            "SpatialStay2026";

    @Autowired
    private InventoryHoldService inventoryHoldService;

    @Autowired
    private InventoryHoldAccessService inventoryHoldAccessService;

    @Autowired
    private InventoryHoldRepository inventoryHoldRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomService roomService;

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

    private Hotel hotel;
    private Room room;

    private User customerA;
    private User customerB;

    private LocalDate checkIn;
    private LocalDate checkOut;

    @BeforeEach
    void setUp() {

        /*
         * Child records must be removed before their parents.
         */
        bookingRepository.deleteAll();
        inventoryHoldRepository.deleteAll();
        accountTokenRepository.deleteAll();
        roomRepository.deleteAll();
        hotelRepository.deleteAll();
        userRepository.deleteAll();

        customerA =
                createCustomer(
                        "booking-a@example.com"
                );

        customerB =
                createCustomer(
                        "booking-b@example.com"
                );

        hotel =
                createHotel();

        room =
                createRoom(
                        hotel,
                        1,
                        new BigDecimal("5000.00")
                );

        checkIn =
                LocalDate.now()
                        .plusDays(10);

        checkOut =
                checkIn.plusDays(2);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    /*
     * ------------------------------------------------------------
     * INVENTORY
     * ------------------------------------------------------------
     */

    @Test
    void activeHoldReducesAvailableInventory() {

        authenticate(customerA);

        InventoryHoldResponse hold =
                inventoryHoldService.createHold(
                        holdRequest(
                                checkIn,
                                checkOut
                        )
                );

        assertThat(hold.status())
                .isEqualTo(
                        InventoryHoldStatus.ACTIVE
                );

        RoomAvailabilityResponse availability =
                availability(
                        checkIn,
                        checkOut
                );

        assertThat(
                availability.totalUnits()
        ).isEqualTo(1);

        assertThat(
                availability.availableUnits()
        ).isZero();

        assertThat(
                availability.available()
        ).isFalse();
    }

    @Test
    void competingCustomersCannotBothHoldLastRoom()
            throws Exception {

        CountDownLatch ready =
                new CountDownLatch(2);

        CountDownLatch start =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {

            Future<Boolean> customerAResult =
                    executor.submit(
                            () ->
                                    tryCreateHoldConcurrently(
                                            customerA,
                                            ready,
                                            start
                                    )
                    );

            Future<Boolean> customerBResult =
                    executor.submit(
                            () ->
                                    tryCreateHoldConcurrently(
                                            customerB,
                                            ready,
                                            start
                                    )
                    );

            /*
             * Both requests are ready before either is allowed
             * to start the authoritative hold transaction.
             */
            ready.await();
            start.countDown();

            boolean first =
                    customerAResult.get();

            boolean second =
                    customerBResult.get();

            /*
             * Exactly one customer must obtain the final unit.
             */
            assertThat(first ^ second)
                    .isTrue();

            assertThat(
                    inventoryHoldRepository.count()
            ).isEqualTo(1);

            InventoryHold onlyHold =
                    inventoryHoldRepository
                            .findAll()
                            .get(0);

            assertThat(
                    onlyHold.getStatus()
            ).isEqualTo(
                    InventoryHoldStatus.ACTIVE
            );

        } finally {
            executor.shutdownNow();
        }
    }

    /*
     * ------------------------------------------------------------
     * IDEMPOTENCY
     * ------------------------------------------------------------
     */

    @Test
    void duplicateBookingRequestReturnsSameBooking() {

        authenticate(customerA);

        InventoryHoldResponse hold =
                inventoryHoldService.createHold(
                        holdRequest(
                                checkIn,
                                checkOut
                        )
                );

        BookingResponse first =
                bookingService.createFromHold(
                        new CreateBookingFromHoldRequest(
                                hold.holdId()
                        )
                );

        BookingResponse second =
                bookingService.createFromHold(
                        new CreateBookingFromHoldRequest(
                                hold.holdId()
                        )
                );

        assertThat(
                second.bookingId()
        ).isEqualTo(
                first.bookingId()
        );

        assertThat(
                second.bookingReference()
        ).isEqualTo(
                first.bookingReference()
        );

        assertThat(
                bookingRepository.count()
        ).isEqualTo(1);

        InventoryHold persistedHold =
                inventoryHoldRepository
                        .findByPublicId(
                                hold.holdId()
                        )
                        .orElseThrow();

        /*
         * Step 9 contract:
         *
         * PENDING_PAYMENT does not consume the hold.
         * The ACTIVE hold protects inventory until payment
         * confirmation or expiration.
         */
        assertThat(
                persistedHold.getStatus()
        ).isEqualTo(
                InventoryHoldStatus.ACTIVE
        );

        assertThat(
                first.status()
        ).isEqualTo(
                BookingStatus.PENDING_PAYMENT
        );
    }

    /*
     * ------------------------------------------------------------
     * OWNERSHIP / SECURITY
     * ------------------------------------------------------------
     */

    @Test
    void anotherCustomerCannotReadOwnedHold() {

        UUID holdId =
                createHoldForCustomerA();

        authenticate(customerB);

        assertThatThrownBy(
                () ->
                        inventoryHoldAccessService
                                .getOwnedHold(
                                        holdId
                                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "not accessible"
                );
    }

    @Test
    void anotherCustomerCannotCreateBookingFromOwnedHold() {

        UUID holdId =
                createHoldForCustomerA();

        authenticate(customerB);

        assertThatThrownBy(
                () ->
                        bookingService
                                .createFromHold(
                                        new CreateBookingFromHoldRequest(
                                                holdId
                                        )
                                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "not accessible"
                );

        assertThat(
                bookingRepository.count()
        ).isZero();
    }

    @Test
    void anotherCustomerCannotReadOwnedBooking() {

        authenticate(customerA);

        InventoryHoldResponse hold =
                inventoryHoldService.createHold(
                        holdRequest(
                                checkIn,
                                checkOut
                        )
                );

        BookingResponse booking =
                bookingService.createFromHold(
                        new CreateBookingFromHoldRequest(
                                hold.holdId()
                        )
                );

        authenticate(customerB);

        assertThatThrownBy(
                () ->
                        bookingService
                                .getOwnedBooking(
                                        booking.bookingId()
                                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "not accessible"
                );
    }

    @Test
    void randomHoldIdentifierCannotCreateBooking() {

        authenticate(customerA);

        assertThatThrownBy(
                () ->
                        bookingService
                                .createFromHold(
                                        new CreateBookingFromHoldRequest(
                                                UUID.randomUUID()
                                        )
                                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );

        assertThat(
                bookingRepository.count()
        ).isZero();
    }

    /*
     * ------------------------------------------------------------
     * EXPIRATION
     * ------------------------------------------------------------
     */

    @Test
    void expiredHoldCannotCreateNewBooking() {

        InventoryHold expiredHold =
                createExpiredHold(
                        customerA,
                        checkIn,
                        checkOut
                );

        authenticate(customerA);

        assertThatThrownBy(
                () ->
                        bookingService
                                .createFromHold(
                                        new CreateBookingFromHoldRequest(
                                                expiredHold.getPublicId()
                                        )
                                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        "expired"
                );

        assertThat(
                bookingRepository.count()
        ).isZero();
    }

    @Test
    void retryNormalizesExpiredPendingBooking() {

        InventoryHold expiredHold =
                createExpiredHold(
                        customerA,
                        checkIn,
                        checkOut
                );

        Booking pending =
                Booking.pendingPayment(
                        expiredHold
                );

        bookingRepository.saveAndFlush(
                pending
        );

        authenticate(customerA);

        BookingResponse response =
                bookingService
                        .createFromHold(
                                new CreateBookingFromHoldRequest(
                                        expiredHold.getPublicId()
                                )
                        );

        assertThat(
                response.status()
        ).isEqualTo(
                BookingStatus.EXPIRED
        );

        InventoryHold refreshedHold =
                inventoryHoldRepository
                        .findByPublicId(
                                expiredHold.getPublicId()
                        )
                        .orElseThrow();

        Booking refreshedBooking =
                bookingRepository
                        .findByPublicId(
                                response.bookingId()
                        )
                        .orElseThrow();

        assertThat(
                refreshedHold.getStatus()
        ).isEqualTo(
                InventoryHoldStatus.EXPIRED
        );

        assertThat(
                refreshedBooking.getStatus()
        ).isEqualTo(
                BookingStatus.EXPIRED
        );

        assertThat(
                bookingRepository.count()
        ).isEqualTo(1);
    }

    @Test
    void expiredHoldDoesNotReduceAvailability() {

        createExpiredHold(
                customerA,
                checkIn,
                checkOut
        );

        RoomAvailabilityResponse availability =
                availability(
                        checkIn,
                        checkOut
                );

        assertThat(
                availability.availableUnits()
        ).isEqualTo(1);

        assertThat(
                availability.available()
        ).isTrue();
    }

    /*
     * ------------------------------------------------------------
     * AUTHORITATIVE PRICING
     * ------------------------------------------------------------
     */

    @Test
    void holdAndBookingUseBackendAuthoritativePricing() {

        authenticate(customerA);

        InventoryHoldResponse hold =
                inventoryHoldService.createHold(
                        holdRequest(
                                checkIn,
                                checkOut
                        )
                );

        /*
         * 5000 × 2 nights × 1 unit
         */
        assertThat(
                hold.pricePerNight()
        ).isEqualByComparingTo(
                "5000.00"
        );

        assertThat(
                hold.totalAmount()
        ).isEqualByComparingTo(
                "10000.00"
        );

        assertThat(
                hold.currency()
        ).isEqualTo(
                "INR"
        );

        BookingResponse booking =
                bookingService.createFromHold(
                        new CreateBookingFromHoldRequest(
                                hold.holdId()
                        )
                );

        assertThat(
                booking.pricePerNight()
        ).isEqualByComparingTo(
                hold.pricePerNight()
        );

        assertThat(
                booking.totalAmount()
        ).isEqualByComparingTo(
                hold.totalAmount()
        );

        assertThat(
                booking.currency()
        ).isEqualTo(
                hold.currency()
        );
    }

    /*
     * ------------------------------------------------------------
     * DATE OVERLAP CONTRACT
     * ------------------------------------------------------------
     */

    @Test
    void sameDayCheckoutAndNextCheckInDoNotOverlap() {

        LocalDate firstCheckIn =
                LocalDate.now()
                        .plusDays(20);

        LocalDate firstCheckOut =
                firstCheckIn.plusDays(2);

        /*
         * Create a confirmed stay ending on firstCheckOut.
         */
        InventoryHold firstHold =
                new InventoryHold(
                        customerA,
                        hotel,
                        room,
                        firstCheckIn,
                        firstCheckOut,
                        1,
                        1,
                        new BigDecimal(
                                "5000.00"
                        ),
                        new BigDecimal(
                                "10000.00"
                        ),
                        "INR",
                        Instant.now()
                                .plusSeconds(600)
                );

        inventoryHoldRepository
                .saveAndFlush(
                        firstHold
                );

        Booking confirmed =
                Booking.pendingPayment(
                        firstHold
                );

        confirmed.markConfirmed(
                Instant.now()
        );

        bookingRepository
                .saveAndFlush(
                        confirmed
                );

        /*
         * The hold must no longer count separately once the
         * booking represents the confirmed stay.
         */
        firstHold.markConsumed(
                Instant.now()
        );

        inventoryHoldRepository
                .saveAndFlush(
                        firstHold
                );

        LocalDate nextCheckIn =
                firstCheckOut;

        LocalDate nextCheckOut =
                nextCheckIn.plusDays(2);

        RoomAvailabilityResponse availability =
                availability(
                        nextCheckIn,
                        nextCheckOut
                );

        assertThat(
                availability.availableUnits()
        ).isEqualTo(1);

        assertThat(
                availability.available()
        ).isTrue();
    }

    /*
     * ------------------------------------------------------------
     * HELPERS
     * ------------------------------------------------------------
     */

    private UUID createHoldForCustomerA() {

        authenticate(customerA);

        return inventoryHoldService
                .createHold(
                        holdRequest(
                                checkIn,
                                checkOut
                        )
                )
                .holdId();
    }

    private CreateInventoryHoldRequest holdRequest(
            LocalDate from,
            LocalDate to
    ) {
        return new CreateInventoryHoldRequest(
                hotel.getSlug(),
                room.getSlug(),
                from,
                to,
                1,
                1
        );
    }

    private RoomAvailabilityResponse availability(
            LocalDate from,
            LocalDate to
    ) {
        return roomService
                .getAvailability(
                        hotel.getSlug(),
                        from,
                        to,
                        1
                )
                .stream()
                .filter(
                        item ->
                                item.roomSlug()
                                        .equals(
                                                room.getSlug()
                                        )
                )
                .findFirst()
                .orElseThrow();
    }

    private boolean tryCreateHoldConcurrently(
            User user,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {

        authenticate(user);

        try {
            ready.countDown();
            start.await();

            inventoryHoldService
                    .createHold(
                            holdRequest(
                                    checkIn,
                                    checkOut
                            )
                    );

            return true;

        } catch (IllegalStateException exception) {

            /*
             * Expected for the customer who loses the race
             * for the final unit.
             */
            return false;

        } finally {
            SecurityContextHolder
                    .clearContext();
        }
    }

    private InventoryHold createExpiredHold(
            User user,
            LocalDate from,
            LocalDate to
    ) {

        long nights =
                java.time.temporal.ChronoUnit
                        .DAYS
                        .between(
                                from,
                                to
                        );

        BigDecimal total =
                room.getPricePerNight()
                        .multiply(
                                BigDecimal.valueOf(
                                        nights
                                )
                        );

        InventoryHold hold =
                new InventoryHold(
                        user,
                        hotel,
                        room,
                        from,
                        to,
                        1,
                        1,
                        room.getPricePerNight(),
                        total,
                        room.getCurrency(),
                        Instant.now()
                                .minusSeconds(5)
                );

        return inventoryHoldRepository
                .saveAndFlush(
                        hold
                );
    }

    private User createCustomer(
            String email
    ) {

        User user =
                new User(
                        email,
                        passwordEncoder.encode(
                                PASSWORD
                        ),
                        "Booking",
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
                "step-nine-test-hotel"
        );

        value.setName(
                "Step Nine Test Hotel"
        );

        value.setDescription(
                "Booking integration test hotel"
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

        value.setReviewCount(100);

        value.setStartingPrice(
                new BigDecimal("5000.00")
        );

        value.setVerified(true);

        value.setStatus(
                "ACTIVE"
        );

        return hotelRepository
                .saveAndFlush(
                        value
                );
    }

    private Room createRoom(
            Hotel owner,
            int totalUnits,
            BigDecimal price
    ) {

        Room value =
            newTestEntity(
                    Room.class
            );

        value.setHotel(owner);

        value.setSlug(
                "step-nine-test-room"
        );

        value.setName(
                "Step Nine Test Room"
        );

        value.setDescription(
                "Booking integration test room"
        );

        value.setRoomType(
                "DELUXE"
        );

        value.setMaxGuests(2);

        value.setTotalUnits(
                totalUnits
        );

        value.setBedType(
                "KING"
        );

        value.setRoomSizeSqFt(400);

        value.setPricePerNight(
                price
        );

        value.setCurrency(
                "INR"
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

    private void authenticate(
            User user
    ) {

        SpatialStayUserPrincipal principal =
                new SpatialStayUserPrincipal(
                        user
                );

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        principal.getAuthorities()
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(
                        authentication
                );
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
}