package com.spatialstay.booking;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import java.time.Instant;
import java.util.List;

public interface BookingModificationRepository
        extends JpaRepository<BookingModification, Long> {

    Optional<BookingModification> findByPublicId(
            UUID publicId
    );

    @Query("""
            select m
            from BookingModification m
            join fetch m.booking b
            join fetch m.user u
            join fetch m.replacementHold h
            join fetch m.originalRoom
            join fetch m.proposedRoom
            where m.publicId = :publicId
              and u.publicId = :userPublicId
            """)
    Optional<BookingModification>
    findByPublicIdAndUserPublicId(
            @Param("publicId")
            UUID publicId,

            @Param("userPublicId")
            UUID userPublicId
    );

    boolean existsByBookingPublicIdAndStatusIn(
            UUID bookingPublicId,
            Collection<BookingModificationStatus> statuses
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select m
            from BookingModification m
            join fetch m.booking b
            join fetch m.user u
            join fetch m.replacementHold h
            join fetch m.originalRoom
            join fetch m.proposedRoom
            where m.publicId = :publicId
            """)
    Optional<BookingModification>
    findByPublicIdForUpdate(
            @Param("publicId")
            UUID publicId
    );

    @Query("""
        select m.publicId
        from BookingModification m
        where m.status in :statuses
        and m.replacementHold.expiresAt <= :now
        order by m.replacementHold.expiresAt asc
        """)
    List<UUID> findExpiredModificationCandidates(
            @Param("statuses")
            Collection<BookingModificationStatus> statuses,

            @Param("now")
            Instant now
    );
}