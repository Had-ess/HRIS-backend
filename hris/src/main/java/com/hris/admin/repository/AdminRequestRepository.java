package com.hris.admin.repository;

import com.hris.admin.entity.AdminRequest;
import com.hris.admin.enums.AdminRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminRequestRepository extends JpaRepository<AdminRequest, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ar FROM AdminRequest ar WHERE ar.id = :id")
    java.util.Optional<AdminRequest> findByIdForUpdate(@Param("id") UUID id);

    Page<AdminRequest> findByRequesterIdOrderBySubmittedAtDesc(UUID requesterId, Pageable pageable);

    Page<AdminRequest> findByStatusInOrderBySubmittedAtDesc(List<AdminRequestStatus> statuses, Pageable pageable);

    List<AdminRequest> findTop5ByRequesterIdOrderBySubmittedAtDesc(UUID requesterId);

    long countByStatusIn(List<AdminRequestStatus> statuses);

    List<AdminRequest> findTop5ByStatusInOrderBySubmittedAtDesc(List<AdminRequestStatus> statuses);

    boolean existsByRequesterId(UUID requesterId);

    boolean existsByResolvedById(UUID resolvedById);
}
