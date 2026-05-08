package com.hris.access.service;

import com.hris.access.entity.AccessProfile;
import com.hris.access.entity.UserProfileAssignment;
import com.hris.access.repository.AccessProfileRepository;
import com.hris.access.repository.UserProfileAssignmentRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccessAssignmentServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AccessProfileRepository accessProfileRepository;

    @Mock
    private UserProfileAssignmentRepository userProfileAssignmentRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserAccessAssignmentService userAccessAssignmentService;

    @Test
    @DisplayName("assignProfile creates a user profile assignment and returns updated profiles")
    void assignProfileCreatesAssignment() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        AccessProfile profile = AccessProfile.builder()
            .id(profileId)
            .code("SELF_SERVICE")
            .displayKey("profile.seed.SELF_SERVICE")
            .isActive(true)
            .build();

        when(userRepository.existsById(userId)).thenReturn(true);
        when(accessProfileRepository.findById(profileId)).thenReturn(Optional.of(profile));
        when(userProfileAssignmentRepository.existsByUserIdAndProfileIdAndIsActiveTrue(userId, profileId)).thenReturn(false);
        when(userProfileAssignmentRepository.findEffectiveByUserId(eq(userId), any(Instant.class))).thenReturn(List.of(
            UserProfileAssignment.builder().profile(profile).profileId(profileId).build()
        ));

        var result = userAccessAssignmentService.assignProfile(userId, profileId, actorId);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().code()).isEqualTo("SELF_SERVICE");
        verify(userProfileAssignmentRepository).save(any(UserProfileAssignment.class));
        verify(auditLogService).log(eq(actorId), any(), eq("user_profile_assignment"), eq(userId), eq(null), eq("SELF_SERVICE"));
    }

    @Test
    @DisplayName("removeProfile soft deletes the active assignment")
    void removeProfileDisablesAssignment() {
        UUID userId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        UserProfileAssignment assignment = UserProfileAssignment.builder()
            .userId(userId)
            .profileId(profileId)
            .isActive(true)
            .build();

        when(userProfileAssignmentRepository.findByUserIdAndProfileIdAndIsActiveTrue(userId, profileId))
            .thenReturn(Optional.of(assignment));

        userAccessAssignmentService.removeProfile(userId, profileId, actorId);

        assertThat(assignment.isActive()).isFalse();
        assertThat(assignment.getExpiresAt()).isNotNull();
        verify(userProfileAssignmentRepository).save(assignment);
        verify(auditLogService).log(eq(actorId), any(), eq("user_profile_assignment"), eq(userId), eq(profileId), eq(null));
    }

    @Test
    @DisplayName("getProfiles rejects unknown users")
    void getProfilesRejectsUnknownUser() {
        UUID userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(false);

        assertThatThrownBy(() -> userAccessAssignmentService.getProfiles(userId))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("User not found");
    }
}
