package com.hris.document.service;

import com.hris.analytics.service.AuditLogService;
import com.hris.auth.entity.Employee;
import com.hris.auth.enums.ContractType;
import com.hris.auth.enums.EmployeeStatus;
import com.hris.auth.repository.UserRepository;
import com.hris.auth.service.EmployeeService;
import com.hris.common.exception.FileAttachmentValidationException;
import com.hris.document.dto.DocumentDtos.DocumentDto;
import com.hris.document.dto.DocumentDtos.UploadMetadata;
import com.hris.document.entity.EmployeeDocument;
import com.hris.document.enums.DocumentType;
import com.hris.document.repository.EmployeeDocumentRepository;
import com.hris.leave.service.FileStorageService;
import com.hris.security.service.AccessScopeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeDocumentServiceTest {

    private static final byte[] PDF_BYTES = "%PDF-1.7 test".getBytes();

    @Mock private EmployeeDocumentRepository documentRepository;
    @Mock private UserRepository userRepository;
    @Mock private EmployeeService employeeService;
    @Mock private AccessScopeService accessScopeService;
    @Mock private FileStorageService fileStorageService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private EmployeeDocumentService service;

    private final UUID userId = UUID.randomUUID();
    private final UUID employeeId = UUID.randomUUID();
    private Employee self;

    @BeforeEach
    void setUp() {
        self = Employee.builder()
            .id(employeeId).userId(userId).employeeCode("EMP-1")
            .hireDate(LocalDate.of(2024, 1, 1))
            .status(EmployeeStatus.ACTIVE).contractType(ContractType.PERMANENT)
            .build();
        lenient().when(accessScopeService.findEmployee(userId)).thenReturn(Optional.of(self));
        lenient().when(fileStorageService.sanitizeFilename(anyString())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(fileStorageService.store(any(), any(UUID.class))).thenReturn("key/file.pdf");
        lenient().when(documentRepository.save(any(EmployeeDocument.class)))
            .thenAnswer(inv -> {
                EmployeeDocument d = inv.getArgument(0);
                if (d.getId() == null) d.setId(UUID.randomUUID());
                return d;
            });
        lenient().when(userRepository.findById(any())).thenReturn(Optional.empty());
    }

    private static MockMultipartFile pdf(String name) {
        return new MockMultipartFile("file", name, "application/pdf", PDF_BYTES);
    }

    @Test
    void uploadOwnStoresUnderOwnEmployee() {
        DocumentDto dto = service.uploadOwn(userId, pdf("cin.pdf"),
            new UploadMetadata(DocumentType.ID_CARD, "  CIN  ", null, LocalDate.of(2030, 1, 1), null));

        assertThat(dto.employeeId()).isEqualTo(employeeId);
        assertThat(dto.title()).isEqualTo("CIN");
        assertThat(dto.mimeType()).isEqualTo("application/pdf");
        verify(fileStorageService).store(any(), org.mockito.ArgumentMatchers.eq(employeeId));
    }

    @Test
    void uploadWithoutEmployeeRecordFails() {
        UUID strangerId = UUID.randomUUID();
        when(accessScopeService.findEmployee(strangerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadOwn(strangerId, pdf("a.pdf"),
            new UploadMetadata(DocumentType.OTHER, "x", null, null, null)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void forbiddenExtensionIsRejected() {
        MockMultipartFile exe = new MockMultipartFile("file", "evil.exe",
            "application/octet-stream", PDF_BYTES);

        assertThatThrownBy(() -> service.uploadOwn(userId, exe,
            new UploadMetadata(DocumentType.OTHER, "x", null, null, null)))
            .isInstanceOf(FileAttachmentValidationException.class);
    }

    @Test
    void contentMismatchIsRejected() {
        MockMultipartFile fakePdf = new MockMultipartFile("file", "doc.pdf",
            "application/pdf", "not a pdf at all".getBytes());

        assertThatThrownBy(() -> service.uploadOwn(userId, fakePdf,
            new UploadMetadata(DocumentType.OTHER, "x", null, null, null)))
            .isInstanceOf(FileAttachmentValidationException.class);
    }

    @Test
    void expiryBeforeIssueIsRejected() {
        assertThatThrownBy(() -> service.uploadOwn(userId, pdf("a.pdf"),
            new UploadMetadata(DocumentType.PASSPORT, "Passport",
                LocalDate.of(2026, 1, 1), LocalDate.of(2025, 1, 1), null)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ownerDownloadsOwnDocumentWithoutScopedRead() {
        EmployeeDocument doc = storedDocument(employeeId);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(fileStorageService.retrieve("key/file.pdf"))
            .thenReturn(new java.io.ByteArrayInputStream(PDF_BYTES));

        var download = service.download(doc.getId(), userId, false);

        assertThat(download.fileName()).isEqualTo("cin.pdf");
    }

    @Test
    void strangerWithoutScopedReadIsDenied() {
        EmployeeDocument doc = storedDocument(UUID.randomUUID());
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));

        assertThatThrownBy(() -> service.download(doc.getId(), userId, false))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void scopedReadGoesThroughEmployeeScopeCheck() {
        UUID otherEmployeeId = UUID.randomUUID();
        EmployeeDocument doc = storedDocument(otherEmployeeId);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));
        when(fileStorageService.retrieve("key/file.pdf"))
            .thenReturn(new java.io.ByteArrayInputStream(PDF_BYTES));

        service.download(doc.getId(), userId, true);

        verify(employeeService).getById(otherEmployeeId, userId);
    }

    @Test
    void deleteRemovesRowAndFile() {
        EmployeeDocument doc = storedDocument(employeeId);
        when(documentRepository.findById(doc.getId())).thenReturn(Optional.of(doc));

        service.delete(doc.getId(), userId, false);

        verify(documentRepository).delete(doc);
        verify(fileStorageService).delete("key/file.pdf");
    }

    private static EmployeeDocument storedDocument(UUID ownerEmployeeId) {
        return EmployeeDocument.builder()
            .id(UUID.randomUUID())
            .employeeId(ownerEmployeeId)
            .docType(DocumentType.ID_CARD)
            .title("CIN")
            .fileName("cin.pdf")
            .mimeType("application/pdf")
            .storagePath("key/file.pdf")
            .sizeBytes(13)
            .build();
    }
}
