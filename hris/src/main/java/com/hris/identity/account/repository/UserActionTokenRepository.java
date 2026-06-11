package com.hris.identity.account.repository;

import com.hris.identity.account.entity.UserActionToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserActionTokenRepository extends JpaRepository<UserActionToken, UUID> {

    Optional<UserActionToken> findByTokenHashAndPurpose(String tokenHash, UserActionToken.Purpose purpose);

    @Modifying
    @Query("""
        UPDATE UserActionToken t SET t.usedAt = :now
        WHERE t.userId = :userId AND t.purpose = :purpose AND t.usedAt IS NULL
        """)
    void invalidateOutstanding(@Param("userId") UUID userId,
                               @Param("purpose") UserActionToken.Purpose purpose,
                               @Param("now") Instant now);

    void deleteByUserId(UUID userId);
}
