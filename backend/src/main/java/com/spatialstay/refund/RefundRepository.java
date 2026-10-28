package com.spatialstay.refund;

import com.spatialstay.payment.PaymentProvider;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RefundRepository
        extends JpaRepository<Refund, Long> {

        Optional<Refund> findByPublicId(
                UUID publicId
        );

        Optional<Refund> findByBookingPublicId(
                UUID bookingPublicId
        );

        Optional<Refund>
        findByProviderAndProviderRefundId(
                PaymentProvider provider,
                String providerRefundId
        );

        /*
        * Used when a Razorpay webhook reaches us before the synchronous
        * refund API response has persisted providerRefundId.
        *
        * The provider payment ID originates from the authoritative
        * successful PaymentAttempt, never from the browser.
        */
        Optional<Refund>
        findByProviderAndProviderPaymentId(
                PaymentProvider provider,
                String providerPaymentId
        );

        /*
        * Recovery-worker discovery query.
        *
        * This query deliberately does NOT lock the rows.
        *
        * Multiple workers may discover the same PENDING refund. That is
        * safe because RefundProcessingTransactionService.claim() performs
        * the authoritative PESSIMISTIC_WRITE lock and only permits the
        * PENDING -> PROCESSING transition once.
        *
        * The worker only discovers PENDING refunds. PROCESSING and
        * REQUIRES_RECONCILIATION refunds must never be blindly resubmitted
        * because Razorpay may already have accepted them.
        */
        @Query("""
                select r.publicId
                from Refund r
                where r.status = :status
                and r.provider = :provider
                and r.providerRefundId is null
                order by r.createdAt asc
                """)
        List<UUID> findPublicIdsForRecovery(
                @Param("status")
                RefundStatus status,
                @Param("provider")
                PaymentProvider provider,
                Pageable pageable
        );

        boolean existsByBookingPublicIdAndProviderAndProviderPaymentId(
                        UUID bookingPublicId,
                        PaymentProvider provider,
                        String providerPaymentId
                );

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                select r
                from Refund r
                where r.publicId = :refundPublicId
                """)
        Optional<Refund> findByPublicIdForUpdate(
                @Param("refundPublicId")
                UUID refundPublicId
        );

        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("""
                select r
                from Refund r
                where r.booking.publicId = :bookingPublicId
                """)
        Optional<Refund> findByBookingPublicIdForUpdate(
                @Param("bookingPublicId")
                UUID bookingPublicId
        );

        @Query("""
                select
                        r.publicId as publicId,
                        r.providerRefundId as providerRefundId
                from Refund r
                where r.status = :status
                and r.provider = :provider
                and r.providerRefundId is not null
                order by r.updatedAt asc
                """)
        List<RefundReconciliationCandidate>
        findReconciliationCandidates(
                @Param("status")
                RefundStatus status,
                @Param("provider")
                PaymentProvider provider,
                Pageable pageable
        );

    

        @Query("""
                select r.currency, r.status, sum(r.refundAmount), count(r)
                from Refund r
                where r.booking.hotel.id = :hotelId
                  and r.createdAt >= :from and r.createdAt < :to
                group by r.currency, r.status
                """)
        List<Object[]> summarizeHotelRefunds(@Param("hotelId") Long hotelId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

        @Query("""
                select r from Refund r join fetch r.booking b
                where b.hotel.id = :hotelId
                  and r.createdAt >= :from and r.createdAt < :to
                order by r.createdAt desc, r.id desc
                """)
        List<Refund> findRecentHotelRefunds(@Param("hotelId") Long hotelId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to, Pageable page);

}
