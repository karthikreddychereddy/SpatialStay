package com.spatialstay.hoteloperations;

import com.spatialstay.user.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record HotelStaffResponse(
        UUID userId,
        String email,
        String firstName,
        String lastName,
        UserStatus accountStatus,
        boolean assignmentActive,
        Instant assignedAt,
        Instant assignmentUpdatedAt
) {}
