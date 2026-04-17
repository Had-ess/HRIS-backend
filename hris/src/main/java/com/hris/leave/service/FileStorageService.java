package com.hris.leave.service;

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

    @PostConstruct
    public void ensureDirectories() {
        try {
            Files.createDirectories(Paths.get(attachmentsRoot));
            log.info("File storage root initialized: {}", attachmentsRoot);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create storage directory: " + attachmentsRoot, e);
        }
    }

    public String store(MultipartFile file) {
        String objectKey = UUID.randomUUID().toString() + "_" + sanitizeFilename(file.getOriginalFilename());
        Path targetPath = Paths.get(attachmentsRoot).resolve(objectKey);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return objectKey;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + objectKey, e);
        }
    }

    /**
     * Store a file under a specific subdirectory (e.g., per leave request).
     */
    public String store(MultipartFile file, UUID subDirectory) {
        Path subDir = Paths.get(attachmentsRoot).resolve(subDirectory.toString());
        try {
            Files.createDirectories(subDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create subdirectory: " + subDir, e);
        }

        String objectKey = subDirectory + "/" + UUID.randomUUID() + "_" + sanitizeFilename(file.getOriginalFilename());
        Path targetPath = Paths.get(attachmentsRoot).resolve(objectKey);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            return objectKey;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + objectKey, e);
        }
    }

    public InputStream retrieve(String objectKey) {
        Path filePath = Paths.get(attachmentsRoot).resolve(objectKey);
        try {
            return Files.newInputStream(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to retrieve file: " + objectKey, e);
        }
    }

    public void delete(String objectKey) {
        try {
            Files.deleteIfExists(Paths.get(attachmentsRoot).resolve(objectKey));
        } catch (IOException e) {
            log.error("Failed to delete file: {}", objectKey, e);
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null) return "unknown";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
