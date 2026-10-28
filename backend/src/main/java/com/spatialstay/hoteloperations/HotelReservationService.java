package com.spatialstay.hoteloperations;

import com.spatialstay.booking.Booking;
import com.spatialstay.booking.BookingRepository;
import com.spatialstay.booking.BookingStatus;
import com.spatialstay.hotel.Hotel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HotelReservationService {
    private final HotelOperationsAccessService accessService;
    private final BookingRepository bookingRepository;
    private final HotelStayRepository stayRepository;
    private final Clock clock;

    @Autowired
    public HotelReservationService(
            HotelOperationsAccessService accessService,
            BookingRepository bookingRepository,
            HotelStayRepository stayRepository
    ) {
        this(accessService, bookingRepository, stayRepository, Clock.systemDefaultZone());
    }

    HotelReservationService(
            HotelOperationsAccessService accessService,
            BookingRepository bookingRepository,
            HotelStayRepository stayRepository,
            Clock clock
    ) {
        this.accessService = accessService;
        this.bookingRepository = bookingRepository;
        this.stayRepository = stayRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<HotelReservationResponse> list(
            Long requestedHotelId,
            HotelReservationView view,
            String search
    ) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        HotelReservationView effectiveView = view == null ? HotelReservationView.ALL : view;
        LocalDate today = LocalDate.now(clock);
        String needle = normalize(search);

        List<Booking> bookings = bookingRepository
                .findTop200ByHotelIdOrderByCheckInAscCreatedAtDesc(hotel.getId());

        Map<UUID, HotelStay> stays = stayRepository.findAllByBookingPublicIdIn(
                        bookings.stream().map(Booking::getPublicId).toList())
                .stream()
                .collect(Collectors.toMap(
                        stay -> stay.getBooking().getPublicId(),
                        Function.identity()
                ));

        return bookings.stream()
                .filter(booking -> matchesView(booking, stays.get(booking.getPublicId()), effectiveView, today))
                .filter(booking -> matchesSearch(booking, needle))
                .map(booking -> toResponse(booking, stays.get(booking.getPublicId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public HotelReservationResponse get(Long requestedHotelId, UUID bookingId) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        Booking booking = bookingRepository.findByPublicId(bookingId)
                .filter(candidate -> candidate.getHotel().getId().equals(hotel.getId()))
                .orElseThrow(() -> new NoSuchElementException("Reservation was not found for this hotel."));
        HotelStay stay = stayRepository.findByBookingPublicId(bookingId).orElse(null);
        return toResponse(booking, stay);
    }

    private boolean matchesView(Booking booking, HotelStay stay, HotelReservationView view, LocalDate today) {
        if (view == HotelReservationView.ALL) return true;
        if (booking.getStatus() != BookingStatus.CONFIRMED) return false;
        StayStatus stayStatus = stay == null ? StayStatus.NOT_CHECKED_IN : stay.getStatus();
        return switch (view) {
            case ARRIVALS -> booking.getCheckIn().equals(today) && stayStatus == StayStatus.NOT_CHECKED_IN;
            case DEPARTURES -> booking.getCheckOut().equals(today) && stayStatus == StayStatus.CHECKED_IN;
            case IN_HOUSE -> stayStatus == StayStatus.CHECKED_IN;
            case UPCOMING -> booking.getCheckIn().isAfter(today) && stayStatus == StayStatus.NOT_CHECKED_IN;
            case ALL -> true;
        };
    }

    private boolean matchesSearch(Booking booking, String needle) {
        if (needle == null) return true;
        String first = Objects.toString(booking.getUser().getFirstName(), "");
        String last = Objects.toString(booking.getUser().getLastName(), "");
        String haystack = String.join(" ",
                booking.getBookingReference(),
                first, last,
                booking.getUser().getEmail(),
                booking.getRoom().getName(),
                booking.getRoom().getRoomType()
        ).toLowerCase(Locale.ROOT);
        return haystack.contains(needle);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private HotelReservationResponse toResponse(Booking booking, HotelStay stay) {
        String guestName = (Objects.toString(booking.getUser().getFirstName(), "") + " "
                + Objects.toString(booking.getUser().getLastName(), "")).trim();
        return new HotelReservationResponse(
                booking.getPublicId(), booking.getBookingReference(), booking.getStatus(),
                stay == null ? StayStatus.NOT_CHECKED_IN : stay.getStatus(),
                guestName, booking.getUser().getEmail(), booking.getRoom().getName(),
                booking.getRoom().getRoomType(), booking.getCheckIn(), booking.getCheckOut(),
                booking.getGuestCount(), booking.getRoomQuantity(), booking.getTotalAmount(),
                booking.getCurrency(), booking.getConfirmedAt(),
                stay == null ? null : stay.getCheckedInAt(),
                stay == null ? null : stay.getCheckedOutAt()
        );
    }
}
