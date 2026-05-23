package com.hris.access.service;

import com.hris.access.entity.AccessProfile;
import com.hris.access.entity.ProfileAssignmentRule;
import com.hris.access.entity.UserProfileAssignment;
import com.hris.access.enums.RuleAction;
import com.hris.access.event.StructuralChangeEvent;
import com.hris.access.repository.AccessProfileRepository;
import com.hris.access.repository.ProfileAssignmentRuleRepository;
import com.hris.access.repository.UserProfileAssignmentRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Translates {@link StructuralChangeEvent}s into profile grants/revokes by
 * consulting the active rule set in {@code profile_assignment_rules}.
 *
 * <p><b>Invariant.</b> The engine never overrides a MANUAL assignment. A
 * MANUAL grant is preserved on REVOKE rules; a SYSTEM grant on top of an
 * existing MANUAL one is also skipped to avoid duplicates and keep the
 * provenance accurate. Only SYSTEM-sourced assignments are reconciled.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomaticProfileAssignmentEngine {

    private final ProfileAssignmentRuleRepository ruleRepository;
    private final UserProfileAssignmentRepository assignmentRepository;
    private final AccessProfileRepository accessProfileRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @EventListener
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onStructuralChange(StructuralChangeEvent event) {
        if (event.userId() == null) {
            log.debug("Skipping {} — no userId on event", event.type());
            return;
        }
        if (!userRepository.existsById(event.userId())) {
            log.debug("Skipping {} — user {} no longer exists", event.type(), event.userId());
            return;
        }

        List<ProfileAssignmentRule> rules = ruleRepository
            .findByTriggerEventAndIsActiveTrueOrderByPriorityAsc(event.type());
        if (rules.isEmpty()) {
            return;
        }

        for (ProfileAssignmentRule rule : rules) {
            try {
                applyRule(rule, event);
            } catch (RuntimeException ex) {
                log.warn("Profile assignment rule {} failed for event {} (user={}): {}",
                    rule.getId(), event.type(), event.userId(), ex.getMessage());
            }
        }
    }

    private void applyRule(ProfileAssignmentRule rule, StructuralChangeEvent event) {
        AccessProfile profile = accessProfileRepository.findById(rule.getProfileId()).orElse(null);
        if (profile == null || !profile.isActive()) {
            return;
        }
        if (rule.getAction() == RuleAction.GRANT) {
            grant(event, profile, rule);
        } else {
            revoke(event, profile, rule);
        }
    }

    private void grant(StructuralChangeEvent event, AccessProfile profile, ProfileAssignmentRule rule) {
        Instant now = Instant.now();
        Optional<UserProfileAssignment> existing =
            assignmentRepository.findByUserIdAndProfileIdAndIsActiveTrue(event.userId(), profile.getId());
        if (existing.isPresent()) {
            return;
        }
        UserProfileAssignment assignment = UserProfileAssignment.builder()
            .userId(event.userId())
            .profileId(profile.getId())
            .assignedAt(now)
            .assignedById(event.actorId())
            .isActive(true)
            .assignmentSource("SYSTEM")
            .sourceEvent(event.type().name())
            .sourceRefId(event.refId())
            .build();
        assignmentRepository.save(assignment);
        auditLogService.log(event.actorId(), AuditAction.UPDATE,
            "user_profile_assignment", event.userId(), null,
            "AUTO_GRANT:" + profile.getCode() + ":" + event.type().name());
        log.info("Auto-grant {} -> user {} via rule {} ({})",
            profile.getCode(), event.userId(), rule.getId(), event.type());
    }

    private void revoke(StructuralChangeEvent event, AccessProfile profile, ProfileAssignmentRule rule) {
        Optional<UserProfileAssignment> existing =
            assignmentRepository.findByUserIdAndProfileIdAndIsActiveTrue(event.userId(), profile.getId());
        if (existing.isEmpty()) {
            return;
        }
        UserProfileAssignment assignment = existing.get();
        if (!"SYSTEM".equalsIgnoreCase(assignment.getAssignmentSource())) {
            // Preserve MANUAL grants — humans always win.
            return;
        }
        Instant now = Instant.now();
        assignment.setActive(false);
        assignment.setExpiresAt(now);
        assignmentRepository.save(assignment);
        auditLogService.log(event.actorId(), AuditAction.UPDATE,
            "user_profile_assignment", event.userId(), profile.getCode(),
            "AUTO_REVOKE:" + event.type().name());
        log.info("Auto-revoke {} <- user {} via rule {} ({})",
            profile.getCode(), event.userId(), rule.getId(), event.type());
    }
}
