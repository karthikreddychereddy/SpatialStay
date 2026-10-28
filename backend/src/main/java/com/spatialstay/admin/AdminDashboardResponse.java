package com.spatialstay.admin;

public record AdminDashboardResponse(
        long hotels,
        long users,
        long bookings,
        long payments,
        long pendingApplications,
        long reconciliationCases
) {}
