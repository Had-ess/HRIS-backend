package com.hris.access.repository;

import com.hris.access.entity.UserProfileAssignment;
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

    void deleteByUserId(UUID userId);
}
