package com.hris.health;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class StorageHealthIndicator implements HealthIndicator {

    private final Path attachmentsRootPath;

    public StorageHealthIndicator(
            @Value("${app.storage.attachments-root:./storage/attachments}") String attachmentsRoot) {
        this.attachmentsRootPath = Paths.get(attachmentsRoot).toAbsolutePath().normalize();
    }

    @Override
    public Health health() {
        if (!Files.exists(attachmentsRootPath)) {
            return Health.down()
                .withDetail("path", attachmentsRootPath.toString())
                .withDetail("reason", "attachments root does not exist")
                .build();
        }

        if (!Files.isDirectory(attachmentsRootPath)) {
            return Health.down()
                .withDetail("path", attachmentsRootPath.toString())
                .withDetail("reason", "attachments root is not a directory")
                .build();
        }

        if (!Files.isReadable(attachmentsRootPath) || !Files.isWritable(attachmentsRootPath)) {
            return Health.down()
                .withDetail("path", attachmentsRootPath.toString())
                .withDetail("reason", "attachments root is not readable/writable")
                .build();
        }

        return Health.up()
            .withDetail("path", attachmentsRootPath.toString())
            .build();
    }
}
