package com.spatialstay.hoteloperations;

import com.spatialstay.user.UserRole;

import java.util.List;
import java.util.UUID;

public record HotelOperationsLoginResponse(
        UUID userPublicId,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        List<HotelAssignmentSummary> hotels
) {
    public record HotelAssignmentSummary(
            Long id,
            String slug,
            String name,
            String city,
            String country
    ) {}
}
