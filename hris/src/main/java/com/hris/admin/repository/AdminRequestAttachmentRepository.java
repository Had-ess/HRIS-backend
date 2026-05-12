package com.hris.admin.repository;

import com.hris.admin.entity.AdminRequestAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AdminRequestAttachmentRepository extends JpaRepository<AdminRequestAttachment, UUID> {
    List<AdminRequestAttachment> findByAdminRequestIdOrderByUploadedAtAsc(UUID adminRequestId);
}
