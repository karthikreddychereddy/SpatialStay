package com.spatialstay.user;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUser {

    public SpatialStayUserPrincipal principal() {
        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof SpatialStayUserPrincipal principal)) {

            throw new IllegalStateException(
                    "Authenticated user is required."
            );
        }

        return principal;
    }

    public UUID publicId() {
        return principal().getPublicId();
    }

    public String email() {
        return principal().getUsername();
    }

    public UserRole role() {
        return principal().getRole();
    }
}