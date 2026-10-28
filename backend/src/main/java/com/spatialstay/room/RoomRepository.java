package com.spatialstay.room;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomRepository
        extends JpaRepository<Room, Long> {

    List<Room> findByHotelSlug(
            String hotelSlug
    );

    List<Room> findByHotelId(
            Long hotelId
    );

    Optional<Room> findByHotelSlugAndSlug(
            String hotelSlug,
            String roomSlug
    );

    Optional<Room> findByIdAndHotelId(
            Long roomId,
            Long hotelId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from Room r
            join fetch r.hotel h
            where r.id = :roomId
              and h.id = :hotelId
            """)
    Optional<Room> findByIdAndHotelIdForUpdate(
            @Param("roomId") Long roomId,
            @Param("hotelId") Long hotelId
    );

    /**
     * Used when creating inventory holds.
     *
     * PESSIMISTIC_WRITE serializes competing hold-creation transactions
     * for the same room inventory row.
     *
     * The transaction that obtains this lock must recalculate authoritative
     * inventory before inserting the hold.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select r
            from Room r
            join fetch r.hotel h
            where h.slug = :hotelSlug
              and r.slug = :roomSlug
            """)
    Optional<Room> findByHotelSlugAndSlugForUpdate(
            @Param("hotelSlug") String hotelSlug,
            @Param("roomSlug") String roomSlug
    );

    @Query("""
            select distinct r.hotel.id
            from Room r
            where upper(r.status) = 'ACTIVE'
              and r.maxGuests >= :guests
              and r.totalUnits > 0
            """)
    List<Long> findHotelIdsWithAvailableRoomForGuests(
            @Param("guests")
            Integer guests
    );
}