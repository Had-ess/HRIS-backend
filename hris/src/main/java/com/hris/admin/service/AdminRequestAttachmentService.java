package com.hris.admin.service;

import com.hris.admin.entity.AdminRequestAttachment;
import com.hris.admin.repository.AdminRequestAttachmentRepository;
import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.FileAttachmentValidationException;
import com.hris.leave.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminRequestAttachmentService {

    private static final long MAX_ATTACHMENT_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_ATTACHMENT_MIME_TYPES = Set.of(
        "application/pdf",
        "image/png",
        "image/jpeg"
    );
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg");
    private static final Map<String, String> ATTACHMENT_MIME_TYPE_BY_EXTENSION = Map.of(
        "pdf", "application/pdf",
        "png", "image/png",
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg"
    );
    private static final byte[] PDF_SIGNATURE = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D};
    private static final byte[] PNG_SIGNATURE = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] JPEG_SIGNATURE_PREFIX = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final AdminRequestAttachmentRepository attachmentRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    public AdminRequestAttachment store(UUID adminRequestId, MultipartFile file, UUID uploaderUserId, boolean responseDocument) {
        String contentType = validateAttachment(file);
        String sanitizedFileName = fileStorageService.sanitizeFilename(file.getOriginalFilename());
        String storagePath = fileStorageService.store(file, adminRequestId);

        AdminRequestAttachment saved = attachmentRepository.save(AdminRequestAttachment.builder()
            .adminRequestId(adminRequestId)
            .fileName(sanitizedFileName)
            .contentType(contentType)
            .sizeBytes(file.getSize())
            .storagePath(storagePath)
            .responseDocument(responseDocument)
            .uploadedByUserId(uploaderUserId)
            .uploadedAt(Instant.now())
            .build());

        auditLogService.log(uploaderUserId, AuditAction.CREATE, "admin_request_attachment",
            saved.getId(), null, saved);
        return saved;
    }

    private String validateAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileAttachmentValidationException("Attachment file is required");
        }

        String sanitizedFilename = fileStorageService.sanitizeFilename(file.getOriginalFilename());
        int extensionSeparator = sanitizedFilename.lastIndexOf('.');
        String extension = extensionSeparator >= 0
            ? sanitizedFilename.substring(extensionSeparator + 1).toLowerCase()
            : "";

        if (!ALLOWED_ATTACHMENT_EXTENSIONS.contains(extension)) {
            throw new FileAttachmentValidationException("FILE_EXTENSION_NOT_ALLOWED");
        }

        if (file.getSize() > MAX_ATTACHMENT_SIZE_BYTES) {
            throw new FileAttachmentValidationException(
                "Attachment exceeds the maximum allowed size of 10 MB");
        }

        String detectedMimeType = detectAttachmentMimeType(file);
        if (!ALLOWED_ATTACHMENT_MIME_TYPES.contains(detectedMimeType)) {
            throw new FileAttachmentValidationException("FILE_CONTENT_MISMATCH");
        }

        String expectedMimeType = ATTACHMENT_MIME_TYPE_BY_EXTENSION.get(extension);
        if (!detectedMimeType.equals(expectedMimeType)) {
            throw new FileAttachmentValidationException("FILE_TYPE_MISMATCH");
        }

        String declaredContentType = file.getContentType();
        if (declaredContentType != null
            && !declaredContentType.isBlank()
            && !detectedMimeType.equals(declaredContentType.toLowerCase())) {
            throw new FileAttachmentValidationException("FILE_CONTENT_MISMATCH");
        }

        return detectedMimeType;
    }

    private String detectAttachmentMimeType(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(PNG_SIGNATURE.length);
            if (startsWith(header, PDF_SIGNATURE)) {
                return "application/pdf";
            }
            if (startsWith(header, PNG_SIGNATURE)) {
                return "image/png";
            }
            if (startsWith(header, JPEG_SIGNATURE_PREFIX)) {
                return "image/jpeg";
            }
        } catch (IOException ex) {
            throw new FileAttachmentValidationException("Failed to read attachment content");
        }

        throw new FileAttachmentValidationException("FILE_CONTENT_MISMATCH");
    }

    private boolean startsWith(byte[] actual, byte[] expectedPrefix) {
        if (actual.length < expectedPrefix.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOf(actual, expectedPrefix.length), expectedPrefix);
    }
}
