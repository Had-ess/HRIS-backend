package com.hris.access.repository;

import com.hris.access.entity.UserProfileAssignment;
import com.hris.auth.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserProfileAssignmentRepository extends JpaRepository<UserProfileAssignment, UUID> {

    @Query("""
        SELECT assignment FROM UserProfileAssignment assignment
        JOIN FETCH assignment.profile profile
        WHERE assignment.userId = :userId
          AND assignment.isActive = true
          AND assignment.assignedAt <= :now
          AND profile.isActive = true
          AND (assignment.expiresAt IS NULL OR assignment.expiresAt > :now)
        """)
    List<UserProfileAssignment> findEffectiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
        SELECT assignment FROM UserProfileAssignment assignment
        WHERE assignment.userId = :userId
          AND assignment.profileId = :profileId
          AND assignment.isActive = true
          AND assignment.assignedAt <= :now
          AND (assignment.expiresAt IS NULL OR assignment.expiresAt > :now)
        """)
    Optional<UserProfileAssignment> findEffectiveByUserIdAndProfileId(
        @Param("userId") UUID userId,
        @Param("profileId") UUID profileId,
        @Param("now") Instant now
    );

    @Query("""
        SELECT COUNT(assignment) FROM UserProfileAssignment assignment
        WHERE assignment.userId = :userId
          AND assignment.isActive = true
          AND assignment.assignedAt <= :now
          AND (assignment.expiresAt IS NULL OR assignment.expiresAt > :now)
        """)
    long countEffectiveByUserId(@Param("userId") UUID userId, @Param("now") Instant now);

    List<UserProfileAssignment> findByUserId(UUID userId);

    Optional<UserProfileAssignment> findByUserIdAndProfileIdAndIsActiveTrue(UUID userId, UUID profileId);

    boolean existsByUserIdAndProfileIdAndIsActiveTrue(UUID userId, UUID profileId);

    long countByProfileIdAndIsActiveTrue(UUID profileId);

    @Query("""
        SELECT DISTINCT e FROM Employee e
        JOIN User u ON u.id = e.userId
        JOIN UserProfileAssignment upa ON upa.userId = u.id
        JOIN AccessProfile ap ON ap.id = upa.profileId
        WHERE ap.code = :profileCode
          AND upa.isActive = true
          AND (upa.expiresAt IS NULL OR upa.expiresAt > :now)
          AND (
                upa.assignmentSource = 'MANUAL'
             OR (upa.assignmentSource = 'SYSTEM' AND upa.sourceRefId = :scopeEntityId)
          )
          AND u.id != :excludeUserId
        ORDER BY e.employeeCode ASC
        """)
    List<Employee> findEmployeesWithScopedProfile(
        @Param("profileCode") String profileCode,
        @Param("scopeEntityId") UUID scopeEntityId,
        @Param("excludeUserId") UUID excludeUserId,
        @Param("now") Instant now
    );

    void deleteByUserId(UUID userId);
}
