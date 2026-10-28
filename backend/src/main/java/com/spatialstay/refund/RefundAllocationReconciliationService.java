package com.spatialstay.refund;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
@Service
public class RefundAllocationReconciliationService {
 private final RefundAllocationRepository repo; private final RazorpayRefundGateway gateway; private final RefundAllocationVerificationService verification;
 public RefundAllocationReconciliationService(RefundAllocationRepository repo,RazorpayRefundGateway gateway,RefundAllocationVerificationService verification){this.repo=repo;this.gateway=gateway;this.verification=verification;}
 @Scheduled(initialDelay=30000,fixedDelay=60000)
 public void reconcile(){for(RefundAllocation a:repo.findProcessingWithProviderRefundId(PageRequest.of(0,20))){try{verification.applyAuthoritativeResult(a.getPublicId(),gateway.fetchRefund(a.getProviderRefundId()));}catch(Exception ignored){/* durable PROCESSING state remains for a later pass */}}}
}
