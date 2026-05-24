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
          AND assignment.assignedAt <= CURRENT_TIMESTAMP
          AND (assignment.expiresAt IS NULL OR assignment.expiresAt > CURRENT_TIMESTAMP)
          AND profile.isActive = true
          AND permission.isActive = true
          AND permission.name IN :permissionNames
        ORDER BY u.email ASC
        """)
    List<User> findByPermissionNames(@Param("permissionNames") List<String> permissionNames);

    @Query("""
        SELECT u.id FROM User u
        WHERE LOWER(COALESCE(u.firstName, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
           OR LOWER(COALESCE(u.lastName, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
           OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :searchTerm, '%'))
        ORDER BY u.email ASC
        """)
    List<UUID> searchIds(@Param("searchTerm") String searchTerm);

    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN UserProfileAssignment assignment ON assignment.userId = u.id
        JOIN AccessProfile profile ON profile.id = assignment.profileId
        WHERE assignment.isActive = true
          AND assignment.assignedAt <= CURRENT_TIMESTAMP
          AND (assignment.expiresAt IS NULL OR assignment.expiresAt > CURRENT_TIMESTAMP)
          AND profile.isActive = true
          AND profile.id = :profileId
        ORDER BY u.email ASC
        """)
    List<User> findByAccessProfileId(@Param("profileId") UUID profileId);

    /**
     * Returns users holding the given profile, honoring scoped assignments.
     *
     * <p>MANUAL assignments are always unrestricted (a System Administrator or HR Admin who was
     * granted the profile by hand is never scope-limited). Only SYSTEM-granted assignments enforce
     * scope, and they only match when {@code source_ref_id} equals the supplied
     * {@code scopeEntityId} (typically the department being approved for).
     *
     * @param profileId      profile code id (e.g. DEPT_APPROVER_PROFILE)
     * @param scopeEntityId  the entity the caller is filtering against (e.g. requester's department)
     */
    @Query("""
        SELECT DISTINCT u FROM User u
        JOIN UserProfileAssignment assignment ON assignment.userId = u.id
        JOIN AccessProfile profile ON profile.id = assignment.profileId
        WHERE assignment.isActive = true
          AND assignment.assignedAt <= CURRENT_TIMESTAMP
          AND (assignment.expiresAt IS NULL OR assignment.expiresAt > CURRENT_TIMESTAMP)
          AND profile.isActive = true
          AND profile.id = :profileId
          AND (
                assignment.assignmentSource = 'MANUAL'
             OR (assignment.assignmentSource = 'SYSTEM' AND assignment.sourceRefId = :scopeEntityId)
          )
        ORDER BY u.email ASC
        """)
    List<User> findByAccessProfileIdScopedTo(
        @Param("profileId") UUID profileId,
        @Param("scopeEntityId") UUID scopeEntityId
    );
}
