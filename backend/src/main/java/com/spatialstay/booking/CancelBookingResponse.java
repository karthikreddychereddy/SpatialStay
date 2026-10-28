package com.spatialstay.booking;

import com.spatialstay.refund.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CancelBookingResponse(

        UUID bookingId,
        String bookingReference,

        BookingStatus status,
        Instant cancelledAt,

        BigDecimal originalAmount,
        BigDecimal cancellationFee,
        BigDecimal refundAmount,
        String currency,

        boolean refundRequired,

        UUID refundId,
        RefundStatus refundStatus,

        String message
) {
}