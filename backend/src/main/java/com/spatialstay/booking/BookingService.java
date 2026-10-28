package com.spatialstay.booking;

import com.spatialstay.user.CurrentUser;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final InventoryHoldRepository inventoryHoldRepository;
    private final CurrentUser currentUser;

    public BookingService(
            BookingRepository bookingRepository,
            InventoryHoldRepository inventoryHoldRepository,
            CurrentUser currentUser
    ) {
        this.bookingRepository =
                bookingRepository;

        this.inventoryHoldRepository =
                inventoryHoldRepository;

        this.currentUser =
                currentUser;
    }

    /**
     * Returns every booking owned by the currently authenticated
     * customer.
     *
     * The repository performs the ownership restriction directly.
     * No other customer's booking can be returned by this method.
     */
    @Transactional(readOnly = true)
    public List<BookingResponse> getMyBookings() {

        UUID userPublicId =
                currentUser.publicId();

        return bookingRepository
                .findAllByUserPublicIdOrderByCreatedAtDesc(
                        userPublicId
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Retrieves one booking only when it belongs to the currently
     * authenticated customer.
     */
    @Transactional(readOnly = true)
    public BookingResponse getOwnedBooking(
            UUID bookingPublicId
    ) {
        if (bookingPublicId == null) {
            throw new IllegalArgumentException(
                    "Booking ID is required."
            );
        }

        UUID userPublicId =
                currentUser.publicId();

        Booking booking =
                bookingRepository
                        .findByPublicIdAndUserPublicId(
                                bookingPublicId,
                                userPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Booking does not exist or is not accessible."
                                )
                        );

        return toResponse(
                booking
        );
    }

    /**
     * Creates a PENDING_PAYMENT booking from an authoritative
     * inventory hold.
     *
     * The hold intentionally remains ACTIVE while payment is pending.
     * Therefore it continues protecting inventory until:
     *
     * 1. Step 10 verifies payment and consumes the hold, or
     * 2. the 10-minute hold expires.
     */
    @Transactional
    public BookingResponse createFromHold(
            CreateBookingFromHoldRequest request
    ) {
        validateRequest(request);

        UUID userPublicId =
                currentUser.publicId();

        /*
         * Ownership + concurrency protection.
         *
         * Only the authenticated owner can lock and use this hold.
         */
        InventoryHold hold =
                inventoryHoldRepository
                        .findOwnedByPublicIdForUpdate(
                                request.holdId(),
                                userPublicId
                        )
                        .orElseThrow(
                                () -> new IllegalArgumentException(
                                        "Inventory hold does not exist or is not accessible."
                                )
                        );

        /*
         * Idempotency:
         *
         * If this hold already created a booking, return that booking
         * instead of creating a duplicate.
         */
        Booking existingBooking =
                bookingRepository
                        .findByInventoryHoldPublicId(
                                hold.getPublicId()
                        )
                        .orElse(null);

        Instant now =
                Instant.now();

        /*
         * Idempotency with lifecycle normalization.
         *
         * A retry must return the existing booking rather than create
         * another one.
         *
         * However, if the payment window already expired before the
         * scheduler normalized the database state, we normalize it here
         * while the hold is already protected by PESSIMISTIC_WRITE.
         *
         * This closes the small expiresAt -> scheduler-run race window.
         */
        if (existingBooking != null) {

            boolean holdExpiredByTime =
                    hold.getExpiresAt() == null
                            || !hold.getExpiresAt().isAfter(now);

            if (
                    existingBooking.getStatus()
                            == BookingStatus.PENDING_PAYMENT
                            && hold.getStatus()
                            == InventoryHoldStatus.ACTIVE
                            && holdExpiredByTime
            ) {
                existingBooking.markExpired(now);
                hold.markExpired(now);
            }

            /*
             * Defensive normalization in case the hold was already
             * expired but its PENDING_PAYMENT booking has not yet been
             * normalized.
             */
            if (
                    existingBooking.getStatus()
                            == BookingStatus.PENDING_PAYMENT
                            && hold.getStatus()
                            == InventoryHoldStatus.EXPIRED
            ) {
                existingBooking.markExpired(now);
            }

            return toResponse(
                    existingBooking
            );
        }

        validateHoldForBooking(
                hold,
                now
        );

        /*
         * Booking.pendingPayment(hold) copies all authoritative booking
         * facts from the hold snapshot.
         *
         * No price, hotel, room, dates, units, guests or currency are
         * accepted from the frontend.
         */
        Booking booking =
                Booking.pendingPayment(
                        hold
                );

        Booking saved =
                bookingRepository.saveAndFlush(
                        booking
                );

        /*
         * IMPORTANT:
         *
         * Do NOT mark the hold CONSUMED here.
         *
         * The ACTIVE hold continues protecting inventory during the
         * payment window.
         *
         * Step 10:
         * verified payment webhook
         *      ↓
         * Booking -> CONFIRMED
         * Hold    -> CONSUMED
         */
        return toResponse(
                saved
        );
    }

    private void validateRequest(
            CreateBookingFromHoldRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException(
                    "Booking request is required."
            );
        }

        if (request.holdId() == null) {
            throw new IllegalArgumentException(
                    "Hold ID is required."
            );
        }
    }

    private void validateHoldForBooking(
            InventoryHold hold,
            Instant now
    ) {
        if (hold.getStatus()
                != InventoryHoldStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Inventory hold is no longer active."
            );
        }

        /*
         * expiresAt is authoritative even if the scheduled cleanup job
         * has not yet changed ACTIVE -> EXPIRED.
         */
        if (hold.getExpiresAt() == null
                || !hold.getExpiresAt().isAfter(now)) {
            throw new IllegalStateException(
                    "Inventory hold has expired."
            );
        }

        if (hold.getUser() == null) {
            throw new IllegalStateException(
                    "Inventory hold customer is missing."
            );
        }

        if (hold.getHotel() == null) {
            throw new IllegalStateException(
                    "Inventory hold hotel is missing."
            );
        }

        if (hold.getRoom() == null) {
            throw new IllegalStateException(
                    "Inventory hold room is missing."
            );
        }

        if (hold.getCheckIn() == null
                || hold.getCheckOut() == null
                || !hold.getCheckOut().isAfter(
                        hold.getCheckIn()
                )) {
            throw new IllegalStateException(
                    "Inventory hold contains an invalid stay."
            );
        }

        if (hold.getUnits() == null
                || hold.getUnits() < 1) {
            throw new IllegalStateException(
                    "Inventory hold contains an invalid room quantity."
            );
        }

        if (hold.getGuests() == null
                || hold.getGuests() < 1) {
            throw new IllegalStateException(
                    "Inventory hold contains an invalid guest count."
            );
        }

        if (hold.getPricePerNight() == null
                || hold.getPricePerNight().signum() < 0) {
            throw new IllegalStateException(
                    "Inventory hold contains an invalid room price."
            );
        }

        if (hold.getTotalAmount() == null
                || hold.getTotalAmount().signum() < 0) {
            throw new IllegalStateException(
                    "Inventory hold contains an invalid total amount."
            );
        }

        if (hold.getCurrency() == null
                || !hold.getCurrency().matches("[A-Z]{3}")) {
            throw new IllegalStateException(
                    "Inventory hold contains an invalid currency."
            );
        }
    }

    private BookingResponse toResponse(
            Booking booking
    ) {
        return new BookingResponse(
                booking.getPublicId(),
                booking.getBookingReference(),
                booking.getInventoryHold().getPublicId(),

                booking.getHotel().getSlug(),
                booking.getHotel().getName(),

                booking.getRoom().getSlug(),
                booking.getRoom().getName(),

                booking.getCheckIn(),
                booking.getCheckOut(),
                booking.getNightCount(),

                booking.getGuestCount(),
                booking.getRoomQuantity(),

                booking.getPricePerNight(),
                booking.getTotalAmount(),
                booking.getCurrency(),

                booking.getStatus(),

                booking.getCreatedAt(),
                booking.getUpdatedAt(),
                booking.getConfirmedAt(),
                booking.getCancelledAt(),
                booking.getExpiredAt()
        );
    }
}