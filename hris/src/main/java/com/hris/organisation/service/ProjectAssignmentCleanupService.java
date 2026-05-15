package com.hris.organisation.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.organisation.repository.ProjectAssignmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectAssignmentCleanupService {

    private final ProjectAssignmentRepository projectAssignmentRepository;
    private final AuditLogService auditLogService;

    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    @SchedulerLock(name = "projectAssignmentCleanupJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT2M")
    @Transactional
    public void deactivateExpiredAssignments() {
        int deactivated = projectAssignmentRepository.deactivateExpired();
        if (deactivated > 0) {
            log.info("Deactivated {} expired project assignments", deactivated);
            auditLogService.log(null, AuditAction.UPDATE,
                "project_assignment_cleanup", null, null,
                Map.of("deactivatedCount", deactivated));
        }
    }
}
