package com.hris.access.service;

import com.hris.access.dto.ProfileAssignmentRuleResponseDto;
import com.hris.access.dto.ProfileAssignmentRuleUpdateDto;
import com.hris.access.entity.AccessProfile;
import com.hris.access.entity.ProfileAssignmentRule;
import com.hris.access.repository.AccessProfileRepository;
import com.hris.access.repository.ProfileAssignmentRuleRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProfileAssignmentRuleService {

    private final ProfileAssignmentRuleRepository ruleRepository;
    private final AccessProfileRepository accessProfileRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<ProfileAssignmentRuleResponseDto> getAll() {
        List<ProfileAssignmentRule> rules = ruleRepository.findAllByOrderByTriggerEventAscPriorityAsc();
        Map<UUID, AccessProfile> profilesById = accessProfileRepository
            .findByIdIn(rules.stream().map(ProfileAssignmentRule::getProfileId).distinct().toList())
            .stream()
            .collect(java.util.stream.Collectors.toMap(AccessProfile::getId, profile -> profile));
        return rules.stream()
            .map(rule -> toDto(rule, profilesById.get(rule.getProfileId())))
            .toList();
    }

    @Transactional(readOnly = true)
    public ProfileAssignmentRuleResponseDto getById(UUID id) {
        ProfileAssignmentRule rule = ruleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Profile assignment rule not found"));
        return toDto(rule, accessProfileRepository.findById(rule.getProfileId()).orElse(null));
    }

    @Transactional
    public ProfileAssignmentRuleResponseDto update(UUID id, ProfileAssignmentRuleUpdateDto dto, UUID actorId) {
        ProfileAssignmentRule rule = ruleRepository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Profile assignment rule not found"));

        Map<String, Object> previous = snapshot(rule);

        if (dto.profileId() != null && !dto.profileId().equals(rule.getProfileId())) {
            AccessProfile target = accessProfileRepository.findById(dto.profileId())
                .orElseThrow(() -> new EntityNotFoundException("Target access profile not found"));
            if (!target.isActive()) {
                throw new IllegalStateException("Cannot target an inactive access profile");
            }
            rule.setProfileId(target.getId());
        }
        if (dto.action() != null) {
            rule.setAction(dto.action());
        }
        if (dto.scopeStrategy() != null) {
            rule.setScopeStrategy(dto.scopeStrategy());
        }
        if (dto.priority() != null) {
            rule.setPriority(dto.priority());
        }
        if (dto.active() != null) {
            rule.setActive(dto.active());
        }
        if (dto.description() != null) {
            rule.setDescription(dto.description());
        }
        rule.setUpdatedAt(Instant.now());

        ProfileAssignmentRule saved = ruleRepository.save(rule);
        auditLogService.log(actorId, AuditAction.UPDATE, "profile_assignment_rule",
            saved.getId(), previous, snapshot(saved));

        return toDto(saved, accessProfileRepository.findById(saved.getProfileId()).orElse(null));
    }

    private ProfileAssignmentRuleResponseDto toDto(ProfileAssignmentRule rule, AccessProfile profile) {
        return new ProfileAssignmentRuleResponseDto(
            rule.getId(),
            rule.getTriggerEvent(),
            rule.getProfileId(),
            profile != null ? profile.getCode() : null,
            profile != null ? profile.getDisplayKey() : null,
            rule.getAction(),
            rule.getScopeStrategy(),
            rule.getPriority(),
            rule.isActive(),
            rule.getDescription(),
            rule.getCreatedAt(),
            rule.getUpdatedAt()
        );
    }

    private Map<String, Object> snapshot(ProfileAssignmentRule rule) {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("triggerEvent", rule.getTriggerEvent());
        state.put("profileId", rule.getProfileId());
        state.put("action", rule.getAction());
        state.put("scopeStrategy", rule.getScopeStrategy());
        state.put("priority", rule.getPriority());
        state.put("isActive", rule.isActive());
        state.put("description", rule.getDescription());
        return state;
    }
}
