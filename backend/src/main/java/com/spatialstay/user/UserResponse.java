package com.spatialstay.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(

        UUID publicId,
        String email,
        String firstName,
        String lastName,
        UserRole role,
        boolean emailVerified,
        Instant createdAt

) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getPublicId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole(),
                user.isEmailVerified(),
                user.getCreatedAt()
        );
    }
}