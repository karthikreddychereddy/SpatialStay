package com.spatialstay.modificationrefund;

import com.spatialstay.payment.PaymentProvider;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ModificationRefundAllocationRepository
        extends JpaRepository<ModificationRefundAllocation, Long> {

    @Query("""
            select a
            from ModificationRefundAllocation a
            join fetch a.batch b
            join fetch a.paymentAttempt p
            where b.publicId = :batchPublicId
            order by a.id asc
            """)
    List<ModificationRefundAllocation>
            findAllByBatchPublicId(
                    @Param("batchPublicId")
                    UUID batchPublicId
            );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select a
            from ModificationRefundAllocation a
            join fetch a.batch b
            join fetch a.paymentAttempt p
            where a.publicId = :publicId
            """)
    Optional<ModificationRefundAllocation>
            findByPublicIdForUpdate(
                    @Param("publicId")
                    UUID publicId
            );

    Optional<ModificationRefundAllocation>
            findByProviderAndProviderRefundId(
                    PaymentProvider provider,
                    String providerRefundId
            );

    /*
     * Amount already allocated/refunded from one successful payment
     * by the modification-refund ledger.
     */
    @Query("""
            select coalesce(sum(a.amount), 0)
            from ModificationRefundAllocation a
            where a.paymentAttempt.publicId = :paymentPublicId
              and a.status in (
                  com.spatialstay.modificationrefund.ModificationRefundAllocationStatus.PENDING,
                  com.spatialstay.modificationrefund.ModificationRefundAllocationStatus.PROCESSING,
                  com.spatialstay.modificationrefund.ModificationRefundAllocationStatus.SUCCEEDED,
                  com.spatialstay.modificationrefund.ModificationRefundAllocationStatus.REQUIRES_RECONCILIATION
              )
            """)
    BigDecimal sumCommittedRefundAmountForPayment(
            @Param("paymentPublicId")
            UUID paymentPublicId
    );

    @Query("""
            select a
            from ModificationRefundAllocation a
            join fetch a.batch b
            join fetch a.paymentAttempt p
            where b.publicId = :batchPublicId
            and a.status =
                com.spatialstay.modificationrefund.ModificationRefundAllocationStatus.PENDING
            order by a.id asc
            """)
    List<ModificationRefundAllocation>
            findPendingByBatchPublicId(
                    @Param("batchPublicId")
                    UUID batchPublicId
            );

    Optional<ModificationRefundAllocation> findByPublicId(
            UUID publicId
    );
}