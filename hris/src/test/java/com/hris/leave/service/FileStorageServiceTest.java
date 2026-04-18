package com.hris.leave.service;

import com.hris.common.exception.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileStorageService Unit Tests")
class FileStorageServiceTest {

    @TempDir
    Path tempDir;

    private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        fileStorageService = new FileStorageService();
        ReflectionTestUtils.setField(fileStorageService, "attachmentsRoot", tempDir.toString());
        fileStorageService.ensureDirectories();
    }

    @Test
    @DisplayName("should fail safely when attachment path is invalid")
    void shouldFailSafely_WhenAttachmentPathIsInvalid() {
        assertThatThrownBy(() -> fileStorageService.retrieve("../outside.txt"))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessage("Attachment file not found");
    }

    @Test
    @DisplayName("should return stored attachment content when path is valid")
    void shouldRetrieveStoredAttachment_WhenPathIsValid() throws Exception {
        Path requestDir = Files.createDirectories(tempDir.resolve("request-1"));
        Path storedFile = requestDir.resolve("scan.pdf");
        Files.writeString(storedFile, "pdf-content");

        try (InputStream inputStream = fileStorageService.retrieve("request-1/scan.pdf")) {
            assertThat(new String(inputStream.readAllBytes())).isEqualTo("pdf-content");
        }
    }
}
