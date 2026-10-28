package com.spatialstay.refund;

import com.spatialstay.booking.Booking;
import com.spatialstay.modificationrefund.ModificationRefundAllocationRepository;
import com.spatialstay.payment.*;
import org.springframework.stereotype.Service;
import java.math.*;
import java.time.Instant;
import java.util.*;

@Service
public class RefundAllocationService {
 private final PaymentAttemptRepository payments; private final ModificationRefundAllocationRepository modificationRefunds; private final RefundAllocationRepository allocations;
 public RefundAllocationService(PaymentAttemptRepository payments,ModificationRefundAllocationRepository modificationRefunds,RefundAllocationRepository allocations){this.payments=payments;this.modificationRefunds=modificationRefunds;this.allocations=allocations;}
 public void createAllocations(Refund refund,Booking booking,BigDecimal required,String currency,Instant now){
  BigDecimal remaining=required.setScale(2,RoundingMode.UNNECESSARY); if(remaining.signum()<=0)throw new IllegalArgumentException("Refund amount must be positive.");
  List<PaymentAttempt> captured=payments.findAllSucceededByBookingForUpdate(booking.getPublicId()); if(captured.isEmpty())throw new IllegalStateException("Booking has no successful captured payments.");
  for(PaymentAttempt p:captured){ if(remaining.signum()==0)break; validate(booking,p,currency); BigDecimal mod=nvl(modificationRefunds.sumCommittedRefundAmountForPayment(p.getPublicId())); BigDecimal cancel=nvl(allocations.sumCommittedForPayment(p.getPublicId())); BigDecimal available=p.getExpectedAmount().subtract(mod).subtract(cancel).setScale(2,RoundingMode.UNNECESSARY); if(available.signum()<=0)continue; BigDecimal amount=available.min(remaining); allocations.save(RefundAllocation.create(refund,p,amount,currency,now)); remaining=remaining.subtract(amount); }
  if(remaining.signum()!=0)throw new IllegalStateException("Insufficient refundable captured balance for cancellation.");
 }
 private BigDecimal nvl(BigDecimal v){return v==null?BigDecimal.ZERO.setScale(2):v.setScale(2,RoundingMode.UNNECESSARY);}
 private void validate(Booking b,PaymentAttempt p,String c){if(p.getStatus()!=PaymentStatus.SUCCEEDED||p.getProvider()!=PaymentProvider.RAZORPAY)throw new IllegalStateException("Invalid captured payment in cancellation ledger."); if(p.getBooking()==null||!b.getPublicId().equals(p.getBooking().getPublicId()))throw new IllegalStateException("Payment does not belong to booking."); if(p.getProviderPaymentId()==null||p.getProviderPaymentId().isBlank())throw new IllegalStateException("Captured payment is missing provider payment ID."); if(p.getCurrency()==null||!p.getCurrency().equalsIgnoreCase(c))throw new IllegalStateException("Payment currency mismatch.");}
}
