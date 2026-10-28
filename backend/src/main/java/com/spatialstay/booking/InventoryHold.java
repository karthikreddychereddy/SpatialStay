package com.spatialstay.booking;

import com.spatialstay.hotel.Hotel;
import com.spatialstay.room.Room;
import com.spatialstay.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(
        name = "inventory_holds",
        indexes = {
                @Index(
                        name = "idx_inventory_hold_public_id",
                        columnList = "public_id"
                ),
                @Index(
                        name = "idx_inventory_hold_user",
                        columnList = "user_id"
                ),
                @Index(
                        name = "idx_inventory_hold_room_dates",
                        columnList = "room_id, check_in, check_out"
                ),
                @Index(
                        name = "idx_inventory_hold_status_expiry",
                        columnList = "status, expires_at"
                )
        }
)
public class InventoryHold {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Public identifier exposed outside the persistence layer.
     *
     * Internal sequential database IDs must never be used as
     * customer-facing hold identifiers.
     */
    @Column(
            name = "public_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private UUID publicId;

    /**
     * Authenticated customer who owns this hold.
     */
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "user_id",
            nullable = false
    )
    private User user;

    /**
     * Hotel snapshot relationship for the held room.
     */
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "hotel_id",
            nullable = false
    )
    private Hotel hotel;

    /**
     * Room inventory being temporarily reserved.
     */
    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "room_id",
            nullable = false
    )
    private Room room;

    @Column(
            name = "check_in",
            nullable = false
    )
    private LocalDate checkIn;

    @Column(
            name = "check_out",
            nullable = false
    )
    private LocalDate checkOut;

    /**
     * Number of nights calculated from check-in/check-out.
     */
    @Column(
            name = "nights",
            nullable = false
    )
    private Integer nights;

    @Column(
            name = "guests",
            nullable = false
    )
    private Integer guests;

    /**
     * Number of physical room units reserved by this hold.
     */
    @Column(
            name = "units",
            nullable = false
    )
    private Integer units;

    /**
     * Authoritative price snapshot captured when the hold is created.
     *
     * Later frontend requests must not be allowed to overwrite this value.
     */
    @Column(
            name = "price_per_night",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal pricePerNight;

    /**
     * Authoritative total captured at hold creation.
     */
    @Column(
            name = "total_amount",
            nullable = false,
            precision = 12,
            scale = 2
    )
    private BigDecimal totalAmount;

    @Column(
            name = "currency",
            nullable = false,
            length = 3
    )
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 32
    )
    private InventoryHoldStatus status;

    /**
     * The hold affects inventory only while:
     *
     * status == ACTIVE
     * AND
     * expiresAt > current time.
     */
    @Column(
            name = "expires_at",
            nullable = false
    )
    private Instant expiresAt;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "consumed_at"
    )
    private Instant consumedAt;

    @Column(
            name = "released_at"
    )
    private Instant releasedAt;

    protected InventoryHold() {
    }

    public InventoryHold(
            User user,
            Hotel hotel,
            Room room,
            LocalDate checkIn,
            LocalDate checkOut,
            Integer guests,
            Integer units,
            BigDecimal pricePerNight,
            BigDecimal totalAmount,
            String currency,
            Instant expiresAt
    ) {
        this.user = requireValue(
                user,
                "User is required."
        );

        this.hotel = requireValue(
                hotel,
                "Hotel is required."
        );

        this.room = requireValue(
                room,
                "Room is required."
        );

        this.checkIn = requireValue(
                checkIn,
                "Check-in date is required."
        );

        this.checkOut = requireValue(
                checkOut,
                "Check-out date is required."
        );

        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException(
                    "Check-out date must be after check-in date."
            );
        }

        long calculatedNights =
                ChronoUnit.DAYS.between(
                        checkIn,
                        checkOut
                );

        if (calculatedNights > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Stay duration is too large."
            );
        }

        this.nights = (int) calculatedNights;

        this.guests = requirePositive(
                guests,
                "Guests must be greater than zero."
        );

        this.units = requirePositive(
                units,
                "Units must be greater than zero."
        );

        this.pricePerNight = requireNonNegativeMoney(
                pricePerNight,
                "Price per night must not be negative."
        );

        this.totalAmount = requireNonNegativeMoney(
                totalAmount,
                "Total amount must not be negative."
        );

        this.currency = requireCurrency(currency);

        this.expiresAt = requireValue(
                expiresAt,
                "Expiration time is required."
        );

        this.status = InventoryHoldStatus.ACTIVE;
    }

    @PrePersist
    void prePersist() {
        if (publicId == null) {
            publicId = UUID.randomUUID();
        }

        if (createdAt == null) {
            createdAt = Instant.now();
        }

        if (status == null) {
            status = InventoryHoldStatus.ACTIVE;
        }
    }

    public boolean isActiveAt(Instant now) {
        if (now == null) {
            return false;
        }

        return status == InventoryHoldStatus.ACTIVE
                && expiresAt != null
                && expiresAt.isAfter(now);
    }

    /**
     * Marks an ACTIVE hold as expired.
     *
     * Lifecycle transitions are package-private so controllers, AI tools
     * and external callers cannot arbitrarily manipulate hold state.
     */
    void markExpired(
            Instant at
    ) {
        if (status != InventoryHoldStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only an ACTIVE inventory hold can expire."
            );
        }

        if (at == null) {
            throw new IllegalArgumentException(
                    "Expiration timestamp is required."
            );
        }

        status = InventoryHoldStatus.EXPIRED;
    }

    /**
     * Marks an ACTIVE hold as consumed.
     *
     * Step 9.7 will use this when a valid hold is converted into a
     * PENDING_PAYMENT booking.
     */
    void markConsumed(
            Instant at
    ) {
        if (status != InventoryHoldStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only an ACTIVE inventory hold can be consumed."
            );
        }

        if (at == null) {
            throw new IllegalArgumentException(
                    "Consumption timestamp is required."
            );
        }

        if (expiresAt == null || !expiresAt.isAfter(at)) {
            throw new IllegalStateException(
                    "Expired inventory hold cannot be consumed."
            );
        }

        status = InventoryHoldStatus.CONSUMED;
        consumedAt = at;
    }

    /**
     * Intentionally releases an ACTIVE hold.
     *
     * This will be used when a customer abandons a hold or a later workflow
     * explicitly releases inventory before natural expiration.
     */
    void markReleased(
            Instant at
    ) {
        if (status != InventoryHoldStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only an ACTIVE inventory hold can be released."
            );
        }

        if (at == null) {
            throw new IllegalArgumentException(
                    "Release timestamp is required."
            );
        }

        status = InventoryHoldStatus.RELEASED;
        releasedAt = at;
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
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

    public Integer getNights() {
        return nights;
    }

    public Integer getGuests() {
        return guests;
    }

    public Integer getUnits() {
        return units;
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

    public InventoryHoldStatus getStatus() {
        return status;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConsumedAt() {
        return consumedAt;
    }

    public Instant getReleasedAt() {
        return releasedAt;
    }

    private static <T> T requireValue(
            T value,
            String message
    ) {
        if (value == null) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    private static Integer requirePositive(
            Integer value,
            String message
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    private static BigDecimal requireNonNegativeMoney(
            BigDecimal value,
            String message
    ) {
        if (
                value == null ||
                value.compareTo(BigDecimal.ZERO) < 0
        ) {
            throw new IllegalArgumentException(message);
        }

        return value;
    }

    private static String requireCurrency(
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Currency is required."
            );
        }

        String normalized =
                value.trim().toUpperCase();

        if (normalized.length() != 3) {
            throw new IllegalArgumentException(
                    "Currency must be a three-letter code."
            );
        }

        return normalized;
    }
}