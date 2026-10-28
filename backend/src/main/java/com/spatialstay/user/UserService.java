package com.spatialstay.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse registerCustomer(
            RegisterRequest request
    ) {
        String normalizedEmail =
                normalizeEmail(request.email());

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "An account with this email already exists."
            );
        }

        PasswordPolicy.validate(request.password());

        String passwordHash =
                passwordEncoder.encode(request.password());

        User user = new User(
                normalizedEmail,
                passwordHash,
                request.firstName(),
                request.lastName()
        );

        try {
            User savedUser =
                    userRepository.saveAndFlush(user);

            return UserResponse.from(savedUser);

        } catch (DataIntegrityViolationException exception) {

            /*
             * The database unique constraint is the final
             * protection against concurrent duplicate
             * registration requests.
             */
            throw new IllegalArgumentException(
                    "An account with this email already exists."
            );
        }
    }

    private String normalizeEmail(String email) {
        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserByPublicId(
            java.util.UUID publicId
    ) {
        User user = userRepository
                .findByPublicId(publicId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User account was not found."
                        )
                );

        return UserResponse.from(user);
    }

    @Transactional
    public void recordSuccessfulLogin(
            java.util.UUID publicId
    ) {
        User user = userRepository
                .findByPublicId(publicId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User account was not found."
                        )
                );

        user.recordSuccessfulLogin();
    }
}