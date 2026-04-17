package com.hris.admin.repository;

import com.hris.admin.entity.AdminRequest;
import com.hris.admin.enums.AdminRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminRequestRepository extends JpaRepository<AdminRequest, UUID> {

    Page<AdminRequest> findByRequesterIdOrderBySubmittedAtDesc(UUID requesterId, Pageable pageable);

    Page<AdminRequest> findByStatusInOrderBySubmittedAtDesc(List<AdminRequestStatus> statuses, Pageable pageable);
}
