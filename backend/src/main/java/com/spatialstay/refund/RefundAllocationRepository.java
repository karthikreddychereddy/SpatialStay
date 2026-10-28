package com.spatialstay.refund;

import com.spatialstay.payment.PaymentProvider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.*;

public interface RefundAllocationRepository extends JpaRepository<RefundAllocation,Long> {
 Optional<RefundAllocation> findByPublicId(UUID publicId);
 Optional<RefundAllocation> findByProviderAndProviderRefundId(PaymentProvider provider,String providerRefundId);
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select a from RefundAllocation a join fetch a.refund r join fetch a.paymentAttempt p where a.publicId=:id") Optional<RefundAllocation> findByPublicIdForUpdate(@Param("id") UUID id);
 @Query("select a from RefundAllocation a join fetch a.paymentAttempt p where a.refund.publicId=:id order by a.id asc") List<RefundAllocation> findAllByRefundPublicId(@Param("id") UUID id);
 @Query("select a from RefundAllocation a where a.refund.publicId=:id and a.status=com.spatialstay.refund.RefundAllocationStatus.PENDING order by a.id asc") List<RefundAllocation> findPendingByRefundPublicId(@Param("id") UUID id);
 @Query("select coalesce(sum(a.amount),0) from RefundAllocation a where a.paymentAttempt.publicId=:paymentId and a.status in (com.spatialstay.refund.RefundAllocationStatus.PENDING,com.spatialstay.refund.RefundAllocationStatus.PROCESSING,com.spatialstay.refund.RefundAllocationStatus.SUCCEEDED,com.spatialstay.refund.RefundAllocationStatus.REQUIRES_RECONCILIATION)") BigDecimal sumCommittedForPayment(@Param("paymentId") UUID paymentId);
 @Query("select a from RefundAllocation a where a.status=com.spatialstay.refund.RefundAllocationStatus.PROCESSING and a.providerRefundId is not null order by a.id asc") List<RefundAllocation> findProcessingWithProviderRefundId(org.springframework.data.domain.Pageable pageable);
 @Query("select distinct a.refund.publicId from RefundAllocation a where a.status=com.spatialstay.refund.RefundAllocationStatus.PENDING order by a.refund.publicId") List<UUID> findRefundIdsWithPendingAllocations(org.springframework.data.domain.Pageable pageable);
}
