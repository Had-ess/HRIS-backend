package com.hris.leave.service;

import com.hris.common.exception.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Filesystem-based file storage.
 * Stores at app.storage.attachments-root per spec.
 */
@Slf4j
@Service
public class FileStorageService {

    @Value("${app.storage.attachments-root:./uploads/attachments}")
    private String attachmentsRoot;
    private Path attachmentsRootPath;

    @PostConstruct
    public void ensureDirectories() {
        try {
            attachmentsRootPath = Paths.get(attachmentsRoot).toAbsolutePath().normalize();
            Files.createDirectories(attachmentsRootPath);
            log.info("File storage root initialized: {}", attachmentsRootPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage directory: " + attachmentsRoot, e);
        }
    }

    public String store(MultipartFile file) {
        String objectKey = UUID.randomUUID() + "_" + sanitizeFilename(file.getOriginalFilename());
        Path targetPath = resolveSafePath(objectKey);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return objectKey;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + objectKey, e);
        }
    }

    /**
     * Store a file under a specific subdirectory (e.g., per leave request).
     */
    public String store(MultipartFile file, UUID subDirectory) {
        Path subDir = resolveSafePath(subDirectory.toString());
        try {
            Files.createDirectories(subDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create subdirectory: " + subDir, e);
        }

        String objectKey = subDirectory + "/" + UUID.randomUUID() + "_" + sanitizeFilename(file.getOriginalFilename());
        Path targetPath = resolveSafePath(objectKey);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
            return objectKey;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + objectKey, e);
        }
    }

    public InputStream retrieve(String objectKey) {
        Path filePath = resolveSafePath(objectKey);
        if (!Files.isRegularFile(filePath)) {
            throw new EntityNotFoundException("Attachment file not found");
        }
        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve attachment file", e);
        }
    }

    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(resolveSafePath(objectKey));
        } catch (IllegalArgumentException e) {
            log.warn("Rejected invalid attachment path during delete");
        } catch (IOException e) {
            log.error("Failed to delete file: {}", objectKey, e);
        }
    }

    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unknown";
        }

        String leafName = Path.of(filename).getFileName().toString().trim();
        String sanitized = leafName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return sanitized.isBlank() ? "unknown" : sanitized;
    }

    private Path resolveSafePath(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            throw new IllegalArgumentException("Invalid attachment path");
        }

        Path resolved = attachmentsRootPath.resolve(objectKey).normalize();
        if (!resolved.startsWith(attachmentsRootPath)) {
            throw new IllegalArgumentException("Invalid attachment path");
        }

        return resolved;
    }
}
