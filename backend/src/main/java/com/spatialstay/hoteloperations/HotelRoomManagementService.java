package com.spatialstay.hoteloperations;

import com.spatialstay.booking.Booking;
import com.spatialstay.booking.BookingRepository;
import com.spatialstay.booking.BookingStatus;
import com.spatialstay.booking.InventoryHold;
import com.spatialstay.booking.InventoryHoldRepository;
import com.spatialstay.booking.InventoryHoldStatus;
import com.spatialstay.hotel.Hotel;
import com.spatialstay.room.Room;
import com.spatialstay.room.RoomRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class HotelRoomManagementService {

    private final HotelOperationsAccessService accessService;
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final InventoryHoldRepository inventoryHoldRepository;
    private final HotelAuditService auditService;

    public HotelRoomManagementService(
            HotelOperationsAccessService accessService,
            RoomRepository roomRepository,
            BookingRepository bookingRepository,
            InventoryHoldRepository inventoryHoldRepository,
            HotelAuditService auditService
    ) {
        this.accessService = accessService;
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.inventoryHoldRepository = inventoryHoldRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<HotelRoomManagementResponse> list(Long requestedHotelId) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        return roomRepository.findByHotelId(hotel.getId()).stream()
                .sorted(Comparator.comparing(Room::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public HotelRoomManagementResponse get(Long requestedHotelId, Long roomId) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        Room room = roomRepository.findByIdAndHotelId(roomId, hotel.getId())
                .orElseThrow(() -> new NoSuchElementException("Room does not exist for this hotel."));
        return toResponse(room);
    }

    @Transactional
    public HotelRoomManagementResponse updateCommercialConfiguration(
            Long requestedHotelId,
            Long roomId,
            UpdateHotelRoomCommercialRequest request
    ) {
        accessService.requireHotelHead();
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        if (request == null) {
            throw new IllegalArgumentException("Room configuration is required.");
        }

        Room room = roomRepository.findByIdAndHotelIdForUpdate(roomId, hotel.getId())
                .orElseThrow(() -> new NoSuchElementException("Room does not exist for this hotel."));

        if (request.totalUnits() != null) {
            if (request.totalUnits() < 0) {
                throw new IllegalArgumentException("Total room units cannot be negative.");
            }
            int protectedPeak = peakProtectedInventory(room.getId());
            if (request.totalUnits() < protectedPeak) {
                throw new IllegalStateException(
                        "Total units cannot be reduced below " + protectedPeak
                                + " because confirmed bookings or active holds already consume that inventory."
                );
            }
            room.setTotalUnits(request.totalUnits());
        }

        if (request.pricePerNight() != null) {
            BigDecimal price = normalizePrice(request.pricePerNight());
            room.setPricePerNight(price);
        }

        if (request.currency() != null) {
            String currency = request.currency().trim().toUpperCase(Locale.ROOT);
            if (!currency.matches("[A-Z]{3}")) {
                throw new IllegalArgumentException("Currency must be a 3-letter ISO-style code.");
            }
            room.setCurrency(currency);
        }

        if (request.status() != null) {
            String status = request.status().trim().toUpperCase(Locale.ROOT);
            if (!status.equals("ACTIVE") && !status.equals("INACTIVE")) {
                throw new IllegalArgumentException("Room status must be ACTIVE or INACTIVE.");
            }
            room.setStatus(status);
        }

        Room saved = roomRepository.saveAndFlush(room);
        auditService.record(hotel, "ROOM_COMMERCIAL_UPDATED", "ROOM", room.getId().toString(),
                "Room inventory/pricing/availability configuration updated.");
        return toResponse(saved);
    }

    private BigDecimal normalizePrice(BigDecimal price) {
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Price per night must be greater than zero.");
        }
        try {
            return price.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Price per night must use at most two decimal places.", exception);
        }
    }

    /*
     * totalUnits is the authoritative room-type inventory used by Step 9.
     * Before lowering it, protect every future date against existing
     * CONFIRMED bookings and non-expired ACTIVE holds. This prevents a
     * hotel-head update from silently overbooking inventory already sold/held.
     */
    private int peakProtectedInventory(Long roomId) {
        LocalDate today = LocalDate.now();
        Instant now = Instant.now();
        List<InventoryInterval> intervals = new ArrayList<>();

        for (Booking booking : bookingRepository.findConfirmedFutureInventoryByRoom(roomId, today)) {
            intervals.add(new InventoryInterval(
                    booking.getCheckIn(), booking.getCheckOut(), booking.getRoomQuantity()));
        }
        for (InventoryHold hold : inventoryHoldRepository.findActiveFutureInventoryByRoom(roomId, today, now)) {
            intervals.add(new InventoryInterval(
                    hold.getCheckIn(), hold.getCheckOut(), hold.getUnits()));
        }

        int peak = 0;
        for (InventoryInterval candidate : intervals) {
            for (LocalDate date = candidate.checkIn(); date.isBefore(candidate.checkOut()); date = date.plusDays(1)) {
                int used = 0;
                for (InventoryInterval interval : intervals) {
                    if (!date.isBefore(interval.checkIn()) && date.isBefore(interval.checkOut())) {
                        used += Math.max(interval.units(), 0);
                    }
                }
                peak = Math.max(peak, used);
            }
        }
        return peak;
    }

    private HotelRoomManagementResponse toResponse(Room room) {
        return new HotelRoomManagementResponse(
                room.getId(), room.getHotel().getId(), room.getSlug(), room.getName(),
                room.getRoomType(), room.getMaxGuests(), room.getTotalUnits(),
                room.getPricePerNight(), room.getCurrency(), room.getStatus(),
                room.getSpatialAvailable(), room.getUpdatedAt());
    }

    private record InventoryInterval(LocalDate checkIn, LocalDate checkOut, int units) {}
}
