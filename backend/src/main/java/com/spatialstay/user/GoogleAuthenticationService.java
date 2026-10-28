package com.spatialstay.user;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class GoogleAuthenticationService
        implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final String GOOGLE_REGISTRATION_ID =
            "google";

    private final UserRepository userRepository;
    private final OidcUserService delegate =
            new OidcUserService();

    public GoogleAuthenticationService(
            UserRepository userRepository
    ) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public OidcUser loadUser(
            OidcUserRequest userRequest
    ) throws OAuth2AuthenticationException {

        if (!GOOGLE_REGISTRATION_ID.equals(
                userRequest
                        .getClientRegistration()
                        .getRegistrationId()
        )) {
            throw oauthError(
                    "unsupported_provider",
                    "Unsupported authentication provider."
            );
        }

        /*
         * Spring Security performs the OIDC protocol work:
         * authorization-code exchange, ID-token validation,
         * signature verification and standard OIDC processing.
         */
        OidcUser oidcUser =
                delegate.loadUser(userRequest);

        String subject = oidcUser.getSubject();
        String email = normalizeEmail(
                oidcUser.getEmail()
        );

        Boolean emailVerified =
                oidcUser.getEmailVerified();

        if (subject == null || subject.isBlank()) {
            throw oauthError(
                    "missing_subject",
                    "Google account identifier is unavailable."
            );
        }

        if (email == null || email.isBlank()) {
            throw oauthError(
                    "missing_email",
                    "Google did not provide an email address."
            );
        }

        if (!Boolean.TRUE.equals(emailVerified)) {
            throw oauthError(
                    "unverified_email",
                    "Google email address is not verified."
            );
        }

        User user = resolveUser(
                subject,
                email,
                oidcUser.getGivenName(),
                oidcUser.getFamilyName()
        );

        ensureAccountCanAuthenticate(user);

        user.recordSuccessfulLogin();

        /*
         * A Google-verified email is considered verified
         * inside SpatialStay as well.
         */
        if (!user.isEmailVerified()) {
            user.markEmailVerified();
        }

        userRepository.save(user);

        /*
         * Keep Google's validated OIDC claims for the duration
         * of the OAuth login. After success we convert the
         * authentication to SpatialStayUserPrincipal.
         */
        return new DefaultOidcUser(
                List.copyOf(
                        oidcUser.getAuthorities()
                ),
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "sub"
        );
    }

    private User resolveUser(
            String subject,
            String email,
            String firstName,
            String lastName
    ) {

        Optional<User> byGoogleSubject =
                userRepository.findByGoogleSubject(
                        subject
                );

        if (byGoogleSubject.isPresent()) {
            User user = byGoogleSubject.get();

            /*
             * The Google subject is our provider identity.
             *
             * If Google reports a different verified email
             * later, update the customer account email only
             * when it does not collide with another account.
             */
            if (!user.getEmail().equals(email)) {
                Optional<User> emailOwner =
                        userRepository.findByEmail(email);

                if (emailOwner.isPresent()
                        && !emailOwner
                                .get()
                                .getId()
                                .equals(user.getId())) {

                    throw oauthError(
                            "email_conflict",
                            "This email address belongs to another account."
                    );
                }

                user.setEmail(email);
                user.markEmailVerified();
            }

            updateNamesIfAvailable(
                    user,
                    firstName,
                    lastName
            );

            return user;
        }

        /*
         * If a verified Google email matches an existing
         * SpatialStay account, link the Google identity
         * instead of creating a duplicate customer.
         */
        Optional<User> byEmail =
                userRepository.findByEmail(email);

        if (byEmail.isPresent()) {
            User existing = byEmail.get();

            if (existing.hasGoogleIdentity()) {
                throw oauthError(
                        "google_identity_conflict",
                        "A different Google identity is already linked."
                );
            }

            existing.linkGoogleIdentity(subject);
            existing.markEmailVerified();

            updateNamesIfAvailable(
                    existing,
                    firstName,
                    lastName
            );

            return existing;
        }

        /*
         * Public Google authentication can create CUSTOMER
         * accounts only.
         */
        User newUser =
                User.createGoogleCustomer(
                        email,
                        subject,
                        safeFirstName(firstName, email),
                        safeLastName(lastName)
                );

        try {
            return userRepository.saveAndFlush(
                    newUser
            );
        } catch (DataIntegrityViolationException exception) {
            /*
             * Handles a rare concurrent first-login race.
             */
            return userRepository
                    .findByGoogleSubject(subject)
                    .orElseThrow(() ->
                            oauthError(
                                    "account_conflict",
                                    "Unable to create Google account."
                            )
                    );
        }
    }

    private void ensureAccountCanAuthenticate(
            User user
    ) {
        if (user.getStatus() == UserStatus.LOCKED) {
            throw oauthError(
                    "account_locked",
                    "Account is locked."
            );
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw oauthError(
                    "account_disabled",
                    "Account is not active."
            );
        }
    }

    private void updateNamesIfAvailable(
            User user,
            String firstName,
            String lastName
    ) {
        if (firstName != null
                && !firstName.isBlank()) {

            user.setFirstName(firstName);
        }

        if (lastName != null
                && !lastName.isBlank()) {

            user.setLastName(lastName);
        }
    }

    private String safeFirstName(
            String firstName,
            String email
    ) {
        if (firstName != null
                && !firstName.isBlank()) {

            return firstName.trim();
        }

        int at = email.indexOf('@');

        String fallback =
                at > 0
                        ? email.substring(0, at)
                        : "SpatialStay";

        if (fallback.length() > 100) {
            fallback = fallback.substring(
                    0,
                    100
            );
        }

        return fallback;
    }

    private String safeLastName(
            String lastName
    ) {
        if (lastName == null
                || lastName.isBlank()) {

            return "";
        }

        String value = lastName.trim();

        if (value.length() > 100) {
            value = value.substring(0, 100);
        }

        return value;
    }

    private String normalizeEmail(
            String email
    ) {
        if (email == null) {
            return null;
        }

        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private OAuth2AuthenticationException oauthError(
            String code,
            String description
    ) {
        return new OAuth2AuthenticationException(
                new OAuth2Error(
                        code,
                        description,
                        null
                )
        );
    }
}