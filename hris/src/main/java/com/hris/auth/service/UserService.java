package com.hris.auth.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.dto.UpdateCurrentUserDto;
import com.hris.auth.dto.UpdateLocaleDto;
import com.hris.auth.dto.UserResponseDto;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserRoleAssignmentService userRoleAssignmentService;
    private final AuditLogService auditLogService;
    private final KeycloakAdminClient keycloakAdminClient;

    @Transactional(readOnly = true)
    public UserResponseDto getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return toDto(user);
    }

    @Transactional
    public UserResponseDto updateCurrentUser(UUID userId, UpdateCurrentUserDto dto) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        String normalizedEmail = normalizeEmail(dto.email());
        if (!normalizedEmail.equals(user.getEmail())) {
            userRepository.findByEmail(normalizedEmail)
                .filter(existing -> !existing.getId().equals(userId))
                .ifPresent(existing -> {
                    throw new IllegalStateException("User email must be unique");
                });
        }

        Map<String, Object> previousState = profileState(user.getEmail(), user.getFirstName(), user.getLastName());
        String firstName = dto.firstName().trim();
        String lastName = dto.lastName().trim();

        keycloakAdminClient.updateUserProfile(user.getKeycloakId(), normalizedEmail, firstName, lastName);

        user.setEmail(normalizedEmail);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        User saved = userRepository.save(user);

        auditLogService.log(userId, AuditAction.UPDATE, "user", userId,
            previousState, profileState(saved.getEmail(), saved.getFirstName(), saved.getLastName()));

        return toDto(saved);
    }

    @Transactional
    public UserResponseDto updateLocale(UUID userId, UpdateLocaleDto dto) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found"));

        user.setLocalePreference(dto.locale());
        User saved = userRepository.save(user);

        return toDto(saved);
    }

    private UserResponseDto toDto(User user) {
        List<String> effectiveRoles = userRoleAssignmentService.getRoles(user.getId()).stream()
            .map(Role::getCode)
            .filter(code -> code != null && !code.isBlank())
            .sorted(Comparator.naturalOrder())
            .toList();

        return new UserResponseDto(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getLocalePreference(),
            user.isActive(),
            user.getCreatedAt(),
            user.getLastLogin(),
            effectiveRoles
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private Map<String, Object> profileState(String email, String firstName, String lastName) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("email", email);
        state.put("firstName", firstName);
        state.put("lastName", lastName);
        return state;
    }
}
