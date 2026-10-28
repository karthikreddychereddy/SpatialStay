package com.spatialstay.hoteloperations;

import com.spatialstay.booking.Booking;
import com.spatialstay.booking.BookingRepository;
import com.spatialstay.booking.BookingStatus;
import com.spatialstay.hotel.Hotel;
import com.spatialstay.user.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class HotelStayOperationService {
    private final HotelOperationsAccessService accessService;
    private final BookingRepository bookingRepository;
    private final HotelStayRepository stayRepository;
    private final CurrentUser currentUser;
    private final Clock clock;
    private final HotelAuditService auditService;

    @Autowired
    public HotelStayOperationService(
            HotelOperationsAccessService accessService,
            BookingRepository bookingRepository,
            HotelStayRepository stayRepository,
            CurrentUser currentUser,
            HotelAuditService auditService
    ) {
        this(accessService, bookingRepository, stayRepository, currentUser, auditService, Clock.systemDefaultZone());
    }

    HotelStayOperationService(
            HotelOperationsAccessService accessService,
            BookingRepository bookingRepository,
            HotelStayRepository stayRepository,
            CurrentUser currentUser,
            HotelAuditService auditService,
            Clock clock
    ) {
        this.accessService = accessService;
        this.bookingRepository = bookingRepository;
        this.stayRepository = stayRepository;
        this.currentUser = currentUser;
        this.auditService = auditService;
        this.clock = clock;
    }

    @Transactional
    public HotelStayOperationResponse checkIn(Long requestedHotelId, UUID bookingId) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        Booking booking = lockedBooking(hotel.getId(), bookingId);
        requireConfirmed(booking);

        LocalDate today = LocalDate.now(clock);
        if (today.isBefore(booking.getCheckIn())) {
            throw new IllegalStateException("Check-in is not available before the reservation check-in date.");
        }
        if (!today.isBefore(booking.getCheckOut())) {
            throw new IllegalStateException("Check-in is not available on or after the reservation check-out date.");
        }

        HotelStay stay = stayRepository.findByBookingPublicId(bookingId)
                .orElseGet(() -> HotelStay.forBooking(booking));
        stay.checkIn(Instant.now(clock), currentUser.publicId());
        HotelStay saved = stayRepository.save(stay);
        auditService.record(hotel, "CHECK_IN", "BOOKING", booking.getPublicId().toString(), "Guest checked in for " + booking.getBookingReference());
        return toResponse(saved);
    }

    @Transactional
    public HotelStayOperationResponse checkOut(Long requestedHotelId, UUID bookingId) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        Booking booking = lockedBooking(hotel.getId(), bookingId);
        requireConfirmed(booking);

        HotelStay stay = stayRepository.findByBookingPublicId(bookingId)
                .orElseThrow(() -> new IllegalStateException("Reservation has not been checked in."));
        stay.checkOut(Instant.now(clock), currentUser.publicId());
        HotelStay saved = stayRepository.save(stay);
        auditService.record(hotel, "CHECK_OUT", "BOOKING", booking.getPublicId().toString(), "Guest checked out for " + booking.getBookingReference());
        return toResponse(saved);
    }

    private Booking lockedBooking(Long hotelId, UUID bookingId) {
        if (bookingId == null) throw new IllegalArgumentException("Booking id is required.");
        return bookingRepository.findHotelBookingForUpdate(bookingId, hotelId)
                .orElseThrow(() -> new NoSuchElementException("Reservation was not found for this hotel."));
    }

    private void requireConfirmed(Booking booking) {
        if (booking.getStatus() != BookingStatus.CONFIRMED) {
            throw new IllegalStateException("Only a confirmed reservation can be used for stay operations.");
        }
    }

    private HotelStayOperationResponse toResponse(HotelStay stay) {
        Booking booking = stay.getBooking();
        return new HotelStayOperationResponse(
                booking.getPublicId(), booking.getBookingReference(), stay.getStatus(),
                stay.getCheckedInAt(), stay.getCheckedOutAt()
        );
    }
}
