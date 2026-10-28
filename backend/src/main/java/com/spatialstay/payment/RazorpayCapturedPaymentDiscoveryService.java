package com.spatialstay.payment;

import com.spatialstay.booking.BookingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.List;
import java.util.UUID;

/**
 * Phase 11.15B: bounded, read-only discovery of missed captured payments.
 * Does not confirm expired bookings or initiate refunds. The authoritative
 * reconciliation transition must be implemented separately and tested.
 */
@Service
@ConditionalOnProperty(prefix = "spatialstay.payment.reconciliation",
        name = "discovery-enabled", havingValue = "true")
public class RazorpayCapturedPaymentDiscoveryService {
    private static final Logger log = LoggerFactory.getLogger(
            RazorpayCapturedPaymentDiscoveryService.class);
    private final PaymentAttemptRepository attempts;
    private final RazorpayOrderPaymentLookup lookup;
    private final CapturedPaymentReconciliationCaseRepository cases;

    public RazorpayCapturedPaymentDiscoveryService(
            PaymentAttemptRepository attempts, RazorpayOrderPaymentLookup lookup,
            CapturedPaymentReconciliationCaseRepository cases) {
        this.attempts = attempts;
        this.lookup = lookup;
        this.cases = cases;
    }

    @Scheduled(fixedDelayString =
            "${spatialstay.payment.reconciliation.discovery-scan-ms:120000}")
    public void discover() {
        // IDs are read in a short transaction. No database lock is held
        // while performing outbound HTTP requests to Razorpay.
        for (UUID id : candidateIds()) {
            try {
                inspect(id);
            } catch (RuntimeException ex) {
                log.warn("Razorpay reconciliation lookup deferred for attempt {}: {}",
                        id, ex.getClass().getSimpleName());
            }
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> candidateIds() {
        return attempts.findOriginalBookingReconciliationCandidates(PageRequest.of(0, 50));
    }

    /** No database writes. Operator must resolve captured/expired cases. */
    public void inspect(UUID paymentAttemptId) {
        PaymentAttempt attempt = attempts.findByPublicIdWithBooking(paymentAttemptId).orElse(null);
        if (attempt == null || cases.existsByPaymentAttemptPublicId(paymentAttemptId)
                || attempt.getProvider() != PaymentProvider.RAZORPAY
                || attempt.getPurpose() != PaymentPurpose.ORIGINAL_BOOKING
                || attempt.getProviderOrderId() == null
                || !(attempt.getStatus() == PaymentStatus.PENDING
                    || attempt.getStatus() == PaymentStatus.PROCESSING
                    || attempt.getStatus() == PaymentStatus.EXPIRED)) {
            return;
        }
        List<VerifiedRazorpayPayment> captured = lookup.findCapturedPayments(
                attempt.getProviderOrderId(), attempt.getExpectedAmount(), attempt.getCurrency());
        if (captured.isEmpty()) {
            return;
        }
        BookingStatus bookingStatus = attempt.getBooking().getStatus();
        // Provider verification above happens without a database transaction or row lock.
        // Unique database constraints prevent duplicate attempt/payment cases.
        cases.saveAndFlush(new CapturedPaymentReconciliationCase(
                attempt.getPublicId(), attempt.getBooking().getPublicId(),
                attempt.getProviderOrderId(), captured.get(0).paymentId(), bookingStatus.name()));
        log.error("CAPTURED_PAYMENT_RECONCILIATION_CASE_RECORDED attempt={} booking={} "
                        + "bookingStatus={} providerOrder={} providerPayment={} "
                        + "(audit case persisted; no booking/payment/refund mutation)",
                attempt.getPublicId(), attempt.getBooking().getPublicId(),
                bookingStatus, attempt.getProviderOrderId(), captured.get(0).paymentId());
    }
}
