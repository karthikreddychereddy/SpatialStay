package com.spatialstay.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class AccountSecurityService {

    private static final SecureRandom SECURE_RANDOM =
            new SecureRandom();

    private static final Duration TOKEN_REQUEST_COOLDOWN =
            Duration.ofSeconds(60);

    private final UserRepository userRepository;
    private final AccountTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final CurrentUser currentUser;

    @Value("${app.auth.expose-development-tokens:false}")
    private boolean exposeDevelopmentTokens;

    public AccountSecurityService(
            UserRepository userRepository,
            AccountTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            CurrentUser currentUser
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.currentUser = currentUser;
    }

    @Transactional
    public Optional<String> createEmailVerificationToken() {

        User user = requireCurrentUser();

        if (user.isEmailVerified()) {
            return Optional.empty();
        }

        if (hasRecentToken(
                user,
                AccountTokenType.EMAIL_VERIFICATION
        )) {
            throw new IllegalArgumentException(
                    "Please wait before requesting another verification email."
            );
        }

        return Optional.of(
                createToken(
                        user,
                        AccountTokenType.EMAIL_VERIFICATION,
                        Duration.ofHours(24)
                )
        );
    }

    @Transactional
    public void verifyEmail(String rawToken) {

        AccountToken token =
                requireUsableToken(
                        rawToken,
                        AccountTokenType.EMAIL_VERIFICATION
                );

        token.getUser().markEmailVerified();
        token.markUsed();
    }

    @Transactional
    public Optional<String> requestPasswordReset(
            String email
    ) {
        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        Optional<User> user =
                userRepository.findByEmail(normalizedEmail);

        /*
        * The caller deliberately receives the same public
        * response whether or not the account exists.
        */
        if (user.isEmpty()) {
            return Optional.empty();
        }

        /*
        * Do not continuously regenerate reset tokens.
        *
        * Returning empty here also preserves the generic
        * forgot-password response.
        */
        if (hasRecentToken(
                user.get(),
                AccountTokenType.PASSWORD_RESET
        )) {
            return Optional.empty();
        }

        return Optional.of(
                createToken(
                        user.get(),
                        AccountTokenType.PASSWORD_RESET,
                        Duration.ofMinutes(30)
                )
        );
    }

    @Transactional
    public void resetPassword(
            String rawToken,
            String newPassword
    ) {
        PasswordPolicy.validate(newPassword);

        AccountToken token =
                requireUsableToken(
                        rawToken,
                        AccountTokenType.PASSWORD_RESET
                );

        User user = token.getUser();

        user.setPasswordHash(
                passwordEncoder.encode(newPassword)
        );

        token.markUsed();
    }

    @Transactional
    public void changePassword(
            String currentPassword,
            String newPassword
    ) {
        PasswordPolicy.validate(newPassword);

        User user = requireCurrentUser();

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(
                        currentPassword,
                        user.getPasswordHash()
                )) {

            throw new IllegalArgumentException(
                    "Current password is incorrect."
            );
        }

        if (passwordEncoder.matches(
                newPassword,
                user.getPasswordHash()
        )) {
            throw new IllegalArgumentException(
                    "New password must be different from the current password."
            );
        }

        user.setPasswordHash(
                passwordEncoder.encode(newPassword)
        );

        tokenRepository.deleteByUserAndTokenType(
                user,
                AccountTokenType.PASSWORD_RESET
        );
    }

    public boolean exposeDevelopmentTokens() {
        return exposeDevelopmentTokens;
    }

    private User requireCurrentUser() {
        return userRepository
                .findByPublicId(currentUser.publicId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user no longer exists."
                        )
                );
    }

    private boolean hasRecentToken(
            User user,
            AccountTokenType type
    ) {
        return tokenRepository
                .findTopByUserAndTokenTypeOrderByCreatedAtDesc(
                        user,
                        type
                )
                .map(token ->
                        token.getCreatedAt().isAfter(
                                Instant.now().minus(
                                        TOKEN_REQUEST_COOLDOWN
                                )
                        )
                )
                .orElse(false);
    }

    private String createToken(
            User user,
            AccountTokenType type,
            Duration lifetime
    ) {
        tokenRepository.deleteByUserAndTokenType(
                user,
                type
        );

        String rawToken = generateSecureToken();

        AccountToken token =
                new AccountToken(
                        user,
                        hashToken(rawToken),
                        type,
                        Instant.now().plus(lifetime)
                );

        tokenRepository.save(token);

        return rawToken;
    }

    private AccountToken requireUsableToken(
            String rawToken,
            AccountTokenType type
    ) {
        AccountToken token =
                tokenRepository
                        .findForUpdateByTokenHashAndTokenType(
                                hashToken(rawToken),
                                type
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Token is invalid or expired."
                                )
                        );

        if (!token.isUsable()) {
            throw new IllegalArgumentException(
                    "Token is invalid or expired."
            );
        }

        return token;
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            byte[] hash =
                    digest.digest(
                            rawToken.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable.",
                    exception
            );
        }
    }
}