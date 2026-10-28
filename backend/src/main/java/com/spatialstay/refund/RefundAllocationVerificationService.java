package com.spatialstay.refund;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.UUID;
@Service
public class RefundAllocationVerificationService {
 private final RefundAllocationProcessingTransactionService tx;
 public RefundAllocationVerificationService(RefundAllocationProcessingTransactionService tx){this.tx=tx;}
 @Transactional(propagation=Propagation.NOT_SUPPORTED)
 public ProcessingResult applyAuthoritativeResult(UUID id,RazorpayRefundResult result){try{tx.record(id,result);return ProcessingResult.PROCESSED;}catch(Exception e){try{tx.uncertain(id);}catch(Exception ignored){}return ProcessingResult.REQUIRES_RECONCILIATION;}}
 public enum ProcessingResult{PROCESSED,REQUIRES_RECONCILIATION}
}
