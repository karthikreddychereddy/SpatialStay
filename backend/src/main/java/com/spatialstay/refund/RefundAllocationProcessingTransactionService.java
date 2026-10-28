package com.spatialstay.refund;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.*;
import java.time.Instant;
import java.util.*;

@Service
public class RefundAllocationProcessingTransactionService {
 private final RefundAllocationRepository allocations; private final RefundRepository refunds;
 public RefundAllocationProcessingTransactionService(RefundAllocationRepository allocations,RefundRepository refunds){this.allocations=allocations;this.refunds=refunds;}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public Claim claim(UUID id){RefundAllocation a=allocations.findByPublicIdForUpdate(id).orElseThrow(); if(a.getStatus()!=RefundAllocationStatus.PENDING)return null; a.markProcessing(null,Instant.now()); allocations.save(a); refresh(a.getRefund().getPublicId()); return new Claim(a.getPublicId(),a.getProviderPaymentId(),a.getAmount(),a.getCurrency());}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void record(UUID id,RazorpayRefundResult r){RefundAllocation a=allocations.findByPublicIdForUpdate(id).orElseThrow(); validate(a,r); String s=r.providerStatus()==null?"":r.providerStatus().trim().toLowerCase(Locale.ROOT); Instant now=Instant.now(); if("processed".equals(s))a.markSucceeded(r.providerRefundId(),now); else if("pending".equals(s)||"created".equals(s))a.markProcessing(r.providerRefundId(),now); else if("failed".equals(s))a.markFailed("Razorpay reported refund failed.",now); else a.markRequiresReconciliation("Unexpected Razorpay refund status: "+s,now); allocations.save(a); refresh(a.getRefund().getPublicId());}
 @Transactional(propagation=Propagation.REQUIRES_NEW)
 public void uncertain(UUID id){RefundAllocation a=allocations.findByPublicIdForUpdate(id).orElseThrow(); a.markRequiresReconciliation("Refund request outcome is uncertain. Verify with Razorpay before retrying.",Instant.now()); allocations.save(a); refresh(a.getRefund().getPublicId());}
 private void validate(RefundAllocation a,RazorpayRefundResult r){if(r==null||r.providerRefundId()==null||!r.providerRefundId().startsWith("rfnd_")||!a.getProviderPaymentId().equals(r.providerPaymentId())||r.currency()==null||!a.getCurrency().equalsIgnoreCase(r.currency())||r.amountInPaise()!=a.getAmount().movePointRight(2).longValueExact())throw new IllegalStateException("Provider refund does not match cancellation allocation.");}
 private void refresh(UUID refundId){Refund refund=refunds.findByPublicIdForUpdate(refundId).orElseThrow(); List<RefundAllocation> list=allocations.findAllByRefundPublicId(refundId); if(list.isEmpty())return; Instant now=Instant.now(); if(list.stream().allMatch(a->a.getStatus()==RefundAllocationStatus.SUCCEEDED))refund.markAggregateSucceeded(now); else if(list.stream().anyMatch(a->a.getStatus()==RefundAllocationStatus.REQUIRES_RECONCILIATION))refund.markRequiresReconciliation("One or more cancellation refund allocations require reconciliation.",now); else if(list.stream().anyMatch(a->a.getStatus()==RefundAllocationStatus.FAILED))refund.markAggregateFailed("One or more cancellation refund allocations failed.",now); else refund.markAggregateProcessing(now); refunds.save(refund);}
 public record Claim(UUID allocationId,String providerPaymentId,BigDecimal amount,String currency){}
}
