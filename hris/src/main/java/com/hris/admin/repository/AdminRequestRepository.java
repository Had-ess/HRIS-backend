package com.hris.admin.repository;

import com.hris.admin.entity.AdminRequest;
import com.hris.admin.enums.AdminRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AdminRequestRepository extends JpaRepository<AdminRequest, UUID>, JpaSpecificationExecutor<AdminRequest> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ar FROM AdminRequest ar WHERE ar.id = :id")
    java.util.Optional<AdminRequest> findByIdForUpdate(@Param("id") UUID id);

    Page<AdminRequest> findByRequesterUserIdOrderByCreatedAtDesc(UUID requesterUserId, Pageable pageable);

    List<AdminRequest> findTop5ByRequesterUserIdOrderByCreatedAtDesc(UUID requesterUserId);

    List<AdminRequest> findTop5ByStatusInOrderBySubmittedAtDesc(List<AdminRequestStatus> statuses);

    long countByStatusIn(List<AdminRequestStatus> statuses);

    boolean existsByRequesterUserId(UUID requesterUserId);
    boolean existsByProcessedByUserId(UUID processedByUserId);
    boolean existsByTypeId(UUID typeId);

    @Query("""
        SELECT ar FROM AdminRequest ar
        WHERE ar.dueAt IS NOT NULL
          AND ar.dueAt < :now
          AND ar.status IN :statuses
          AND (ar.slaNotifiedAt IS NULL OR ar.slaNotifiedAt < :renotifyThreshold)
        ORDER BY ar.dueAt ASC
        """)
    List<AdminRequest> findOverdueRequests(
        @Param("now") Instant now,
        @Param("statuses") List<AdminRequestStatus> statuses,
        @Param("renotifyThreshold") Instant renotifyThreshold
    );
}
