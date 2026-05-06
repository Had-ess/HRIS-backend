package com.hris.leave.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.FileAttachmentValidationException;
import com.hris.leave.entity.FileAttachment;
import com.hris.leave.repository.FileAttachmentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveAttachmentService Unit Tests")
class LeaveAttachmentServiceTest {

    @Mock
    private FileAttachmentRepository fileAttachmentRepository;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private AuditLogService auditLogService;

    @Test
    @DisplayName("upload stores validated attachment and preserves metadata")
    void uploadStoresValidatedAttachmentAndPreservesMetadata() {
        LeaveAttachmentService service = new LeaveAttachmentService(
            fileAttachmentRepository,
            fileStorageService,
            auditLogService
        );
        UUID requestId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
            "file", "scan.pdf", "application/pdf", "%PDF-demo".getBytes()
        );

        when(fileStorageService.sanitizeFilename("scan.pdf")).thenReturn("scan.pdf");
        when(fileStorageService.store(file, requestId)).thenReturn(requestId + "/scan.pdf");
        when(fileAttachmentRepository.save(any(FileAttachment.class))).thenAnswer(invocation -> {
            FileAttachment attachment = invocation.getArgument(0);
            attachment.setId(UUID.randomUUID());
            return attachment;
        });

        FileAttachment result = service.upload(requestId, file, uploaderId);

        assertThat(result.getRequestId()).isEqualTo(requestId);
        assertThat(result.getFileName()).isEqualTo("scan.pdf");
        assertThat(result.getMimeType()).isEqualTo("application/pdf");
        assertThat(result.getUploadedById()).isEqualTo(uploaderId);
        verify(fileStorageService).store(file, requestId);
        verify(fileAttachmentRepository).save(any(FileAttachment.class));
        verify(auditLogService).log(eq(uploaderId), any(), eq("file_attachment"), eq(result.getId()), eq(null), eq(result));
    }

    @Test
    @DisplayName("upload rejects oversized attachment")
    void uploadRejectsOversizedAttachment() {
        LeaveAttachmentService service = new LeaveAttachmentService(
            fileAttachmentRepository,
            fileStorageService,
            auditLogService
        );
        byte[] payload = new byte[(10 * 1024 * 1024) + 1];
        payload[0] = 0x25;
        payload[1] = 0x50;
        payload[2] = 0x44;
        payload[3] = 0x46;
        payload[4] = 0x2D;
        MockMultipartFile file = new MockMultipartFile(
            "file", "scan.pdf", "application/pdf", payload
        );

        when(fileStorageService.sanitizeFilename("scan.pdf")).thenReturn("scan.pdf");

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), file, UUID.randomUUID()))
            .isInstanceOf(FileAttachmentValidationException.class)
            .hasMessage("Attachment exceeds the maximum allowed size of 10 MB");

        verify(fileStorageService, never()).store(any(), any());
    }

    @Test
    @DisplayName("upload rejects disguised attachment")
    void uploadRejectsDisguisedAttachment() {
        LeaveAttachmentService service = new LeaveAttachmentService(
            fileAttachmentRepository,
            fileStorageService,
            auditLogService
        );
        MockMultipartFile file = new MockMultipartFile(
            "file", "scan.pdf", "application/pdf", "not-a-real-pdf".getBytes()
        );

        when(fileStorageService.sanitizeFilename("scan.pdf")).thenReturn("scan.pdf");

        assertThatThrownBy(() -> service.upload(UUID.randomUUID(), file, UUID.randomUUID()))
            .isInstanceOf(FileAttachmentValidationException.class)
            .hasMessage("Unsupported attachment type. Allowed types: PDF, JPG, JPEG, PNG");

        verify(fileStorageService, never()).store(any(), any());
    }

    @Test
    @DisplayName("download returns resource for matching request attachment")
    void downloadReturnsResourceForMatchingRequestAttachment() {
        LeaveAttachmentService service = new LeaveAttachmentService(
            fileAttachmentRepository,
            fileStorageService,
            auditLogService
        );
        UUID requestId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        FileAttachment attachment = FileAttachment.builder()
            .id(attachmentId)
            .requestId(requestId)
            .fileName("medical_note.pdf")
            .mimeType("application/pdf")
            .storagePath(requestId + "/stored.pdf")
            .build();

        when(fileAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));
        when(fileStorageService.retrieve(attachment.getStoragePath()))
            .thenReturn(new ByteArrayInputStream("pdf".getBytes()));

        AttachmentDownload result = service.download(requestId, attachmentId);

        assertThat(result.fileName()).isEqualTo("medical_note.pdf");
        assertThat(result.mimeType()).isEqualTo("application/pdf");
        assertThat(result.resource()).isNotNull();
    }

    @Test
    @DisplayName("delete rejects attachment that does not belong to request")
    void deleteRejectsAttachmentThatDoesNotBelongToRequest() {
        LeaveAttachmentService service = new LeaveAttachmentService(
            fileAttachmentRepository,
            fileStorageService,
            auditLogService
        );
        UUID requestId = UUID.randomUUID();
        UUID attachmentId = UUID.randomUUID();
        FileAttachment attachment = FileAttachment.builder()
            .id(attachmentId)
            .requestId(UUID.randomUUID())
            .storagePath("other/file.pdf")
            .build();

        when(fileAttachmentRepository.findById(attachmentId)).thenReturn(Optional.of(attachment));

        assertThatThrownBy(() -> service.delete(requestId, attachmentId, UUID.randomUUID()))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("Attachment not found");
    }
}
