package com.hris.auth.service;

import com.hris.auth.dto.AccountProvisioningRequest;
import com.hris.auth.dto.AdminUserCreateDto;
import com.hris.auth.dto.AdminUserResponseDto;
import com.hris.auth.entity.Role;
import com.hris.auth.entity.User;
import com.hris.auth.repository.UserRepository;
import com.hris.auth.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final AccountProvisioningService accountProvisioningService;

    @Transactional(readOnly = true)
    public List<AdminUserResponseDto> getAll() {
        return userRepository.findAll().stream()
            .sorted((left, right) -> left.getEmail().compareToIgnoreCase(right.getEmail()))
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public AdminUserResponseDto create(AdminUserCreateDto dto, UUID actorId) {
        if (dto.roleIds() == null || dto.roleIds().isEmpty()) {
            throw new IllegalArgumentException("At least one role must be assigned");
        }

        User saved = accountProvisioningService.provision(new AccountProvisioningRequest(
            dto.username(),
            dto.email(),
            dto.firstName(),
            dto.lastName(),
            dto.password(),
            dto.temporaryPassword() != null && dto.temporaryPassword(),
            dto.roleIds()
        ), actorId);
        return toDto(saved);
    }

    private AdminUserResponseDto toDto(User user) {
        List<String> roles = userRoleRepository.findEffectiveByUserId(user.getId(), Instant.now()).stream()
            .map(userRole -> userRole.getRole())
            .filter(java.util.Objects::nonNull)
            .map(Role::getCode)
            .sorted()
            .toList();

        return new AdminUserResponseDto(
            user.getId(),
            user.getKeycloakId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.isActive(),
            user.getCreatedAt(),
            user.getLastLogin(),
            roles
        );
    }
}
