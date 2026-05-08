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
        SELECT DISTINCT u FROM User u
        JOIN UserProfileAssignment assignment ON assignment.userId = u.id
        JOIN AccessProfile profile ON profile.id = assignment.profileId
        JOIN ProfilePermission profilePermission ON profilePermission.profileId = profile.id
        JOIN Permission permission ON permission.id = profilePermission.permissionId
        WHERE assignment.isActive = true
          AND profile.isActive = true
          AND permission.isActive = true
          AND permission.name IN :permissionNames
        ORDER BY u.email ASC
        """)
    List<User> findByPermissionNames(@Param("permissionNames") List<String> permissionNames);

    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN UserProfileAssignment assignment ON assignment.userId = u.id
        JOIN AccessProfile profile ON profile.id = assignment.profileId
        WHERE assignment.isActive = true
          AND profile.isActive = true
          AND profile.id = :profileId
        ORDER BY u.email ASC
        """)
    List<User> findByAccessProfileId(@Param("profileId") UUID profileId);
}
