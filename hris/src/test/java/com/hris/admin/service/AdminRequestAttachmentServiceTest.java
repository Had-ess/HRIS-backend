package com.hris.admin.service;

import com.hris.admin.entity.AdminRequestAttachment;
import com.hris.admin.repository.AdminRequestAttachmentRepository;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.FileAttachmentValidationException;
import com.hris.leave.service.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRequestAttachmentService Unit Tests")
class AdminRequestAttachmentServiceTest {

    @Mock private AdminRequestAttachmentRepository repository;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private AdminRequestAttachmentService service;

    @Test
    @DisplayName("store persists validated attachment metadata")
    void storePersistsValidatedAttachmentMetadata() {
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf",
            new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31});
        when(fileStorageService.sanitizeFilename("proof.pdf")).thenReturn("proof.pdf");
        when(fileStorageService.store(any(), any(UUID.class))).thenReturn("request/proof.pdf");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AdminRequestAttachment result = service.store(UUID.randomUUID(), file, UUID.randomUUID(), false);

        assertThat(result.getFileName()).isEqualTo("proof.pdf");
        assertThat(result.getContentType()).isEqualTo("application/pdf");
        assertThat(result.getSizeBytes()).isPositive();
    }

    @Test
    @DisplayName("store rejects oversized attachment")
    void storeRejectsOversizedAttachment() {
        byte[] content = new byte[10 * 1024 * 1024 + 1];
        content[0] = 0x25;
        content[1] = 0x50;
        content[2] = 0x44;
        content[3] = 0x46;
        content[4] = 0x2D;
        MockMultipartFile file = new MockMultipartFile("file", "proof.pdf", "application/pdf", content);
        when(fileStorageService.sanitizeFilename("proof.pdf")).thenReturn("proof.pdf");

        assertThatThrownBy(() -> service.store(UUID.randomUUID(), file, UUID.randomUUID(), false))
            .isInstanceOf(FileAttachmentValidationException.class)
            .hasMessageContaining("maximum allowed size");
    }
}
