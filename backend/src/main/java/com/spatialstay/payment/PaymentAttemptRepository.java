package com.spatialstay.payment;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentAttemptRepository
        extends JpaRepository<PaymentAttempt, Long> {

    long countByStatus(PaymentStatus status);

    @Query("select coalesce(sum(p.expectedAmount), 0) from PaymentAttempt p where p.status = :status")
    java.math.BigDecimal sumExpectedAmountByStatus(@Param("status") PaymentStatus status);

    Optional<PaymentAttempt> findByPublicId(
            UUID publicId
    );

    /** Read-only scanner lookup: initialize booking before the session closes. */
    @Query("""
            select p from PaymentAttempt p
            join fetch p.booking b
            where p.publicId = :publicId
            """)
    Optional<PaymentAttempt> findByPublicIdWithBooking(
            @Param("publicId") UUID publicId
    );

    @Query("""
            select p
            from PaymentAttempt p
            join fetch p.booking b
            join fetch p.user u
            where p.publicId = :paymentPublicId
              and u.publicId = :userPublicId
            """)
    Optional<PaymentAttempt> findOwnedByPublicId(
            @Param("paymentPublicId")
            UUID paymentPublicId,

            @Param("userPublicId")
            UUID userPublicId
    );

    Optional<PaymentAttempt>
            findByProviderAndProviderOrderId(
                    PaymentProvider provider,
                    String providerOrderId
            );

    Optional<PaymentAttempt>
            findByProviderAndProviderPaymentId(
                    PaymentProvider provider,
                    String providerPaymentId
            );

    List<PaymentAttempt>
            findAllByBookingPublicIdOrderByCreatedAtDesc(
                    UUID bookingPublicId
            );

    /*
     * IMPORTANT:
     *
     * This intentionally returns only the original booking payment.
     *
     * Existing cancellation/refund code currently depends on this
     * meaning. Do not broaden this query to modification payments.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from PaymentAttempt p
            join fetch p.booking b
            join fetch p.user u
            where b.publicId = :bookingPublicId
              and p.status =
                  com.spatialstay.payment.PaymentStatus.SUCCEEDED
              and p.purpose =
                  com.spatialstay.payment.PaymentPurpose.ORIGINAL_BOOKING
            order by p.succeededAt desc
            """)
    List<PaymentAttempt> findSucceededByBookingForUpdate(
            @Param("bookingPublicId")
            UUID bookingPublicId
    );

    /*
     * Step 11.11 financial ledger query.
     *
     * Returns every successful captured payment belonging to the
     * booking, newest first.
     *
     * This includes:
     *
     * ORIGINAL_BOOKING
     * MODIFICATION_ADDITIONAL
     *
     * Refund allocation uses newest-money-first so later additional
     * captures are unwound before the original booking capture.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from PaymentAttempt p
            join fetch p.booking b
            join fetch p.user u
            left join fetch p.bookingModification m
            where b.publicId = :bookingPublicId
              and p.status =
                  com.spatialstay.payment.PaymentStatus.SUCCEEDED
            order by p.succeededAt desc, p.id desc
            """)
    List<PaymentAttempt> findAllSucceededByBookingForUpdate(
            @Param("bookingPublicId")
            UUID bookingPublicId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from PaymentAttempt p
            join fetch p.booking b
            join fetch p.user u
            where p.publicId = :paymentPublicId
            """)
    Optional<PaymentAttempt> findByPublicIdForUpdate(
            @Param("paymentPublicId")
            UUID paymentPublicId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select p
            from PaymentAttempt p
            join fetch p.booking b
            join fetch p.user u
            where p.provider = :provider
              and p.providerOrderId = :providerOrderId
            """)
    Optional<PaymentAttempt>
            findByProviderOrderForUpdate(
                    @Param("provider")
                    PaymentProvider provider,

                    @Param("providerOrderId")
                    String providerOrderId
            );

    @Query("""
            select p
            from PaymentAttempt p
            join fetch p.booking b
            join fetch p.user u
            join fetch p.bookingModification m
            where m.publicId = :modificationPublicId
              and p.purpose =
                  com.spatialstay.payment.PaymentPurpose.MODIFICATION_ADDITIONAL
            order by p.createdAt desc
            """)
    List<PaymentAttempt> findModificationPayments(
            @Param("modificationPublicId")
            UUID modificationPublicId
    );

    /** Bounded discovery only; no row locks during provider HTTP calls. */
    @Query("""
            select p.publicId
            from PaymentAttempt p
            where p.provider = com.spatialstay.payment.PaymentProvider.RAZORPAY
              and p.purpose = com.spatialstay.payment.PaymentPurpose.ORIGINAL_BOOKING
              and p.providerOrderId is not null
              and p.status in (
                  com.spatialstay.payment.PaymentStatus.PENDING,
                  com.spatialstay.payment.PaymentStatus.PROCESSING,
                  com.spatialstay.payment.PaymentStatus.EXPIRED)
            order by p.createdAt asc
            """)
    List<UUID> findOriginalBookingReconciliationCandidates(
            org.springframework.data.domain.Pageable page
    );

    boolean existsByBookingModificationPublicIdAndPurposeAndStatusIn(
            UUID bookingModificationPublicId,
            PaymentPurpose purpose,
            Collection<PaymentStatus> statuses
    );



    @Query("""
            select p.currency, sum(p.expectedAmount), count(p)
            from PaymentAttempt p
            where p.booking.hotel.id = :hotelId
              and p.status = com.spatialstay.payment.PaymentStatus.SUCCEEDED
              and p.succeededAt >= :from and p.succeededAt < :to
            group by p.currency
            """)
    List<Object[]> summarizeHotelSuccessfulPayments(@Param("hotelId") Long hotelId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to);

    @Query("""
            select p from PaymentAttempt p join fetch p.booking b
            where b.hotel.id = :hotelId
              and p.status = com.spatialstay.payment.PaymentStatus.SUCCEEDED
              and p.succeededAt >= :from and p.succeededAt < :to
            order by p.succeededAt desc, p.id desc
            """)
    List<PaymentAttempt> findRecentHotelSuccessfulPayments(@Param("hotelId") Long hotelId, @Param("from") java.time.Instant from, @Param("to") java.time.Instant to, org.springframework.data.domain.Pageable page);

}
