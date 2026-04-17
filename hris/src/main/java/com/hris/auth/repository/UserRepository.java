package com.hris.auth.repository;

import com.hris.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByKeycloakId(String keycloakId);

    Optional<User> findByEmail(String email);

    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :timestamp WHERE u.id = :userId")
    void updateLastLogin(@Param("userId") UUID userId, @Param("timestamp") Instant timestamp);

    @Query("""
        SELECT u FROM User u
        JOIN UserRole ur ON ur.userId = u.id
        JOIN Role r ON r.id = ur.roleId
        WHERE r.code = :roleCode AND ur.isActive = true
        """)
    List<User> findByRole(@Param("roleCode") String roleCode);
}
