package com.spatialstay.refund;

import com.spatialstay.payment.PaymentAttempt;
import com.spatialstay.payment.PaymentProvider;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name="refund_allocations", uniqueConstraints={
 @UniqueConstraint(name="uk_refund_allocation_public_id",columnNames="public_id"),
 @UniqueConstraint(name="uk_refund_allocation_refund_payment",columnNames={"refund_id","payment_attempt_id"})
}, indexes={@Index(name="idx_refund_allocation_refund",columnList="refund_id"),@Index(name="idx_refund_allocation_provider_refund",columnList="provider,provider_refund_id")})
public class RefundAllocation {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="public_id",nullable=false,unique=true,updatable=false) private UUID publicId;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="refund_id",nullable=false,updatable=false) private Refund refund;
 @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="payment_attempt_id",nullable=false,updatable=false) private PaymentAttempt paymentAttempt;
 @Enumerated(EnumType.STRING) @Column(nullable=false,updatable=false,length=30) private PaymentProvider provider;
 @Column(name="provider_payment_id",nullable=false,updatable=false,length=120) private String providerPaymentId;
 @Column(nullable=false,updatable=false,precision=19,scale=2) private BigDecimal amount;
 @Column(nullable=false,updatable=false,length=3) private String currency;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=40) private RefundAllocationStatus status;
 @Column(name="provider_refund_id",length=120) private String providerRefundId;
 @Column(name="failure_reason",length=1000) private String failureReason;
 @Column(name="created_at",nullable=false,updatable=false) private Instant createdAt;
 @Column(name="updated_at",nullable=false) private Instant updatedAt;
 @Version @Column(nullable=false) private Long version;
 protected RefundAllocation() {}
 public static RefundAllocation create(Refund refund, PaymentAttempt payment, BigDecimal amount, String currency, Instant now) {
  Objects.requireNonNull(refund); Objects.requireNonNull(payment); Objects.requireNonNull(now);
  if(payment.getProvider()!=PaymentProvider.RAZORPAY || payment.getProviderPaymentId()==null || payment.getProviderPaymentId().isBlank()) throw new IllegalArgumentException("Refund allocation requires a captured Razorpay payment.");
  BigDecimal normalized=amount.setScale(2,RoundingMode.UNNECESSARY); if(normalized.signum()<=0 || normalized.compareTo(payment.getExpectedAmount())>0) throw new IllegalArgumentException("Invalid refund allocation amount.");
  RefundAllocation a=new RefundAllocation(); a.publicId=UUID.randomUUID(); a.refund=refund; a.paymentAttempt=payment; a.provider=payment.getProvider(); a.providerPaymentId=payment.getProviderPaymentId().trim(); a.amount=normalized; a.currency=currency.trim().toUpperCase(Locale.ROOT); a.status=RefundAllocationStatus.PENDING; a.createdAt=now; a.updatedAt=now; return a;
 }
 void markProcessing(String id,Instant now){ if(status==RefundAllocationStatus.SUCCEEDED)return; if(id!=null&&!id.isBlank()){ if(providerRefundId!=null&&!providerRefundId.equals(id.trim()))throw new IllegalStateException("Provider refund ID cannot change."); providerRefundId=id.trim(); } status=RefundAllocationStatus.PROCESSING; failureReason=null; updatedAt=now; }
 void markSucceeded(String id,Instant now){ if(id==null||id.isBlank())throw new IllegalArgumentException("Provider refund ID required."); if(providerRefundId!=null&&!providerRefundId.equals(id.trim()))throw new IllegalStateException("Provider refund ID cannot change."); providerRefundId=id.trim(); status=RefundAllocationStatus.SUCCEEDED; failureReason=null; updatedAt=now; }
 void markFailed(String reason,Instant now){ if(status==RefundAllocationStatus.SUCCEEDED)return; status=RefundAllocationStatus.FAILED; failureReason=reason; updatedAt=now; }
 void markRequiresReconciliation(String reason,Instant now){ if(status==RefundAllocationStatus.SUCCEEDED)return; status=RefundAllocationStatus.REQUIRES_RECONCILIATION; failureReason=reason; updatedAt=now; }
 public UUID getPublicId(){return publicId;} public Refund getRefund(){return refund;} public PaymentAttempt getPaymentAttempt(){return paymentAttempt;} public PaymentProvider getProvider(){return provider;} public String getProviderPaymentId(){return providerPaymentId;} public BigDecimal getAmount(){return amount;} public String getCurrency(){return currency;} public RefundAllocationStatus getStatus(){return status;} public String getProviderRefundId(){return providerRefundId;}
}
