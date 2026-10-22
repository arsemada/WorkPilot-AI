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

    public static String storageKey(UUID organizationId, UUID documentId) {
        return organizationId + "/" + documentId;
    }

    public void write(String storageKey, byte[] bytes) {
        Path path = resolve(storageKey);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, bytes);
        } catch (IOException exception) {
            throw new UncheckedIOException("The document could not be stored", exception);
        }
    }

    public Resource read(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Document file is missing");
        }
        return new FileSystemResource(path);
    }

    public byte[] readBytes(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Document file is missing");
        }
        try {
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("The stored document could not be read", exception);
        }
    }

    public void delete(String storageKey) {
        Path path = resolve(storageKey);
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("The stored document could not be removed", exception);
        }
    }

    private Path resolve(String storageKey) {
        Path relative = Path.of(storageKey);
        if (relative.isAbsolute()) {
            throw new IllegalArgumentException("Document path is outside the storage directory");
        }
        for (Path part : relative) {
            String name = part.toString();
            if (name.isBlank() || ".".equals(name) || "..".equals(name)) {
                throw new IllegalArgumentException("Document path is outside the storage directory");
            }
        }
        Path path = root.resolve(relative).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Document path is outside the storage directory");
        }
        return path;
    }
} 
