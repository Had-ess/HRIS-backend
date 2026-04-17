package com.hris.auth.service;

import com.hris.auth.entity.Role;
import com.hris.auth.repository.RoleRepository;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.InvalidRoleHierarchyException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<Role> getAll() {
        return roleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Role getById(UUID id) {
        return roleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Role not found"));
    }

    @Transactional
    public Role create(Role role) {
        if (role.getParentId() != null) {
            validateNoRoleCycle(null, role.getParentId());
        }
        return roleRepository.save(role);
    }

    @Transactional
    public Role update(UUID id, Role role) {
        Role existing = getById(id);
        UUID proposedParentId = role.getParentId();

        if (proposedParentId != null) {
            validateNoRoleCycle(id, proposedParentId);
        }

        existing.setName(role.getName());
        existing.setCode(role.getCode());
        existing.setActive(role.isActive());
        existing.setParentId(proposedParentId);

        return roleRepository.save(existing);
    }

    @Transactional
    public void deactivate(UUID id) {
        Role existing = getById(id);
        if (existing.isSystemRole()) {
            throw new IllegalStateException("Cannot deactivate a system role");
        }
        existing.setActive(false);
        roleRepository.save(existing);
    }

    void validateNoRoleCycle(UUID roleId, UUID proposedParentId) {
        if (proposedParentId == null) {
            return;
        }
        if (roleId != null && roleId.equals(proposedParentId)) {
            throw new InvalidRoleHierarchyException("Role cannot be its own parent");
        }

        UUID currentRoleId = proposedParentId;
        while (currentRoleId != null) {
            Role currentRole = roleRepository.findById(currentRoleId)
                .orElseThrow(() -> new EntityNotFoundException("Role not found"));

            if (roleId != null && roleId.equals(currentRole.getId())) {
                throw new InvalidRoleHierarchyException("Role hierarchy cycle detected");
            }

            currentRoleId = currentRole.getParentId();
        }
    }
}
