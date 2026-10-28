package com.spatialstay.admin;

import java.util.UUID;

public record AdminAuthResponse(
        UUID userPublicId,
        String email,
        String firstName,
        String lastName,
        String role
) {}
