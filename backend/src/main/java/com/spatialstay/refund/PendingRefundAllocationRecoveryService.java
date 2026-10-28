package com.spatialstay.refund;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.UUID;
@Service
public class PendingRefundAllocationRecoveryService {
 private final RefundAllocationRepository repo; private final RefundAllocationProcessingService processing;
 public PendingRefundAllocationRecoveryService(RefundAllocationRepository repo,RefundAllocationProcessingService processing){this.repo=repo;this.processing=processing;}
 @Scheduled(initialDelay=20000,fixedDelay=60000)
 public void recover(){for(UUID refundId:repo.findRefundIdsWithPendingAllocations(PageRequest.of(0,20))){try{processing.submit(refundId);}catch(Exception ignored){/* uncertain allocation is durable; never blind-retry it */}}}
}
