package com.hris.document.dto;

import com.hris.document.enums.DocumentType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Response records of the documents API (see DOCUMENTS_DESIGN.md §6). */
public final class DocumentDtos {

    private DocumentDtos() {
    }

    public record DocumentDto(
        UUID id,
        UUID employeeId,
        DocumentType docType,
        String title,
        String fileName,
        String mimeType,
        long sizeBytes,
        LocalDate issueDate,
        LocalDate expiryDate,
        String note,
        String uploadedByName,
        Instant createdAt
    ) {
    }

    /** Upload metadata arriving as multipart form fields next to the file part. */
    public record UploadMetadata(
        DocumentType docType,
        String title,
        LocalDate issueDate,
        LocalDate expiryDate,
        String note
    ) {
    }
}
