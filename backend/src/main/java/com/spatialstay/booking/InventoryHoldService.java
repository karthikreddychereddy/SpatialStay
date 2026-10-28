package com.spatialstay.booking;

import com.spatialstay.room.Room;
import com.spatialstay.room.RoomRepository;
import com.spatialstay.user.CurrentUser;
import com.spatialstay.user.User;
import com.spatialstay.user.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Service
public class InventoryHoldService {

    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final InventoryHoldRepository inventoryHoldRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final Duration holdDuration;

    public InventoryHoldService(
            RoomRepository roomRepository,
            BookingRepository bookingRepository,
            InventoryHoldRepository inventoryHoldRepository,
            UserRepository userRepository,
            CurrentUser currentUser,
            @Value("${spatialstay.booking.hold-duration:PT10M}")
            Duration holdDuration
    ) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.inventoryHoldRepository = inventoryHoldRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;

        if (holdDuration == null
                || holdDuration.isZero()
                || holdDuration.isNegative()) {
            throw new IllegalArgumentException(
                    "Inventory hold duration must be greater than zero."
            );
        }

        this.holdDuration = holdDuration;
    }

    /**
     * Creates an inventory hold for the currently authenticated customer.
     *
     * The room row is pessimistically locked before inventory is checked.
     * This prevents competing transactions from reserving the same final
     * room unit.
     */
    @Transactional
    public InventoryHoldResponse createHold(
            CreateInventoryHoldRequest request
    ) {
        validateRequest(request);

        UUID userPublicId = currentUser.publicId();

        User user = userRepository
                .findByPublicId(userPublicId)
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Authenticated user does not exist."
                        )
                );

        /*
         * Concurrency boundary.
         *
         * PostgreSQL holds the room-row write lock until this transaction
         * commits or rolls back.
         */
        Room room = roomRepository
                .findByHotelSlugAndSlugForUpdate(
                        request.hotelSlug().trim(),
                        request.roomSlug().trim()
                )
                .orElseThrow(
                        () -> new IllegalArgumentException(
                                "Room does not exist."
                        )
                );

        validateRoom(
                room,
                request
        );

        Instant now = Instant.now();

        int totalUnits =
                room.getTotalUnits() != null
                        ? Math.max(room.getTotalUnits(), 0)
                        : 0;

        long confirmedUnits = safeCount(
                bookingRepository.sumConfirmedUnitsForStay(
                        room.getId(),
                        request.checkIn(),
                        request.checkOut()
                )
        );

        long heldUnits = safeCount(
                inventoryHoldRepository.sumActiveUnitsForStay(
                        room.getId(),
                        request.checkIn(),
                        request.checkOut(),
                        now
                )
        );

        long availableUnits =
                (long) totalUnits
                        - confirmedUnits
                        - heldUnits;

        if (availableUnits < 1
                || request.units() > availableUnits) {
            throw new IllegalStateException(
                    "Requested room inventory is no longer available."
            );
        }

        long nights = ChronoUnit.DAYS.between(
                request.checkIn(),
                request.checkOut()
        );

        BigDecimal pricePerNight =
                authoritativePrice(room);

        BigDecimal totalAmount = pricePerNight
                .multiply(
                        BigDecimal.valueOf(nights)
                )
                .multiply(
                        BigDecimal.valueOf(
                                request.units()
                        )
                )
                .setScale(
                        2,
                        RoundingMode.UNNECESSARY
                );

        String currency =
                authoritativeCurrency(room);

        /*
         * Hold duration comes from backend configuration.
         *
         * Default:
         * spatialstay.booking.hold-duration=PT10M
         */
        Instant expiresAt =
                now.plus(holdDuration);

        InventoryHold hold =
                new InventoryHold(
                        user,
                        room.getHotel(),
                        room,
                        request.checkIn(),
                        request.checkOut(),
                        request.guests(),
                        request.units(),
                        pricePerNight,
                        totalAmount,
                        currency,
                        expiresAt
                );

        InventoryHold saved =
                inventoryHoldRepository.saveAndFlush(
                        hold
                );

        return toResponse(saved);
    }

    private void validateRequest(
            CreateInventoryHoldRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Hold request is required."
            );
        }

        requireText(
                request.hotelSlug(),
                "Hotel slug is required."
        );

        requireText(
                request.roomSlug(),
                "Room slug is required."
        );

        LocalDate checkIn =
                request.checkIn();

        LocalDate checkOut =
                request.checkOut();

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

        LocalDate today =
                LocalDate.now();

        if (checkIn.isBefore(today)) {
            throw new IllegalArgumentException(
                    "Check-in cannot be before today."
            );
        }

        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException(
                    "Check-out must be after check-in."
            );
        }

        if (request.guests() == null
                || request.guests() < 1) {
            throw new IllegalArgumentException(
                    "Guests must be at least 1."
            );
        }

        if (request.units() == null
                || request.units() < 1) {
            throw new IllegalArgumentException(
                    "Units must be at least 1."
            );
        }
    }

    private void validateRoom(
            Room room,
            CreateInventoryHoldRequest request
    ) {
        if (room.getStatus() == null
                || !"ACTIVE".equalsIgnoreCase(
                        room.getStatus()
                )) {
            throw new IllegalStateException(
                    "Room is not currently active."
            );
        }

        if (room.getHotel() == null
                || room.getHotel().getSlug() == null
                || !room.getHotel()
                        .getSlug()
                        .equalsIgnoreCase(
                                request.hotelSlug().trim()
                        )) {
            throw new IllegalStateException(
                    "Room does not belong to the selected hotel."
            );
        }

        Integer maxGuests =
                room.getMaxGuests();

        if (maxGuests == null
                || maxGuests < 1) {
            throw new IllegalStateException(
                    "Room capacity is not configured correctly."
            );
        }

        long totalGuestCapacity =
                (long) maxGuests
                        * request.units();

        if (request.guests()
                > totalGuestCapacity) {
            throw new IllegalArgumentException(
                    "Guest count exceeds the capacity of the requested room units."
            );
        }
    }

    private BigDecimal authoritativePrice(
            Room room
    ) {
        BigDecimal value =
                room.getPricePerNight();

        if (value == null
                || value.signum() < 0) {
            throw new IllegalStateException(
                    "Room price is not configured correctly."
            );
        }

        try {
            return value.setScale(
                    2,
                    RoundingMode.UNNECESSARY
            );
        } catch (ArithmeticException exception) {
            throw new IllegalStateException(
                    "Room price must use at most two decimal places.",
                    exception
            );
        }
    }

    private String authoritativeCurrency(
            Room room
    ) {
        String currency =
                room.getCurrency();

        if (currency == null
                || !currency
                        .trim()
                        .matches("[A-Za-z]{3}")) {
            throw new IllegalStateException(
                    "Room currency is not configured correctly."
            );
        }

        return currency
                .trim()
                .toUpperCase(Locale.ROOT);
    }

    private long safeCount(
            Long value
    ) {
        if (value == null
                || value < 0) {
            return 0L;
        }

        return value;
    }

    private void requireText(
            String value,
            String message
    ) {
        if (value == null
                || value.isBlank()) {
            throw new IllegalArgumentException(
                    message
            );
        }
    }

    private InventoryHoldResponse toResponse(
            InventoryHold hold
    ) {
        return new InventoryHoldResponse(
                hold.getPublicId(),
                hold.getHotel().getSlug(),
                hold.getHotel().getName(),
                hold.getRoom().getSlug(),
                hold.getRoom().getName(),
                hold.getCheckIn(),
                hold.getCheckOut(),
                hold.getNights(),
                hold.getGuests(),
                hold.getUnits(),
                hold.getPricePerNight(),
                hold.getTotalAmount(),
                hold.getCurrency(),
                hold.getStatus(),
                hold.getExpiresAt(),
                hold.getCreatedAt()
        );
    }
}