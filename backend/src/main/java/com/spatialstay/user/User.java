package com.spatialstay.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(
                        name = "idx_users_email",
                        columnList = "email",
                        unique = true
                ),
                @Index(
                        name = "idx_users_public_id",
                        columnList = "public_id",
                        unique = true
                ),
                @Index(
                        name = "idx_users_google_subject",
                        columnList = "google_subject",
                        unique = true
                ),
                @Index(
                        name = "idx_users_role_status",
                        columnList = "role,status"
                )
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "public_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private UUID publicId;

    @Column(
            name = "email",
            nullable = false,
            unique = true,
            length = 320
    )
    private String email;

    /*
     * Null is allowed for Google-only accounts.
     *
     * Password accounts always contain a one-way password hash.
     */
    @Column(
            name = "password_hash",
            length = 255
    )
    private String passwordHash;

    /*
     * Stable Google OpenID Connect subject identifier.
     *
     * We use Google's "sub" claim as the provider identity,
     * rather than relying on email as the permanent Google ID.
     */
    @Column(
            name = "google_subject",
            unique = true,
            length = 255
    )
    private String googleSubject;

    @Column(
            name = "first_name",
            nullable = false,
            length = 100
    )
    private String firstName;

    @Column(
            name = "last_name",
            nullable = false,
            length = 100
    )
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "role",
            nullable = false,
            length = 30
    )
    private UserRole role = UserRole.CUSTOMER;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "status",
            nullable = false,
            length = 30
    )
    private UserStatus status = UserStatus.ACTIVE;

    @Column(
            name = "email_verified",
            nullable = false
    )
    private boolean emailVerified = false;

    @Column(
            name = "created_at",
            nullable = false,
            updatable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    protected User() {
    }

    /*
     * Password-based CUSTOMER account.
     */
    public User(
            String email,
            String passwordHash,
            String firstName,
            String lastName
    ) {
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.firstName = cleanName(firstName);
        this.lastName = cleanName(lastName);

        this.role = UserRole.CUSTOMER;
        this.status = UserStatus.ACTIVE;
        this.emailVerified = false;
    }

    /*
     * Creates a hotel operations account.
     *
     * This factory is intentionally separate from public registration so a
     * browser cannot self-assign HOTEL_STAFF or HOTEL_HEAD. Provisioning is
     * performed only by trusted hotel/admin workflows.
     */
    public static User createHotelOperator(
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            UserRole role
    ) {
        if (role != UserRole.HOTEL_STAFF
                && role != UserRole.HOTEL_HEAD) {
            throw new IllegalArgumentException(
                    "Hotel operator role must be HOTEL_STAFF or HOTEL_HEAD."
            );
        }

        User user = new User(
                email,
                passwordHash,
                firstName,
                lastName
        );

        user.role = role;
        user.emailVerified = true;
        return user;
    }

    /*
     * Creates a Google-only CUSTOMER account.
     *
     * Role/status/verification are deliberately decided
     * server-side and cannot come from the browser.
     */
    public static User createGoogleCustomer(
            String email,
            String googleSubject,
            String firstName,
            String lastName
    ) {
        User user = new User();

        user.email = normalizeEmail(email);
        user.passwordHash = null;
        user.googleSubject = requireGoogleSubject(
                googleSubject
        );
        user.firstName = cleanName(firstName);
        user.lastName = cleanName(lastName);

        user.role = UserRole.CUSTOMER;
        user.status = UserStatus.ACTIVE;

        /*
         * This must only be called after the Google OIDC
         * response has been successfully validated and the
         * email_verified claim has been confirmed.
         */
        user.emailVerified = true;

        return user;
    }

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();

        if (publicId == null) {
            publicId = UUID.randomUUID();
        }

        email = normalizeEmail(email);
        firstName = cleanName(firstName);
        lastName = cleanName(lastName);

        if (googleSubject != null) {
            googleSubject = googleSubject.trim();
        }

        if (role == null) {
            role = UserRole.CUSTOMER;
        }

        if (status == null) {
            status = UserStatus.ACTIVE;
        }

        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        email = normalizeEmail(email);
        firstName = cleanName(firstName);
        lastName = cleanName(lastName);

        if (googleSubject != null) {
            googleSubject = googleSubject.trim();
        }

        updatedAt = Instant.now();
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }

        return email
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static String cleanName(String value) {
        if (value == null) {
            return null;
        }

        return value.trim();
    }

    private static String requireGoogleSubject(
            String googleSubject
    ) {
        if (googleSubject == null
                || googleSubject.isBlank()) {

            throw new IllegalArgumentException(
                    "Google subject cannot be empty."
            );
        }

        return googleSubject.trim();
    }

    public Long getId() {
        return id;
    }

    public UUID getPublicId() {
        return publicId;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getGoogleSubject() {
        return googleSubject;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public boolean hasPassword() {
        return passwordHash != null
                && !passwordHash.isBlank();
    }

    public boolean hasGoogleIdentity() {
        return googleSubject != null
                && !googleSubject.isBlank();
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
        this.emailVerified = false;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public void setFirstName(String firstName) {
        this.firstName = cleanName(firstName);
    }

    public void setLastName(String lastName) {
        this.lastName = cleanName(lastName);
    }

    /*
     * Links a validated Google identity to an existing
     * SpatialStay account.
     */
    public void linkGoogleIdentity(
            String googleSubject
    ) {
        String normalizedSubject =
                requireGoogleSubject(googleSubject);

        if (this.googleSubject != null
                && !this.googleSubject.equals(
                        normalizedSubject
                )) {

            throw new IllegalStateException(
                    "A different Google identity is already linked."
            );
        }

        this.googleSubject = normalizedSubject;
    }

    public void changeRole(UserRole role) {
        if (role == null) {
            throw new IllegalArgumentException(
                    "User role cannot be null."
            );
        }

        this.role = role;
    }

    public void changeStatus(UserStatus status) {
        if (status == null) {
            throw new IllegalArgumentException(
                    "User status cannot be null."
            );
        }

        this.status = status;
    }

    public void markEmailVerified() {
        this.emailVerified = true;
    }

    public void recordSuccessfulLogin() {
        this.lastLoginAt = Instant.now();
    }
}