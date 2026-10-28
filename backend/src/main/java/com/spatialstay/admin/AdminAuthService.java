package com.spatialstay.admin;

import com.spatialstay.user.CurrentUser;
import com.spatialstay.user.User;
import com.spatialstay.user.UserRepository;
import com.spatialstay.user.UserRole;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminAuthService {
    private final CurrentUser currentUser;
    private final UserRepository userRepository;

    public AdminAuthService(CurrentUser currentUser, UserRepository userRepository) {
        this.currentUser = currentUser;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public AdminAuthResponse currentAdmin() {
        User user = userRepository.findByPublicId(currentUser.publicId())
                .orElseThrow(() -> new AccessDeniedException("Admin account was not found."));
        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("ADMIN access is required.");
        }
        return new AdminAuthResponse(user.getPublicId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.getRole().name());
    }
}
