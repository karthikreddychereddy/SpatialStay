package com.spatialstay.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository
        extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPublicId(UUID publicId);

    Optional<User> findByGoogleSubject(
            String googleSubject
    );

    boolean existsByEmail(String email);

    long countByRole(UserRole role);
}