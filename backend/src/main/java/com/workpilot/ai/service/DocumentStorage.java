package com.workpilot.ai.service;

import com.workpilot.ai.config.DocumentStorageProperties;
import com.workpilot.ai.exception.ResourceNotFoundException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Component
public class DocumentStorage {

    private final Path root;

    public DocumentStorage(DocumentStorageProperties properties) {
        this.root = Path.of(properties.getDirectory()).toAbsolutePath().normalize();
    }

    public void write(UUID organizationId, UUID documentId, byte[] bytes) {
        Path path = resolve(organizationId, documentId);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new UncheckedIOException("The document could not be stored", exception);
        }
    }

    public Resource read(UUID organizationId, UUID documentId) {
        Path path = resolve(organizationId, documentId);
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Document file is missing");
        }
        return new FileSystemResource(path);
    }

    public void delete(UUID organizationId, UUID documentId) {
        Path path = resolve(organizationId, documentId);
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("The stored document could not be removed", exception);
        }
    }

    private Path resolve(UUID organizationId, UUID documentId) {
        Path path = root.resolve(organizationId.toString()).resolve(documentId.toString()).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Document path is outside the storage directory");
        }
        return path;
    }
}
