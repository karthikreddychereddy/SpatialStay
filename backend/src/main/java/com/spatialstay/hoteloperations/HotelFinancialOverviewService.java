package com.spatialstay.hoteloperations;

import com.spatialstay.booking.Booking;
import com.spatialstay.booking.BookingRepository;
import com.spatialstay.hotel.Hotel;
import com.spatialstay.modificationrefund.ModificationRefundBatch;
import com.spatialstay.modificationrefund.ModificationRefundBatchRepository;
import com.spatialstay.modificationrefund.ModificationRefundBatchStatus;
import com.spatialstay.payment.PaymentAttempt;
import com.spatialstay.payment.PaymentAttemptRepository;
import com.spatialstay.refund.Refund;
import com.spatialstay.refund.RefundRepository;
import com.spatialstay.refund.RefundStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class HotelFinancialOverviewService {

    private static final Instant ALL_TIME_START = Instant.parse("2000-01-01T00:00:00Z");
    private static final int RECENT_SOURCE_LIMIT = 20;
    private static final int RECENT_RESULT_LIMIT = 20;

    private final HotelOperationsAccessService accessService;
    private final BookingRepository bookingRepository;
    private final PaymentAttemptRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final ModificationRefundBatchRepository modificationRefundRepository;

    public HotelFinancialOverviewService(
            HotelOperationsAccessService accessService,
            BookingRepository bookingRepository,
            PaymentAttemptRepository paymentRepository,
            RefundRepository refundRepository,
            ModificationRefundBatchRepository modificationRefundRepository
    ) {
        this.accessService = accessService;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.modificationRefundRepository = modificationRefundRepository;
    }

    @Transactional(readOnly = true)
    public HotelFinancialOverviewResponse get(Long requestedHotelId, Instant requestedFrom, Instant requestedTo) {
        Hotel hotel = accessService.resolveAssignedHotel(requestedHotelId);
        accessService.requireHotelHead();

        Instant from = requestedFrom == null ? ALL_TIME_START : requestedFrom;
        Instant to = requestedTo == null ? Instant.now().plusSeconds(1) : requestedTo;
        if (!from.isBefore(to)) {
            throw new IllegalArgumentException("from must be earlier than to.");
        }

        Map<String, Totals> totals = new TreeMap<>();

        for (Object[] row : bookingRepository.summarizeHotelConfirmedBookingValue(hotel.getId(), from, to)) {
            totals(totals, (String) row[0]).confirmedBookingValue = money(row[1]);
        }
        for (Object[] row : paymentRepository.summarizeHotelSuccessfulPayments(hotel.getId(), from, to)) {
            Totals t = totals(totals, (String) row[0]);
            t.collectedPayments = money(row[1]);
            t.successfulPaymentCount = number(row[2]);
        }
        for (Object[] row : refundRepository.summarizeHotelRefunds(hotel.getId(), from, to)) {
            String currency = (String) row[0];
            RefundStatus status = (RefundStatus) row[1];
            BigDecimal amount = money(row[2]);
            long count = number(row[3]);
            Totals t = totals(totals, currency);
            if (status == RefundStatus.SUCCEEDED) {
                t.succeededRefunds = t.succeededRefunds.add(amount);
                t.successfulRefundCount += count;
            } else {
                t.pendingRefundObligations = t.pendingRefundObligations.add(amount);
            }
        }
        for (Object[] row : modificationRefundRepository.summarizeHotelRefundBatches(hotel.getId(), from, to)) {
            String currency = (String) row[0];
            ModificationRefundBatchStatus status = (ModificationRefundBatchStatus) row[1];
            BigDecimal amount = money(row[2]);
            long count = number(row[3]);
            Totals t = totals(totals, currency);
            if (status == ModificationRefundBatchStatus.SUCCEEDED) {
                t.succeededRefunds = t.succeededRefunds.add(amount);
                t.successfulRefundCount += count;
            } else {
                t.pendingRefundObligations = t.pendingRefundObligations.add(amount);
            }
        }

        List<HotelFinancialOverviewResponse.CurrencySummary> currencies = totals.entrySet().stream()
                .map(entry -> entry.getValue().response(entry.getKey()))
                .toList();

        List<HotelFinancialOverviewResponse.FinancialTransaction> transactions = recentTransactions(hotel.getId(), from, to);
        return new HotelFinancialOverviewResponse(hotel.getId(), hotel.getName(), from, to, currencies, transactions);
    }

    private List<HotelFinancialOverviewResponse.FinancialTransaction> recentTransactions(Long hotelId, Instant from, Instant to) {
        var page = PageRequest.of(0, RECENT_SOURCE_LIMIT);
        List<HotelFinancialOverviewResponse.FinancialTransaction> result = new ArrayList<>();

        for (PaymentAttempt p : paymentRepository.findRecentHotelSuccessfulPayments(hotelId, from, to, page)) {
            Booking b = p.getBooking();
            result.add(transaction("PAYMENT", p.getPublicId(), b, p.getExpectedAmount(), p.getCurrency(),
                    p.getStatus().name(), p.getPurpose().name(), p.getSucceededAt()));
        }
        for (Refund r : refundRepository.findRecentHotelRefunds(hotelId, from, to, page)) {
            Booking b = r.getBooking();
            result.add(transaction("CANCELLATION_REFUND", r.getPublicId(), b, r.getRefundAmount(), r.getCurrency(),
                    r.getStatus().name(), "CANCELLATION", refundTime(r)));
        }
        for (ModificationRefundBatch r : modificationRefundRepository.findRecentHotelRefundBatches(hotelId, from, to, page)) {
            Booking b = r.getBooking();
            result.add(transaction("MODIFICATION_REFUND", r.getPublicId(), b, r.getTotalAmount(), r.getCurrency(),
                    r.getStatus().name(), r.getReason().name(), modificationRefundTime(r)));
        }

        return result.stream()
                .sorted(Comparator.comparing(HotelFinancialOverviewResponse.FinancialTransaction::occurredAt).reversed())
                .limit(RECENT_RESULT_LIMIT)
                .toList();
    }

    private HotelFinancialOverviewResponse.FinancialTransaction transaction(
            String type, UUID publicId, Booking booking, BigDecimal amount, String currency,
            String status, String purpose, Instant occurredAt
    ) {
        return new HotelFinancialOverviewResponse.FinancialTransaction(
                type, publicId, booking.getPublicId(), booking.getBookingReference(), amount,
                currency, status, purpose, occurredAt
        );
    }

    private Instant refundTime(Refund refund) {
        return refund.getSucceededAt() != null ? refund.getSucceededAt() : refund.getCreatedAt();
    }

    private Instant modificationRefundTime(ModificationRefundBatch refund) {
        return refund.getSucceededAt() != null ? refund.getSucceededAt() : refund.getCreatedAt();
    }

    private Totals totals(Map<String, Totals> map, String currency) {
        return map.computeIfAbsent(currency, ignored -> new Totals());
    }

    private BigDecimal money(Object value) {
        return value == null ? BigDecimal.ZERO : (BigDecimal) value;
    }

    private long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private static final class Totals {
        private BigDecimal confirmedBookingValue = BigDecimal.ZERO;
        private BigDecimal collectedPayments = BigDecimal.ZERO;
        private BigDecimal succeededRefunds = BigDecimal.ZERO;
        private BigDecimal pendingRefundObligations = BigDecimal.ZERO;
        private long successfulPaymentCount;
        private long successfulRefundCount;

        private HotelFinancialOverviewResponse.CurrencySummary response(String currency) {
            return new HotelFinancialOverviewResponse.CurrencySummary(
                    currency,
                    confirmedBookingValue,
                    collectedPayments,
                    succeededRefunds,
                    collectedPayments.subtract(succeededRefunds),
                    pendingRefundObligations,
                    successfulPaymentCount,
                    successfulRefundCount
            );
        }
    }
}
