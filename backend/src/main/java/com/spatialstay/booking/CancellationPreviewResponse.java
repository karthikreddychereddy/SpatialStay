package com.spatialstay.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record CancellationPreviewResponse(

        UUID bookingId,
        String bookingReference,

        boolean cancellable,
        CancellationEligibilityReason reason,
        String message,

        Instant confirmedAt,
        Instant managementAvailableAt,

        LocalDate checkIn,
        LocalDate checkOut,

        BigDecimal originalAmount,
        BigDecimal cancellationFee,
        BigDecimal refundAmount,
        String currency,

        String policyDescription
) {
}