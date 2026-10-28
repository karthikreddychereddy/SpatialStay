package com.spatialstay.booking;

import com.spatialstay.room.Room;
import com.spatialstay.room.RoomRepository;
import com.spatialstay.user.CurrentUser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.Locale;
import java.util.UUID;

@Service
public class BookingModificationService {

    private static final Duration MANAGEMENT_COOLDOWN =
            Duration.ofMinutes(30);

    private static final EnumSet<BookingModificationStatus>
            ACTIVE_MODIFICATION_STATUSES =
            EnumSet.of(
                    BookingModificationStatus.PENDING,
                    BookingModificationStatus.AWAITING_PAYMENT,
                    BookingModificationStatus.READY_TO_APPLY,
                    BookingModificationStatus.REQUIRES_RECONCILIATION
            );

    private final BookingRepository bookingRepository;
    private final BookingModificationRepository
            modificationRepository;
    private final RoomRepository roomRepository;
    private final InventoryHoldRepository
            inventoryHoldRepository;
    private final CurrentUser currentUser;
    private final Duration holdDuration;

    public BookingModificationService(
            BookingRepository bookingRepository,
            BookingModificationRepository modificationRepository,
            RoomRepository roomRepository,
            InventoryHoldRepository inventoryHoldRepository,
            CurrentUser currentUser,
            @Value("${spatialstay.booking.hold-duration:PT10M}")
            Duration holdDuration
    ) {
        this.bookingRepository =
                bookingRepository;

        this.modificationRepository =
                modificationRepository;

        this.roomRepository =
                roomRepository;

        this.inventoryHoldRepository =
                inventoryHoldRepository;

        this.currentUser =
                currentUser;

        if (holdDuration == null
                || holdDuration.isZero()
                || holdDuration.isNegative()) {

            throw new IllegalArgumentException(
                    "Modification hold duration must be greater than zero."
            );
        }

        this.holdDuration =
                holdDuration;
    }

    /*
     * Creates a durable modification proposal and reserves the proposed
     * inventory.
     *
     * The original confirmed Booking is deliberately NOT changed here.
     */
    @Transactional
    public BookingModificationResponse createModification(
            UUID bookingPublicId,
            BookingModificationRequest request
    ) {
        validateRequest(
                bookingPublicId,
                request
        );

        UUID userPublicId =
                currentUser.publicId();

        /*
         * Lock the booking so cancellation/another modification cannot
         * change its lifecycle while this proposal is being created.
         */
        Booking booking =
                bookingRepository
                        .findOwnedByPublicIdForUpdate(
                                bookingPublicId,
                                userPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Booking does not exist or is not accessible."
                                )
                        );

        validateBookingCanBeModified(
                booking
        );

        if (modificationRepository
                .existsByBookingPublicIdAndStatusIn(
                        bookingPublicId,
                        ACTIVE_MODIFICATION_STATUSES
                )) {

            throw new IllegalStateException(
                    "This booking already has an active modification."
            );
        }

        if (booking.getHotel() == null
                || booking.getHotel().getSlug() == null) {

            throw new IllegalStateException(
                    "Booking hotel relationship is invalid."
            );
        }

        /*
         * Room row is the same inventory serialization boundary used
         * by the normal Step 9 hold flow.
         */
        Room proposedRoom =
                roomRepository
                        .findByHotelSlugAndSlugForUpdate(
                                booking.getHotel()
                                        .getSlug(),
                                request.roomSlug().trim()
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Requested room does not exist in this hotel."
                                )
                        );

        validateRoom(
                proposedRoom,
                request
        );

        Instant now =
                Instant.now();

        int totalUnits =
                proposedRoom.getTotalUnits() == null
                        ? 0
                        : Math.max(
                                proposedRoom.getTotalUnits(),
                                0
                        );

        /*
         * Exclude the booking being modified.
         *
         * Its original inventory must not block its own replacement
         * proposal.
         */
        long confirmedUnits =
                safeCount(
                        bookingRepository
                                .sumConfirmedUnitsForStayExcludingBooking(
                                        proposedRoom.getId(),
                                        request.checkIn(),
                                        request.checkOut(),
                                        booking.getPublicId()
                                )
                );

        /*
         * All currently active holds still count, including checkout
         * flows and other modification proposals.
         */
        long heldUnits =
                safeCount(
                        inventoryHoldRepository
                                .sumActiveUnitsForStay(
                                        proposedRoom.getId(),
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
                || request.units()
                > availableUnits) {

            throw new IllegalStateException(
                    "Requested replacement inventory is no longer available."
            );
        }

        long nights =
                ChronoUnit.DAYS.between(
                        request.checkIn(),
                        request.checkOut()
                );

        BigDecimal pricePerNight =
                authoritativePrice(
                        proposedRoom
                );

        BigDecimal proposedTotal =
                pricePerNight
                        .multiply(
                                BigDecimal.valueOf(
                                        nights
                                )
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
                authoritativeCurrency(
                        proposedRoom
                );

        if (booking.getCurrency() == null
                || !booking.getCurrency()
                        .equalsIgnoreCase(
                                currency
                        )) {

            throw new IllegalStateException(
                    "Modification currency does not match the original booking."
            );
        }

        BigDecimal originalTotal =
                booking.getTotalAmount()
                        .setScale(
                                2,
                                RoundingMode.UNNECESSARY
                        );

        BigDecimal difference =
                proposedTotal
                        .subtract(
                                originalTotal
                        )
                        .setScale(
                                2,
                                RoundingMode.UNNECESSARY
                        );

        ModificationFinancialType financialType =
                classify(
                        difference
                );

        InventoryHold replacementHold =
                new InventoryHold(
                        booking.getUser(),
                        booking.getHotel(),
                        proposedRoom,
                        request.checkIn(),
                        request.checkOut(),
                        request.guests(),
                        request.units(),
                        pricePerNight,
                        proposedTotal,
                        currency,
                        now.plus(
                                holdDuration
                        )
                );

        replacementHold =
                inventoryHoldRepository
                        .saveAndFlush(
                                replacementHold
                        );

        BookingModification modification =
                new BookingModification(
                        booking,
                        replacementHold,
                        proposedRoom,
                        difference,
                        financialType
                );

        modification =
                modificationRepository
                        .saveAndFlush(
                                modification
                        );

        return toResponse(
                modification
        );
    }

    @Transactional(readOnly = true)
    public BookingModificationResponse getModification(
            UUID modificationPublicId
    ) {
        if (modificationPublicId == null) {
            throw new IllegalArgumentException(
                    "Modification ID is required."
            );
        }

        UUID userPublicId =
                currentUser.publicId();

        BookingModification modification =
                modificationRepository
                        .findByPublicIdAndUserPublicId(
                                modificationPublicId,
                                userPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Modification does not exist or is not accessible."
                                )
                        );

        return toResponse(
                modification
        );
    }

    private void validateBookingCanBeModified(
            Booking booking
    ) {
        if (booking.getStatus()
                != BookingStatus.CONFIRMED) {

            throw new IllegalStateException(
                    "Only confirmed bookings can be modified."
            );
        }

        if (booking.getConfirmedAt() == null) {
            throw new IllegalStateException(
                    "Confirmed booking is missing its confirmation timestamp."
            );
        }

        Instant availableAt =
                booking.getConfirmedAt()
                        .plus(
                                MANAGEMENT_COOLDOWN
                        );

        if (Instant.now()
                .isBefore(
                        availableAt
                )) {

            throw new IllegalStateException(
                    "Booking modification becomes available 30 minutes after payment confirmation."
            );
        }

        LocalDate today =
                LocalDate.now();

        if (booking.getCheckIn() == null
                || !booking.getCheckIn()
                        .isAfter(
                                today
                        )) {

            throw new IllegalStateException(
                    "Booking can no longer be modified after check-in has started."
            );
        }
    }

    private void validateRequest(
            UUID bookingPublicId,
            BookingModificationRequest request
    ) {
        if (bookingPublicId == null) {
            throw new IllegalArgumentException(
                    "Booking ID is required."
            );
        }

        if (request == null) {
            throw new IllegalArgumentException(
                    "Modification request is required."
            );
        }

        if (request.roomSlug() == null
                || request.roomSlug()
                        .isBlank()) {

            throw new IllegalArgumentException(
                    "Room slug is required."
            );
        }

        if (request.checkIn() == null) {
            throw new IllegalArgumentException(
                    "Check-in date is required."
            );
        }

        if (request.checkOut() == null) {
            throw new IllegalArgumentException(
                    "Check-out date is required."
            );
        }

        if (request.checkIn()
                .isBefore(
                        LocalDate.now()
                )) {

            throw new IllegalArgumentException(
                    "Check-in cannot be before today."
            );
        }

        if (!request.checkOut()
                .isAfter(
                        request.checkIn()
                )) {

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
            BookingModificationRequest request
    ) {
        if (room.getStatus() == null
                || !"ACTIVE".equalsIgnoreCase(
                        room.getStatus()
                )) {

            throw new IllegalStateException(
                    "Requested room is not currently active."
            );
        }

        Integer maxGuests =
                room.getMaxGuests();

        if (maxGuests == null
                || maxGuests < 1) {

            throw new IllegalStateException(
                    "Requested room capacity is not configured correctly."
            );
        }

        long capacity =
                (long) maxGuests
                        * request.units();

        if (request.guests()
                > capacity) {

            throw new IllegalArgumentException(
                    "Guest count exceeds the capacity of the requested room units."
            );
        }
    }

    private BigDecimal authoritativePrice(
            Room room
    ) {
        BigDecimal price =
                room.getPricePerNight();

        if (price == null
                || price.signum() < 0) {

            throw new IllegalStateException(
                    "Room price is not configured correctly."
            );
        }

        return price.setScale(
                2,
                RoundingMode.UNNECESSARY
        );
    }

    private String authoritativeCurrency(
            Room room
    ) {
        String currency =
                room.getCurrency();

        if (currency == null
                || !currency.trim()
                        .matches("[A-Za-z]{3}")) {

            throw new IllegalStateException(
                    "Room currency is not configured correctly."
            );
        }

        return currency
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );
    }

    private ModificationFinancialType classify(
            BigDecimal difference
    ) {
        int comparison =
                difference.compareTo(
                        BigDecimal.ZERO
                );

        if (comparison > 0) {
            return ModificationFinancialType
                    .ADDITIONAL_PAYMENT;
        }

        if (comparison < 0) {
            return ModificationFinancialType
                    .PARTIAL_REFUND;
        }

        return ModificationFinancialType
                .NO_ADJUSTMENT;
    }

    private long safeCount(
            Long value
    ) {
        if (value == null) {
            return 0L;
        }

        if (value < 0) {
            throw new IllegalStateException(
                    "Inventory count cannot be negative."
            );
        }

        return value;
    }

    private BookingModificationResponse toResponse(
            BookingModification modification
    ) {
        Booking booking =
                modification.getBooking();

        InventoryHold hold =
                modification
                        .getReplacementHold();

        BigDecimal difference =
                modification
                        .getPriceDifference();

        ModificationFinancialType financialType =
                modification
                        .getFinancialType();

        String message =
                switch (financialType) {

                    case NO_ADJUSTMENT ->
                            "The proposed booking has the same total price.";

                    case ADDITIONAL_PAYMENT ->
                            "Additional payment is required before the modification can be completed.";

                    case PARTIAL_REFUND ->
                            "The modification reduces the booking total. The difference will be handled as a partial refund.";
                };

        return new BookingModificationResponse(
                modification.getPublicId(),

                booking.getPublicId(),

                booking.getBookingReference(),

                modification.getStatus(),

                financialType,

                modification
                        .getOriginalRoom()
                        .getSlug(),

                modification.getOriginalCheckIn(),

                modification.getOriginalCheckOut(),

                modification.getOriginalGuests(),

                modification.getOriginalUnits(),

                modification.getOriginalTotal(),

                modification
                        .getProposedRoom()
                        .getSlug(),

                modification.getProposedCheckIn(),

                modification.getProposedCheckOut(),

                modification.getProposedGuests(),

                modification.getProposedUnits(),

                modification
                        .getProposedPricePerNight(),

                modification.getProposedTotal(),

                difference,

                modification.getCurrency(),

                hold.getPublicId(),

                hold.getExpiresAt(),

                financialType
                        == ModificationFinancialType
                        .ADDITIONAL_PAYMENT,

                financialType
                        == ModificationFinancialType
                        .PARTIAL_REFUND,

                message
        );
    }
}