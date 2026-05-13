package com.hris.access.service;

import com.hris.access.dto.UserProfileSummaryDto;
import com.hris.access.entity.AccessProfile;
import com.hris.access.entity.UserProfileAssignment;
import com.hris.access.repository.AccessProfileRepository;
import com.hris.access.repository.UserProfileAssignmentRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAccessAssignmentService {

    private final UserRepository userRepository;
    private final AccessProfileRepository accessProfileRepository;
    private final UserProfileAssignmentRepository userProfileAssignmentRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<UserProfileSummaryDto> getProfiles(UUID userId) {
        ensureUser(userId);
        return userProfileAssignmentRepository.findEffectiveByUserId(userId, Instant.now()).stream()
            .map(UserProfileAssignment::getProfile)
            .filter(profile -> profile != null)
            .sorted(Comparator.comparing(AccessProfile::getCode))
            .map(profile -> new UserProfileSummaryDto(
                profile.getId(),
                profile.getCode(),
                profile.getDisplayKey(),
                profile.isActive()
            ))
            .toList();
    }

    @Transactional
    public List<UserProfileSummaryDto> assignProfile(UUID userId, UUID profileId, UUID actorId) {
        ensureUser(userId);
        Instant now = Instant.now();
        AccessProfile profile = accessProfileRepository.findById(profileId)
            .orElseThrow(() -> new EntityNotFoundException("Access profile not found"));
        if (userProfileAssignmentRepository.findEffectiveByUserIdAndProfileId(userId, profileId, now).isPresent()) {
            throw new IllegalStateException("Access profile already assigned to user");
        }
        userProfileAssignmentRepository.save(UserProfileAssignment.builder()
            .userId(userId)
            .profileId(profile.getId())
            .assignedById(actorId)
            .assignedAt(now)
            .isActive(true)
            .build());
        auditLogService.log(actorId, AuditAction.UPDATE, "user_profile_assignment", userId, null, profile.getCode());
        return getProfiles(userId);
    }

    @Transactional
    public void removeProfile(UUID userId, UUID profileId, UUID actorId) {
        Instant now = Instant.now();
        if (userId.equals(actorId) && userProfileAssignmentRepository.countEffectiveByUserId(userId, now) <= 1) {
            throw new IllegalStateException("You cannot remove your last active access profile");
        }
        UserProfileAssignment assignment = userProfileAssignmentRepository
            .findEffectiveByUserIdAndProfileId(userId, profileId, now)
            .orElseThrow(() -> new EntityNotFoundException("User profile assignment not found"));
        assignment.setActive(false);
        assignment.setExpiresAt(now);
        userProfileAssignmentRepository.save(assignment);
        auditLogService.log(actorId, AuditAction.DELETE, "user_profile_assignment", userId, profileId, null);
    }

    private void ensureUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found");
        }
    }
}
