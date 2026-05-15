package com.hris.leave.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.FileAttachmentValidationException;
import com.hris.leave.entity.FileAttachment;
import com.hris.leave.repository.FileAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LeaveAttachmentService {

    private static final long MAX_ATTACHMENT_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_ATTACHMENT_MIME_TYPES = Set.of(
        "application/pdf",
        "image/png",
        "image/jpeg"
    );
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = Set.of(
        "pdf",
        "png",
        "jpg",
        "jpeg"
    );
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

    private final FileAttachmentRepository fileAttachmentRepository;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    public FileAttachment upload(UUID requestId, MultipartFile file, UUID uploaderId) {
        String detectedMimeType = validateAttachment(file);
        String sanitizedFileName = sanitizeAttachmentFilename(file.getOriginalFilename());

        String storagePath = fileStorageService.store(file, requestId);

        FileAttachment attachment = FileAttachment.builder()
            .requestId(requestId)
            .fileName(sanitizedFileName)
            .mimeType(detectedMimeType)
            .storagePath(storagePath)
            .uploadedById(uploaderId)
            .uploadedAt(Instant.now())
            .build();

        FileAttachment saved = fileAttachmentRepository.save(attachment);

        auditLogService.log(uploaderId, AuditAction.CREATE, "file_attachment",
            saved.getId(), null, saved);

        return saved;
    }

    public List<FileAttachment> list(UUID requestId) {
        return fileAttachmentRepository.findByRequestId(requestId);
    }

    public AttachmentDownload download(UUID requestId, UUID attachmentId) {
        FileAttachment attachment = findAttachmentForRequest(requestId, attachmentId);

        return new AttachmentDownload(
            attachment.getFileName(),
            attachment.getMimeType(),
            new InputStreamResource(fileStorageService.retrieve(attachment.getStoragePath()))
        );
    }

    public void delete(UUID requestId, UUID attachmentId, UUID requesterId) {
        FileAttachment attachment = findAttachmentForRequest(requestId, attachmentId);

        fileAttachmentRepository.delete(attachment);
        fileStorageService.delete(attachment.getStoragePath());
        auditLogService.log(requesterId, AuditAction.DELETE, "file_attachment",
            attachment.getId(), attachment, null);
    }

    private FileAttachment findAttachmentForRequest(UUID requestId, UUID attachmentId) {
        FileAttachment attachment = fileAttachmentRepository.findById(attachmentId)
            .orElseThrow(() -> new EntityNotFoundException("Attachment not found"));

        if (!attachment.getRequestId().equals(requestId)) {
            throw new EntityNotFoundException("Attachment not found");
        }

        return attachment;
    }

    private String validateAttachment(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileAttachmentValidationException("Attachment file is required");
        }

        String sanitizedFilename = sanitizeAttachmentFilename(file.getOriginalFilename());
        int extensionSeparator = sanitizedFilename.lastIndexOf('.');
        String extension = extensionSeparator >= 0
            ? sanitizedFilename.substring(extensionSeparator + 1).toLowerCase()
            : "";

        if (!ALLOWED_ATTACHMENT_EXTENSIONS.contains(extension)) {
            throw new FileAttachmentValidationException("FILE_EXTENSION_NOT_ALLOWED");
        }

        String detectedMimeType = detectAttachmentMimeType(file);
        if (!ALLOWED_ATTACHMENT_MIME_TYPES.contains(detectedMimeType)) {
            throw new FileAttachmentValidationException("FILE_CONTENT_MISMATCH");
        }

        String expectedMimeType = ATTACHMENT_MIME_TYPE_BY_EXTENSION.get(extension);
        if (!detectedMimeType.equals(expectedMimeType)) {
            throw new FileAttachmentValidationException("FILE_TYPE_MISMATCH");
        }

        if (file.getSize() > MAX_ATTACHMENT_SIZE_BYTES) {
            throw new FileAttachmentValidationException(
                "Attachment exceeds the maximum allowed size of 10 MB");
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
        } catch (IOException e) {
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

    private String sanitizeAttachmentFilename(String originalFilename) {
        String sanitizedFilename = fileStorageService.sanitizeFilename(originalFilename);
        if (sanitizedFilename == null || sanitizedFilename.isBlank()) {
            return "unknown";
        }
        return sanitizedFilename;
    }
}
