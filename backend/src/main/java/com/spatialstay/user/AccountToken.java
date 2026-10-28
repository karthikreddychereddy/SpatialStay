package com.spatialstay.user;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "account_tokens",
        indexes = {
                @Index(
                        name = "idx_account_tokens_hash",
                        columnList = "token_hash",
                        unique = true
                ),
                @Index(
                        name = "idx_account_tokens_user_type",
                        columnList = "user_id, token_type"
                )
        }
)
public class AccountToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(
            name = "token_hash",
            nullable = false,
            unique = true,
            length = 64
    )
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "token_type", nullable = false, length = 40)
    private AccountTokenType tokenType;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AccountToken() {
    }

    public AccountToken(
            User user,
            String tokenHash,
            AccountTokenType tokenType,
            Instant expiresAt
    ) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public User getUser() {
        return user;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isUsable() {
        return usedAt == null
                && expiresAt.isAfter(Instant.now());
    }

    public void markUsed() {
        this.usedAt = Instant.now();
    }
}