package com.spatialstay.refund;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class RefundAllocationProcessingService {
 private final RefundAllocationRepository repo; private final RefundAllocationProcessingTransactionService tx; private final RazorpayRefundGateway gateway;
 public RefundAllocationProcessingService(RefundAllocationRepository repo,RefundAllocationProcessingTransactionService tx,RazorpayRefundGateway gateway){this.repo=repo;this.tx=tx;this.gateway=gateway;}
 public void submit(UUID refundId){for(RefundAllocation a:repo.findPendingByRefundPublicId(refundId)){var c=tx.claim(a.getPublicId());if(c==null)continue;try{var r=gateway.createCancellationAllocationRefund(c.allocationId(),c.providerPaymentId(),c.amount(),c.currency());tx.record(c.allocationId(),r);}catch(Exception e){try{tx.uncertain(c.allocationId());}catch(Exception x){e.addSuppressed(x);}throw new IllegalStateException("Cancellation refund allocation requires reconciliation.",e);}}}
}
