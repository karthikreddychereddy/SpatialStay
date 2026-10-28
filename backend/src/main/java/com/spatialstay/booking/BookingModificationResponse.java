package com.spatialstay.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BookingModificationResponse(

        UUID modificationId,

        UUID bookingId,

        String bookingReference,

        BookingModificationStatus status,

        ModificationFinancialType financialType,

        String originalRoomSlug,

        LocalDate originalCheckIn,

        LocalDate originalCheckOut,

        Integer originalGuests,

        Integer originalUnits,

        BigDecimal originalTotal,

        String proposedRoomSlug,

        LocalDate proposedCheckIn,

        LocalDate proposedCheckOut,

        Integer proposedGuests,

        Integer proposedUnits,

        BigDecimal proposedPricePerNight,

        BigDecimal proposedTotal,

        BigDecimal priceDifference,

        String currency,

        UUID replacementHoldId,

        Instant holdExpiresAt,

        boolean requiresAdditionalPayment,

        boolean requiresPartialRefund,

        String message
) {
}