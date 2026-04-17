package com.hris.leave.repository;

import com.hris.leave.entity.FileAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface FileAttachmentRepository extends JpaRepository<FileAttachment, UUID> {
    List<FileAttachment> findByRequestId(UUID requestId);
}
