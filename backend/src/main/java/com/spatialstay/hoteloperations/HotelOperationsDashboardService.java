package com.spatialstay.hoteloperations;

import com.spatialstay.booking.Booking;
import com.spatialstay.booking.BookingRepository;
import com.spatialstay.booking.InventoryHoldRepository;
import com.spatialstay.hotel.Hotel;
import com.spatialstay.room.Room;
import com.spatialstay.room.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class HotelOperationsDashboardService {

    private final HotelOperationsAccessService accessService;
    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final InventoryHoldRepository inventoryHoldRepository;
    private final Clock clock;

    @Autowired
    public HotelOperationsDashboardService(
            HotelOperationsAccessService accessService,
            BookingRepository bookingRepository,
            RoomRepository roomRepository,
            InventoryHoldRepository inventoryHoldRepository
    ) {
        this(accessService, bookingRepository, roomRepository, inventoryHoldRepository, Clock.systemDefaultZone());
    }

    HotelOperationsDashboardService(
            HotelOperationsAccessService accessService,
            BookingRepository bookingRepository,
            RoomRepository roomRepository,
            InventoryHoldRepository inventoryHoldRepository,
            Clock clock
    ) {
        this.accessService = accessService;
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.inventoryHoldRepository = inventoryHoldRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public HotelOperationsDashboardResponse getDashboard(Long requestedHotelId) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);

        long arrivals = bookingRepository.countConfirmedArrivals(hotel.getId(), today);
        long departures = bookingRepository.countConfirmedDepartures(hotel.getId(), today);
        long currentlyStaying = bookingRepository.countConfirmedStaysOnDate(hotel.getId(), today);
        long upcoming = bookingRepository.countConfirmedUpcoming(hotel.getId(), today);

        List<Room> rooms = roomRepository.findByHotelId(hotel.getId());
        long totalUnits = 0;
        long occupiedUnits = 0;
        long heldUnits = 0;

        LocalDate tomorrow = today.plusDays(1);
        for (Room room : rooms) {
            if (!"ACTIVE".equalsIgnoreCase(room.getStatus())) {
                continue;
            }
            totalUnits += room.getTotalUnits();
            occupiedUnits += bookingRepository.sumConfirmedUnitsForStay(
                    room.getId(), today, tomorrow
            );
            heldUnits += inventoryHoldRepository.sumActiveUnitsForStay(
                    room.getId(), today, tomorrow, now
            );
        }

        long availableUnits = Math.max(0, totalUnits - occupiedUnits - heldUnits);
        BigDecimal bookingValue = bookingRepository.sumConfirmedBookingValue(hotel.getId());

        List<HotelOperationsDashboardResponse.RecentReservation> recent =
                bookingRepository.findTop10HotelBookings(hotel.getId()).stream()
                        .map(this::toRecentReservation)
                        .toList();

        String currency = rooms.stream()
                .findFirst()
                .map(Room::getCurrency)
                .orElse("INR");

        return new HotelOperationsDashboardResponse(
                new HotelOperationsDashboardResponse.HotelSummary(
                        hotel.getId(), hotel.getSlug(), hotel.getName(), hotel.getCity(),
                        hotel.getCountry(), hotel.getStatus()
                ),
                today,
                arrivals,
                departures,
                currentlyStaying,
                upcoming,
                totalUnits,
                occupiedUnits,
                heldUnits,
                availableUnits,
                bookingValue,
                currency,
                recent
        );
    }

    private HotelOperationsDashboardResponse.RecentReservation toRecentReservation(Booking booking) {
        String firstName = booking.getUser().getFirstName();
        String lastName = booking.getUser().getLastName();
        String guestName = ((firstName == null ? "" : firstName) + " "
                + (lastName == null ? "" : lastName)).trim();

        return new HotelOperationsDashboardResponse.RecentReservation(
                booking.getPublicId(),
                booking.getBookingReference(),
                guestName,
                booking.getRoom().getName(),
                booking.getCheckIn(),
                booking.getCheckOut(),
                booking.getGuestCount(),
                booking.getRoomQuantity(),
                booking.getTotalAmount(),
                booking.getCurrency(),
                booking.getStatus(),
                booking.getCreatedAt()
        );
    }
}
