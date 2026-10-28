package com.spatialstay.booking;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryHoldRepository
        extends JpaRepository<InventoryHold, Long> {

    Optional<InventoryHold> findByPublicId(
            UUID publicId
    );

    Optional<InventoryHold> findByPublicIdAndUserPublicId(
            UUID publicId,
            UUID userPublicId
    );
    /**
     * Authoritative lifecycle lock for an inventory hold.
     *
     * Step 9 expiration and Step 10 payment confirmation must use
     * the same hold-row locking boundary so the two transitions
     * cannot race each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select h
            from InventoryHold h
            join fetch h.user
            join fetch h.hotel
            join fetch h.room
            where h.publicId = :holdPublicId
            """)
    Optional<InventoryHold> findByPublicIdForUpdate(
            @Param("holdPublicId") UUID holdPublicId
    );

    /**
     * Locks the customer's hold while it is being converted into
     * a booking.
     *
     * Ownership is enforced directly in the database query.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select h
            from InventoryHold h
            join fetch h.user u
            join fetch h.hotel hotel
            join fetch h.room room
            where h.publicId = :holdPublicId
              and u.publicId = :userPublicId
            """)
    Optional<InventoryHold> findOwnedByPublicIdForUpdate(
            @Param("holdPublicId") UUID holdPublicId,
            @Param("userPublicId") UUID userPublicId
    );

    /**
     * Only ACTIVE and non-expired holds consume inventory.
     */
    @Query("""
            select coalesce(sum(h.units), 0)
            from InventoryHold h
            where h.room.id = :roomId
              and h.status = com.spatialstay.booking.InventoryHoldStatus.ACTIVE
              and h.expiresAt > :now
              and h.checkIn < :checkOut
              and h.checkOut > :checkIn
            """)
    Long sumActiveUnitsForStay(
            @Param("roomId") Long roomId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("now") Instant now
    );

    List<InventoryHold> findByStatusAndExpiresAtLessThanEqual(
            InventoryHoldStatus status,
            Instant expiresAt
    );
    @Query("""
            select h
            from InventoryHold h
            where h.room.id = :roomId
              and h.status = com.spatialstay.booking.InventoryHoldStatus.ACTIVE
              and h.expiresAt > :now
              and h.checkOut > :today
            """)
    List<InventoryHold> findActiveFutureInventoryByRoom(
            @Param("roomId") Long roomId,
            @Param("today") LocalDate today,
            @Param("now") Instant now
    );

}