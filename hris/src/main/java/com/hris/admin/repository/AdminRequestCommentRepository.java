package com.hris.admin.repository;

import com.hris.admin.entity.AdminRequestComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminRequestCommentRepository extends JpaRepository<AdminRequestComment, UUID> {
    List<AdminRequestComment> findByAdminRequestIdOrderByCreatedAtAsc(UUID adminRequestId);
}
