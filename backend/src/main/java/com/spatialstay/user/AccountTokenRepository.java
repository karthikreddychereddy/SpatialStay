package com.spatialstay.user;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountTokenRepository
        extends JpaRepository<AccountToken, Long> {

    /*
     * Used when consuming a security token.
     *
     * The database row is locked until the surrounding
     * transaction completes, preventing two concurrent
     * requests from successfully consuming the same token.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select token
            from AccountToken token
            where token.tokenHash = :tokenHash
              and token.tokenType = :tokenType
            """)
    Optional<AccountToken> findForUpdateByTokenHashAndTokenType(
            @Param("tokenHash") String tokenHash,
            @Param("tokenType") AccountTokenType tokenType
    );

    Optional<AccountToken>
    findTopByUserAndTokenTypeOrderByCreatedAtDesc(
            User user,
            AccountTokenType tokenType
    );

    void deleteByUserAndTokenType(
            User user,
            AccountTokenType tokenType
    );
}