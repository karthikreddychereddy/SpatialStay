package com.spatialstay.hoteloperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record HotelFinancialOverviewResponse(
        Long hotelId,
        String hotelName,
        Instant from,
        Instant to,
        List<CurrencySummary> currencies,
        List<FinancialTransaction> recentTransactions
) {
    public record CurrencySummary(
            String currency,
            BigDecimal confirmedBookingValue,
            BigDecimal collectedPayments,
            BigDecimal succeededRefunds,
            BigDecimal netCollected,
            BigDecimal pendingRefundObligations,
            long successfulPaymentCount,
            long successfulRefundCount
    ) {}

    public record FinancialTransaction(
            String type,
            UUID publicId,
            UUID bookingPublicId,
            String bookingReference,
            BigDecimal amount,
            String currency,
            String status,
            String purpose,
            Instant occurredAt
    ) {}
}
