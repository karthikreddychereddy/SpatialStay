package com.spatialstay.booking;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository
        extends JpaRepository<Booking, Long> {

    long countByStatus(BookingStatus status);

    Optional<Booking> findByPublicId(
            UUID publicId
    );

    Optional<Booking> findByBookingReference(
            String bookingReference
    );

    Optional<Booking> findByPublicIdAndUserPublicId(
            UUID publicId,
            UUID userPublicId
    );

    /**
     * Customer-owned pessimistic booking lock.
     *
     * Used by authoritative lifecycle mutations such as cancellation.
     *
     * Ownership is part of the database query and the row is locked
     * before cancellation eligibility is evaluated.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b
            from Booking b
            where b.publicId = :bookingPublicId
              and b.user.publicId = :userPublicId
            """)
    Optional<Booking> findOwnedByPublicIdForUpdate(
            @Param("bookingPublicId") UUID bookingPublicId,
            @Param("userPublicId") UUID userPublicId
    );

    List<Booking> findAllByUserPublicIdOrderByCreatedAtDesc(
            UUID userPublicId
    );

    Optional<Booking> findByInventoryHoldPublicId(
            UUID holdPublicId
    );

    @Query("""
            select coalesce(sum(b.roomQuantity), 0)
            from Booking b
            where b.room.id = :roomId
              and b.status = com.spatialstay.booking.BookingStatus.CONFIRMED
              and b.checkIn < :checkOut
              and b.checkOut > :checkIn
            """)
    Long sumConfirmedUnitsForStay(
            @Param("roomId") Long roomId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );

    @Query("""
                select coalesce(sum(b.roomQuantity), 0)
                from Booking b
                where b.room.id = :roomId
                and b.status = com.spatialstay.booking.BookingStatus.CONFIRMED
                and b.publicId <> :excludedBookingPublicId
                and b.checkIn < :checkOut
                and b.checkOut > :checkIn
                """)
        Long sumConfirmedUnitsForStayExcludingBooking(
                @Param("roomId") Long roomId,
                @Param("checkIn") LocalDate checkIn,
                @Param("checkOut") LocalDate checkOut,
                @Param("excludedBookingPublicId")
                UUID excludedBookingPublicId
        );

    @Query("""
            select count(b)
            from Booking b
            where b.hotel.id = :hotelId
              and b.status = com.spatialstay.booking.BookingStatus.CONFIRMED
              and b.checkIn = :date
            """)
    long countConfirmedArrivals(
            @Param("hotelId") Long hotelId,
            @Param("date") LocalDate date
    );

    @Query("""
            select count(b)
            from Booking b
            where b.hotel.id = :hotelId
              and b.status = com.spatialstay.booking.BookingStatus.CONFIRMED
              and b.checkOut = :date
            """)
    long countConfirmedDepartures(
            @Param("hotelId") Long hotelId,
            @Param("date") LocalDate date
    );

    @Query("""
            select count(b)
            from Booking b
            where b.hotel.id = :hotelId
              and b.status = com.spatialstay.booking.BookingStatus.CONFIRMED
              and b.checkIn <= :date
              and b.checkOut > :date
            """)
    long countConfirmedStaysOnDate(
            @Param("hotelId") Long hotelId,
            @Param("date") LocalDate date
    );

    @Query("""
            select count(b)
            from Booking b
            where b.hotel.id = :hotelId
              and b.status = com.spatialstay.booking.BookingStatus.CONFIRMED
              and b.checkIn > :date
            """)
    long countConfirmedUpcoming(
            @Param("hotelId") Long hotelId,
            @Param("date") LocalDate date
    );

    @Query("""
            select coalesce(sum(b.totalAmount), 0)
            from Booking b
            where b.hotel.id = :hotelId
              and b.status = com.spatialstay.booking.BookingStatus.CONFIRMED
            """)
    BigDecimal sumConfirmedBookingValue(
            @Param("hotelId") Long hotelId
    );

    @EntityGraph(attributePaths = {"user", "room"})
    List<Booking> findTop10ByHotelIdOrderByCreatedAtDesc(
            Long hotelId
    );

    default List<Booking> findTop10HotelBookings(Long hotelId) {
        return findTop10ByHotelIdOrderByCreatedAtDesc(hotelId);
    }
    @EntityGraph(attributePaths = {"user", "room", "hotel"})
    List<Booking> findTop200ByHotelIdOrderByCheckInAscCreatedAtDesc(
            Long hotelId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"user", "room", "hotel"})
    @Query("""
            select b
            from Booking b
            where b.publicId = :bookingPublicId
              and b.hotel.id = :hotelId
            """)
    Optional<Booking> findHotelBookingForUpdate(
            @Param("bookingPublicId") UUID bookingPublicId,
            @Param("hotelId") Long hotelId
    );




    @Query("""
            select b
            from Booking b
            where b.room.id = :roomId
              and b.status = com.spatialstay.booking.BookingStatus.CONFIRMED
              and b.checkOut > :today
            """)
    List<Booking> findConfirmedFutureInventoryByRoom(
            @Param("roomId") Long roomId,
            @Param("today") LocalDate today
    );

    @Query("""
            select b.currency, sum(b.totalAmount)
            from Booking b
            where b.hotel.id = :hotelId
              and b.status = com.spatialstay.booking.BookingStatus.CONFIRMED
              and b.confirmedAt >= :from and b.confirmedAt < :to
            group by b.currency
            """)
    List<Object[]> summarizeHotelConfirmedBookingValue(@Param("hotelId") Long hotelId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

}
