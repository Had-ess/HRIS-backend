package com.hris.leave.service;

import com.hris.leave.entity.LeaveRequest;
import com.hris.leave.enums.LeaveStatus;
import com.hris.leave.repository.LeaveRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LeaveRequestCleanupService {

    private final LeaveRequestRepository leaveRequestRepository;

    @Value("${app.leave.cancelled-cleanup.retention-hours:24}")
    private long retentionHours;

    @Scheduled(cron = "${app.leave.cancelled-cleanup.cron:0 0 * * * *}")
    @SchedulerLock(name = "leaveRequestCancelledCleanupJob", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void cleanupCancelledRequestsJob() {
        try {
            cleanupCancelledRequestsOlderThan(Duration.ofHours(retentionHours));
        } catch (RuntimeException ex) {
            log.error("Cancelled leave request cleanup job failed", ex);
        }
    }

    @Transactional
    public int cleanupCancelledRequestsOlderThan(Duration retention) {
        Instant now = Instant.now();
        Instant cutoff = now.minus(retention);
        List<LeaveRequest> expiredCancelledRequests =
            leaveRequestRepository.findByStatusAndCancelledAtLessThanEqualAndDeletedAtIsNull(
                LeaveStatus.CANCELLED,
                cutoff
            );

        if (expiredCancelledRequests.isEmpty()) {
            log.debug("Cancelled leave request cleanup found no eligible records (cutoff={})", cutoff);
            return 0;
        }

        expiredCancelledRequests.forEach(request -> request.setDeletedAt(now));
        leaveRequestRepository.saveAll(expiredCancelledRequests);

        log.info("Cancelled leave request cleanup soft-deleted {} records older than {} hours",
            expiredCancelledRequests.size(), retention.toHours());
        return expiredCancelledRequests.size();
    }
}
