package com.spatialstay.modificationrefund;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModificationRefundBatchRepository
        extends JpaRepository<ModificationRefundBatch, Long> {

    Optional<ModificationRefundBatch>
            findByPublicId(
                    UUID publicId
            );

    Optional<ModificationRefundBatch>
            findByBookingModificationPublicId(
                    UUID bookingModificationPublicId
            );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select b
            from ModificationRefundBatch b
            join fetch b.booking booking
            join fetch b.bookingModification modification
            join fetch b.user user
            where b.publicId = :publicId
            """)
    Optional<ModificationRefundBatch>
            findByPublicIdForUpdate(
                    @Param("publicId")
                    UUID publicId
            );



    @Query("""
            select r.currency, r.status, sum(r.totalAmount), count(r)
            from ModificationRefundBatch r
            where r.booking.hotel.id = :hotelId
              and r.createdAt >= :from and r.createdAt < :to
            group by r.currency, r.status
            """)
    java.util.List<Object[]> summarizeHotelRefundBatches(@Param("hotelId") Long hotelId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("""
            select r from ModificationRefundBatch r join fetch r.booking b
            where b.hotel.id = :hotelId
              and r.createdAt >= :from and r.createdAt < :to
            order by r.createdAt desc, r.id desc
            """)
    java.util.List<ModificationRefundBatch> findRecentHotelRefundBatches(@Param("hotelId") Long hotelId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to, org.springframework.data.domain.Pageable page);

}
