package com.hris.auth.repository;

import com.hris.auth.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    List<UserRole> findByUserIdAndIsActiveTrue(UUID userId);

    List<UserRole> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);

    Optional<UserRole> findByUserIdAndRoleIdAndIsActiveTrue(UUID userId, UUID roleId);

    boolean existsByUserIdAndRoleIdAndIsActiveTrue(UUID userId, UUID roleId);

    @Query("""
        SELECT CASE WHEN COUNT(ur) > 0 THEN true ELSE false END
        FROM UserRole ur
        WHERE ur.roleId = :roleId
          AND ur.isActive = true
          AND (ur.expiresAt IS NULL OR ur.expiresAt > :now)
        """)
    boolean existsEffectiveByRoleId(@Param("roleId") UUID roleId, @Param("now") Instant now);

    @Query("""
        SELECT COUNT(ur)
        FROM UserRole ur
        WHERE ur.roleId = :roleId
          AND ur.isActive = true
          AND (ur.expiresAt IS NULL OR ur.expiresAt > :now)
        """)
    long countEffectiveByRoleId(@Param("roleId") UUID roleId, @Param("now") Instant now);

    @Query("""
        SELECT ur FROM UserRole ur
        JOIN FETCH ur.role r
        WHERE ur.userId = :userId
          AND ur.isActive = true
          AND (ur.expiresAt IS NULL OR ur.expiresAt > :now)
        """)
    List<UserRole> findEffectiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);
}
