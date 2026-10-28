package com.spatialstay.booking;

import com.spatialstay.hotel.Hotel;
import com.spatialstay.room.Room;
import com.spatialstay.user.User;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "bookings",
        indexes = {
                @Index(
                        name = "idx_booking_public_id",
                        columnList = "public_id",
                        unique = true
                ),
                @Index(
                        name = "idx_booking_reference",
                        columnList = "booking_reference",
                        unique = true
                ),
                @Index(
                        name = "idx_booking_user_status",
                        columnList = "user_id,status"
                ),
                @Index(
                        name = "idx_booking_room_stay",
                        columnList = "room_id,check_in,check_out,status"
                )
        },
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_booking_inventory_hold",
                        columnNames = "inventory_hold_id"
                )
        }
)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "public_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private UUID publicId;

    @Column(
            name = "booking_reference",
            nullable = false,
            unique = true,
            updatable = false,
            length = 32
    )
    private String bookingReference;

    /**
     * The authoritative inventory hold from which this booking
     * was created.
     *
     * One hold can create at most one booking.
     */
    @OneToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "inventory_hold_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private InventoryHold inventoryHold;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false,
            updatable = false
    )
    private User user;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "hotel_id",
            nullable = false,
            updatable = false
    )
    private Hotel hotel;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "room_id",
            nullable = false,
            updatable = false
    )
    private Room room;

    @Column(
            name = "check_in",
            nullable = false,
            updatable = false
    )
    private LocalDate checkIn;

    @Column(
            name = "check_out",
            nullable = false,
            updatable = false
    )
    private LocalDate checkOut;

    @Column(
            name = "night_count",
            nullable = false,
            updatable = false
    )
    private Integer nightCount;

    @Column(
            name = "room_quantity",
            nullable = false,
            updatable = false
    )
    private Integer roomQuantity;

    @Column(
            name = "guest_count",
            nullable = false,
            updatable = false
    )
    private Integer guestCount;

    @Column(
            name = "price_per_night",
            nullable = false,
            updatable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal pricePerNight;

    @Column(
            name = "total_amount",
            nullable = false,
            updatable = false,
            precision = 14,
            scale = 2
    )
    private BigDecimal totalAmount;

    @Column(
            name = "currency",
            nullable = false,
            updatable = false,
            length = 3
    )
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private BookingStatus status;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "expired_at")
    private Instant expiredAt;

    protected Booking() {
    }

    /**
     * Creates a PENDING_PAYMENT booking entirely from an authoritative
     * inventory hold.
     *
     * No price, room, dates, customer or quantity values are accepted
     * separately from the caller.
     */
    public static Booking pendingPayment(
            InventoryHold hold
    ) {
        if (hold == null) {
            throw new IllegalArgumentException(
                    "Inventory hold is required."
            );
        }

        requireStay(
                hold.getUser(),
                hold.getHotel(),
                hold.getRoom(),
                hold.getCheckIn(),
                hold.getCheckOut(),
                hold.getUnits(),
                hold.getGuests()
        );

        Booking booking =
                new Booking();

        booking.inventoryHold =
                hold;

        booking.user =
                hold.getUser();

        booking.hotel =
                hold.getHotel();

        booking.room =
                hold.getRoom();

        booking.checkIn =
                hold.getCheckIn();

        booking.checkOut =
                hold.getCheckOut();

        booking.nightCount =
                Math.toIntExact(
                        ChronoUnit.DAYS.between(
                                hold.getCheckIn(),
                                hold.getCheckOut()
                        )
                );

        booking.roomQuantity =
                hold.getUnits();

        booking.guestCount =
                hold.getGuests();

        booking.pricePerNight =
                money(
                        hold.getPricePerNight(),
                        "Price per night"
                );

        booking.totalAmount =
                money(
                        hold.getTotalAmount(),
                        "Total amount"
                );

        booking.currency =
                normalizeCurrency(
                        hold.getCurrency()
                );

        booking.status =
                BookingStatus.PENDING_PAYMENT;

        return booking;
    }

    @PrePersist
    protected void onCreate() {
        if (publicId == null) {
            publicId =
                    UUID.randomUUID();
        }

        if (bookingReference == null) {
            bookingReference =
                    newBookingReference();
        }

        if (status == null) {
            status =
                    BookingStatus.PENDING_PAYMENT;
        }

        Instant now =
                Instant.now();

        createdAt =
                now;

        updatedAt =
                now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt =
                Instant.now();
    }

    void markConfirmed(
            Instant at
    ) {
        requireStatus(
                BookingStatus.PENDING_PAYMENT
        );

        status =
                BookingStatus.CONFIRMED;

        confirmedAt =
                requireInstant(at);
    }

    void markExpired(
            Instant at
    ) {
        requireStatus(
                BookingStatus.PENDING_PAYMENT
        );

        status =
                BookingStatus.EXPIRED;

        expiredAt =
                requireInstant(at);
    }

    void markCancelled(
            Instant at
    ) {
        if (status != BookingStatus.PENDING_PAYMENT
                && status != BookingStatus.CONFIRMED) {
            throw new IllegalStateException(
                    "Booking cannot be cancelled from status "
                            + status
                            + "."
            );
        }

        status =
                BookingStatus.CANCELLED;

        cancelledAt =
                requireInstant(at);
    }

        void applyModification(
                Room proposedRoom,
                LocalDate proposedCheckIn,
                LocalDate proposedCheckOut,
                Integer proposedGuests,
                Integer proposedUnits,
                BigDecimal proposedPricePerNight,
                BigDecimal proposedTotal,
                String proposedCurrency
        ) {
        if (status != BookingStatus.CONFIRMED) {
                throw new IllegalStateException(
                        "Only a confirmed booking can be modified."
                );
        }

        requireStay(
                user,
                hotel,
                proposedRoom,
                proposedCheckIn,
                proposedCheckOut,
                proposedUnits,
                proposedGuests
        );

        if (proposedPricePerNight == null) {
                throw new IllegalArgumentException(
                        "Proposed price per night is required."
                );
        }

        if (proposedTotal == null) {
                throw new IllegalArgumentException(
                        "Proposed total amount is required."
                );
        }

        String normalizedCurrency =
                normalizeCurrency(
                        proposedCurrency
                );

        /*
        * Step 11 currently does not support cross-currency
        * modifications.
        */
        if (!currency.equalsIgnoreCase(
                normalizedCurrency
        )) {
                throw new IllegalStateException(
                        "Modification currency must match the original booking currency."
                );
        }

        long calculatedNights =
                ChronoUnit.DAYS.between(
                        proposedCheckIn,
                        proposedCheckOut
                );

        if (calculatedNights < 1
                || calculatedNights > Integer.MAX_VALUE) {

                throw new IllegalArgumentException(
                        "Proposed stay duration is invalid."
                );
        }

        room =
                proposedRoom;

        checkIn =
                proposedCheckIn;

        checkOut =
                proposedCheckOut;

        nightCount =
                Math.toIntExact(
                        calculatedNights
                );

        roomQuantity =
                proposedUnits;

        guestCount =
                proposedGuests;

        pricePerNight =
                money(
                        proposedPricePerNight,
                        "Proposed price per night"
                );

        totalAmount =
                money(
                        proposedTotal,
                        "Proposed total amount"
                );

        currency =
                normalizedCurrency;
        }

    private static void requireStay(
            User user,
            Hotel hotel,
            Room room,
            LocalDate checkIn,
            LocalDate checkOut,
            int roomQuantity,
            int guestCount
    ) {
        if (user == null) {
            throw new IllegalArgumentException(
                    "User is required."
            );
        }

        if (hotel == null) {
            throw new IllegalArgumentException(
                    "Hotel is required."
            );
        }

        if (room == null) {
            throw new IllegalArgumentException(
                    "Room is required."
            );
        }

        if (checkIn == null) {
            throw new IllegalArgumentException(
                    "Check-in date is required."
            );
        }

        if (checkOut == null) {
            throw new IllegalArgumentException(
                    "Check-out date is required."
            );
        }

        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException(
                    "Check-out must be after check-in."
            );
        }

        if (roomQuantity < 1) {
            throw new IllegalArgumentException(
                    "Room quantity must be at least 1."
            );
        }

        if (guestCount < 1) {
            throw new IllegalArgumentException(
                    "Guest count must be at least 1."
            );
        }
    }

    private static BigDecimal money(
            BigDecimal value,
            String field
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    field + " is required."
            );
        }

        if (value.signum() < 0) {
            throw new IllegalArgumentException(
                    field + " cannot be negative."
            );
        }

        try {
            return value.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    field
                            + " must use at most two decimal places.",
                    exception
            );
        }
    }

    private static String normalizeCurrency(
            String currency
    ) {
        if (currency == null
                || !currency.trim().matches("[A-Za-z]{3}")) {
            throw new IllegalArgumentException(
                    "Currency must be a 3-letter code."
            );
        }

        return currency
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private static Instant requireInstant(
            Instant value
    ) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Timestamp is required."
            );
        }

        return value;
    }

    private void requireStatus(
            BookingStatus expected
    ) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Booking must be "
                            + expected
                            + " but is "
                            + status
                            + "."
            );
        }
    }

    private static String newBookingReference() {
        return "SS-"
                + UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getBookingReference() {
        return bookingReference;
    }

    public InventoryHold getInventoryHold() {
        return inventoryHold;
    }

    public User getUser() {
        return user;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public Room getRoom() {
        return room;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public Integer getNightCount() {
        return nightCount;
    }

    public Integer getRoomQuantity() {
        return roomQuantity;
    }

    public Integer getGuestCount() {
        return guestCount;
    }

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public Instant getExpiredAt() {
        return expiredAt;
    }
}