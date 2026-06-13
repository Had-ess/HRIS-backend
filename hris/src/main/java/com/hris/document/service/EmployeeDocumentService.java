package com.hris.document.service;

import com.hris.analytics.enums.AuditAction;
import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.repository.UserRepository;
import com.hris.auth.service.EmployeeService;
import com.hris.common.exception.EntityNotFoundException;
import com.hris.common.exception.FileAttachmentValidationException;
import com.hris.document.dto.DocumentDtos.DocumentDto;
import com.hris.document.dto.DocumentDtos.UploadMetadata;
import com.hris.document.entity.EmployeeDocument;
import com.hris.document.repository.EmployeeDocumentRepository;
import com.hris.leave.service.FileStorageService;
import com.hris.security.service.AccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Employee document vault (see DOCUMENTS_DESIGN.md). Both the employee (own
 * vault) and HR (any employee in scope) upload; no verification workflow.
 * Validation rules mirror the leave attachment service: 10 MB, pdf/png/jpeg,
 * magic-byte sniffing.
 */
@Service
@RequiredArgsConstructor
public class EmployeeDocumentService {

    private static final long MAX_DOCUMENT_SIZE_BYTES = 10L * 1024L * 1024L;
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
        "application/pdf", "image/png", "image/jpeg");
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "png", "jpg", "jpeg");
    private static final Map<String, String> MIME_TYPE_BY_EXTENSION = Map.of(
        "pdf", "application/pdf",
        "png", "image/png",
        "jpg", "image/jpeg",
        "jpeg", "image/jpeg");
    private static final byte[] PDF_SIGNATURE = new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D};
    private static final byte[] PNG_SIGNATURE = new byte[] {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] JPEG_SIGNATURE_PREFIX = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private final EmployeeDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final EmployeeService employeeService;
    private final AccessScopeService accessScopeService;
    private final FileStorageService fileStorageService;
    private final AuditLogService auditLogService;

    public record DocumentDownload(String fileName, String mimeType, InputStreamResource resource) {
    }

    @Transactional(readOnly = true)
    public List<DocumentDto> listOwn(UUID userId) {
        Employee self = requireOwnEmployee(userId);
        return toDtos(documentRepository.findByEmployeeIdOrderByCreatedAtDesc(self.getId()));
    }

    @Transactional
    public DocumentDto uploadOwn(UUID userId, MultipartFile file, UploadMetadata metadata) {
        Employee self = requireOwnEmployee(userId);
        return store(self.getId(), file, metadata, userId);
    }

    /** HR view of someone's vault; department scope enforced by EmployeeService.getById. */
    @Transactional(readOnly = true)
    public List<DocumentDto> listFor(UUID employeeId, UUID requesterId) {
        Employee self = accessScopeService.findEmployee(requesterId).orElse(null);
        if (self == null || !self.getId().equals(employeeId)) {
            employeeService.getById(employeeId, requesterId);
        }
        return toDtos(documentRepository.findByEmployeeIdOrderByCreatedAtDesc(employeeId));
    }

    @Transactional
    public DocumentDto uploadFor(UUID employeeId, MultipartFile file, UploadMetadata metadata, UUID actorId) {
        employeeService.getById(employeeId, actorId);
        return store(employeeId, file, metadata, actorId);
    }

    /**
     * Owner always may download their own document; otherwise the caller must
     * hold DOCUMENT_READ (controller-resolved flag) and have the employee in scope.
     */
    @Transactional(readOnly = true)
    public DocumentDownload download(UUID documentId, UUID requesterId, boolean hasScopedRead) {
        EmployeeDocument document = requireAccessible(documentId, requesterId, hasScopedRead);
        return new DocumentDownload(
            document.getFileName(),
            document.getMimeType(),
            new InputStreamResource(fileStorageService.retrieve(document.getStoragePath())));
    }

    @Transactional
    public void delete(UUID documentId, UUID requesterId, boolean hasScopedManage) {
        EmployeeDocument document = requireAccessible(documentId, requesterId, hasScopedManage);
        documentRepository.delete(document);
        fileStorageService.delete(document.getStoragePath());
        auditLogService.log(requesterId, AuditAction.DELETE, "employee_document",
            document.getId(), document, null);
    }

    private EmployeeDocument requireAccessible(UUID documentId, UUID requesterId, boolean hasScopedAccess) {
        EmployeeDocument document = documentRepository.findById(documentId)
            .orElseThrow(() -> new EntityNotFoundException("Document not found"));
        Employee self = accessScopeService.findEmployee(requesterId).orElse(null);
        boolean owner = self != null && self.getId().equals(document.getEmployeeId());
        if (owner) {
            return document;
        }
        if (!hasScopedAccess) {
            throw new AccessDeniedException("You are not allowed to access this document");
        }
        employeeService.getById(document.getEmployeeId(), requesterId);
        return document;
    }

    private DocumentDto store(UUID employeeId, MultipartFile file, UploadMetadata metadata, UUID actorId) {
        validateMetadata(metadata);
        String detectedMimeType = validateFile(file);
        String storagePath = fileStorageService.store(file, employeeId);

        EmployeeDocument saved = documentRepository.save(EmployeeDocument.builder()
            .employeeId(employeeId)
            .docType(metadata.docType())
            .title(metadata.title().trim())
            .fileName(fileStorageService.sanitizeFilename(file.getOriginalFilename()))
            .mimeType(detectedMimeType)
            .storagePath(storagePath)
            .sizeBytes(file.getSize())
            .issueDate(metadata.issueDate())
            .expiryDate(metadata.expiryDate())
            .note(trimmedOrNull(metadata.note()))
            .uploadedByUserId(actorId)
            .build());

        auditLogService.log(actorId, AuditAction.CREATE, "employee_document",
            saved.getId(), null, saved);
        return toDto(saved, resolveUserName(actorId));
    }

    private Employee requireOwnEmployee(UUID userId) {
        return accessScopeService.findEmployee(userId)
            .orElseThrow(() -> new IllegalStateException(
                "Your account has no employee record, so it has no document vault"));
    }

    private void validateMetadata(UploadMetadata metadata) {
        if (metadata.docType() == null) {
            throw new IllegalArgumentException("Document type is required");
        }
        if (metadata.title() == null || metadata.title().isBlank()) {
            throw new IllegalArgumentException("Document title is required");
        }
        if (metadata.title().length() > 150) {
            throw new IllegalArgumentException("Document title must not exceed 150 characters");
        }
        if (metadata.note() != null && metadata.note().length() > 500) {
            throw new IllegalArgumentException("Document note must not exceed 500 characters");
        }
        if (metadata.issueDate() != null && metadata.expiryDate() != null
            && metadata.expiryDate().isBefore(metadata.issueDate())) {
            throw new IllegalArgumentException("Expiry date cannot be before the issue date");
        }
    }

    private String validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileAttachmentValidationException("Document file is required");
        }
        String sanitized = fileStorageService.sanitizeFilename(file.getOriginalFilename());
        int dot = sanitized.lastIndexOf('.');
        String extension = dot >= 0 ? sanitized.substring(dot + 1).toLowerCase() : "";
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileAttachmentValidationException("FILE_EXTENSION_NOT_ALLOWED");
        }
        String detected = detectMimeType(file);
        if (!ALLOWED_MIME_TYPES.contains(detected) || !detected.equals(MIME_TYPE_BY_EXTENSION.get(extension))) {
            throw new FileAttachmentValidationException("FILE_CONTENT_MISMATCH");
        }
        if (file.getSize() > MAX_DOCUMENT_SIZE_BYTES) {
            throw new FileAttachmentValidationException(
                "Document exceeds the maximum allowed size of 10 MB");
        }
        return detected;
    }

    private String detectMimeType(MultipartFile file) {
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
            throw new FileAttachmentValidationException("Failed to read document content");
        }
        throw new FileAttachmentValidationException("FILE_CONTENT_MISMATCH");
    }

    private static boolean startsWith(byte[] actual, byte[] expectedPrefix) {
        if (actual.length < expectedPrefix.length) {
            return false;
        }
        return Arrays.equals(Arrays.copyOf(actual, expectedPrefix.length), expectedPrefix);
    }

    private List<DocumentDto> toDtos(List<EmployeeDocument> documents) {
        return documents.stream()
            .map(d -> toDto(d, resolveUserName(d.getUploadedByUserId())))
            .toList();
    }

    private String resolveUserName(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
            .map(u -> (nullToEmpty(u.getFirstName()) + " " + nullToEmpty(u.getLastName())).trim())
            .filter(name -> !name.isBlank())
            .orElse(null);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String trimmedOrNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static DocumentDto toDto(EmployeeDocument document, String uploadedByName) {
        return new DocumentDto(
            document.getId(),
            document.getEmployeeId(),
            document.getDocType(),
            document.getTitle(),
            document.getFileName(),
            document.getMimeType(),
            document.getSizeBytes(),
            document.getIssueDate(),
            document.getExpiryDate(),
            document.getNote(),
            uploadedByName,
            document.getCreatedAt()
        );
    }
}
