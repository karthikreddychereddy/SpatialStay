package com.spatialstay.room;

import com.spatialstay.booking.BookingRepository;
import com.spatialstay.booking.InventoryHoldRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomMapper roomMapper;
    private final BookingRepository bookingRepository;
    private final InventoryHoldRepository inventoryHoldRepository;

    public RoomService(
            RoomRepository roomRepository,
            RoomMapper roomMapper,
            BookingRepository bookingRepository,
            InventoryHoldRepository inventoryHoldRepository
    ) {
        this.roomRepository = roomRepository;
        this.roomMapper = roomMapper;
        this.bookingRepository = bookingRepository;
        this.inventoryHoldRepository = inventoryHoldRepository;
    }

    public List<RoomResponse> getRoomsByHotel(
            String hotelSlug
    ) {
        return roomRepository
                .findByHotelSlug(hotelSlug)
                .stream()
                .map(roomMapper::toResponse)
                .toList();
    }

    public Optional<RoomResponse> getRoomByHotelAndSlug(
            String hotelSlug,
            String roomSlug
    ) {
        return roomRepository
                .findByHotelSlugAndSlug(
                        hotelSlug,
                        roomSlug
                )
                .map(roomMapper::toResponse);
    }

    public List<RoomAvailabilityResponse> getAvailability(
            String hotelSlug,
            LocalDate checkIn,
            LocalDate checkOut,
            Integer guests
    ) {
        validateStay(
                checkIn,
                checkOut,
                guests
        );

        long nights =
                ChronoUnit.DAYS.between(
                        checkIn,
                        checkOut
                );

        /*
         * Use one consistent instant for the entire availability calculation.
         *
         * This prevents different rooms in the same response from being
         * evaluated against slightly different expiration timestamps.
         */
        Instant now = Instant.now();

        return roomRepository
                .findByHotelSlug(hotelSlug)
                .stream()
                .filter(this::isActive)
                .filter(
                        room ->
                                room.getMaxGuests() >= guests
                )
                .map(
                        room ->
                                toAvailabilityResponse(
                                        room,
                                        checkIn,
                                        checkOut,
                                        guests,
                                        nights,
                                        now
                                )
                )
                .toList();
    }

    private RoomAvailabilityResponse toAvailabilityResponse(
            Room room,
            LocalDate checkIn,
            LocalDate checkOut,
            Integer guests,
            long nights,
            Instant now
    ) {
        int totalUnits =
                room.getTotalUnits() != null
                        ? Math.max(room.getTotalUnits(), 0)
                        : 0;

        long confirmedUnits =
                safeCount(
                        bookingRepository
                                .sumConfirmedUnitsForStay(
                                        room.getId(),
                                        checkIn,
                                        checkOut
                                )
                );

        long heldUnits =
                safeCount(
                        inventoryHoldRepository
                                .sumActiveUnitsForStay(
                                        room.getId(),
                                        checkIn,
                                        checkOut,
                                        now
                                )
                );

        /*
         * Use long arithmetic while subtracting persisted quantities.
         *
         * This avoids integer overflow if corrupted or unexpectedly large
         * persisted values ever exist.
         */
        long remainingUnits =
                (long) totalUnits
                        - confirmedUnits
                        - heldUnits;

        int availableUnits =
                remainingUnits <= 0
                        ? 0
                        : remainingUnits >= Integer.MAX_VALUE
                                ? Integer.MAX_VALUE
                                : (int) remainingUnits;

        boolean available =
                availableUnits > 0;

        BigDecimal estimatedStayTotal =
                room.getPricePerNight()
                        .multiply(
                                BigDecimal.valueOf(nights)
                        );

        return new RoomAvailabilityResponse(
                room.getId(),
                room.getSlug(),
                room.getName(),
                checkIn,
                checkOut,
                nights,
                guests,
                room.getMaxGuests(),
                totalUnits,
                availableUnits,
                available,
                room.getPricePerNight(),
                estimatedStayTotal,
                room.getCurrency()
        );
    }

    private long safeCount(
            Long value
    ) {
        if (value == null || value < 0) {
            return 0L;
        }

        return value;
    }

    private void validateStay(
            LocalDate checkIn,
            LocalDate checkOut,
            Integer guests
    ) {
        if (checkIn == null) {
            throw new IllegalArgumentException(
                    "checkIn is required."
            );
        }

        if (checkOut == null) {
            throw new IllegalArgumentException(
                    "checkOut is required."
            );
        }

        if (guests == null) {
            throw new IllegalArgumentException(
                    "guests is required."
            );
        }

        LocalDate today =
                LocalDate.now();

        if (checkIn.isBefore(today)) {
            throw new IllegalArgumentException(
                    "checkIn cannot be before today."
            );
        }

        if (!checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException(
                    "checkOut must be after checkIn."
            );
        }

        if (guests < 1) {
            throw new IllegalArgumentException(
                    "guests must be at least 1."
            );
        }
    }

    private boolean isActive(
            Room room
    ) {
        return "ACTIVE".equalsIgnoreCase(
                room.getStatus()
        );
    }
}