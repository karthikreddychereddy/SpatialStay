package com.spatialstay.admin;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public final class AdminOperationsModels {
    private AdminOperationsModels() {}

    public record HotelSummary(Long id,String slug,String name,String city,String state,String country,Boolean verified,String status,BigDecimal rating,Integer reviewCount,BigDecimal startingPrice,String contactEmail,LocalDateTime createdAt) {}
    public record UserSummary(UUID publicId,String email,String firstName,String lastName,String role,String status,boolean emailVerified,Instant createdAt,Instant lastLoginAt) {}
    public record BookingSummary(UUID publicId,String reference,UUID customerPublicId,String customerEmail,Long hotelId,String hotelName,LocalDate checkIn,LocalDate checkOut,Integer guests,Integer rooms,BigDecimal totalAmount,String currency,String status,Instant createdAt,Instant confirmedAt) {}
    public record PaymentSummary(UUID publicId,UUID bookingPublicId,String bookingReference,String customerEmail,String provider,String providerOrderId,String providerPaymentId,BigDecimal amount,String currency,String status,Instant createdAt,Instant succeededAt) {}
    public record RefundSummary(UUID publicId,UUID bookingPublicId,String bookingReference,String customerEmail,String provider,String providerPaymentId,String providerRefundId,BigDecimal refundAmount,String currency,String status,String failureReason,Instant createdAt,Instant succeededAt) {}
    public record ReconciliationSummary(Long id,UUID paymentAttemptPublicId,UUID bookingPublicId,String provider,String providerOrderId,String providerPaymentId,String bookingStatusAtDiscovery,String status,Instant discoveredAt) {}
    public record AuditSummary(Long id,UUID adminPublicId,String adminEmail,String action,String targetType,String targetId,String detail,Instant createdAt) {}
    public record Analytics(long hotels,long users,long customers,long hotelHeads,long hotelStaff,long bookings,long confirmedBookings,long cancelledBookings,long paymentAttempts,long successfulPayments,long refunds,long successfulRefunds,long pendingApplications,long reconciliationCases,BigDecimal confirmedBookingValue,BigDecimal successfulPaymentValue,BigDecimal successfulRefundValue) {}
}
