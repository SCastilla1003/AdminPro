package com.adminpro.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

@Service
@ConditionalOnProperty(name = "storage.backend", havingValue = "local")
public class LocalStorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageService.class);

    @Value("${upload.dir:documents/}")
    private String baseDir;

    @Override
    public void store(InputStream inputStream, String key, String contentType) throws IOException {
        Path filePath = resolvePath(key);
        ensureDirectory(filePath.getParent());
        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Stored local file: {}", filePath);
    }

    @Override
    public void store(byte[] data, String key, String contentType) throws IOException {
        Path filePath = resolvePath(key);
        ensureDirectory(filePath.getParent());
        Files.write(filePath, data);
        log.debug("Stored local file (bytes): {}", filePath);
    }

    @Override
    public void store(MultipartFile file, String key) throws IOException {
        Path filePath = resolvePath(key);
        ensureDirectory(filePath.getParent());
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        log.debug("Stored local multipart: {}", filePath);
    }

    @Override
    public Resource loadAsResource(String key) {
        Path filePath = resolvePath(key);
        Resource resource = new FileSystemResource(filePath);
        if (resource.exists()) {
            return resource;
        }
        return null;
    }

    @Override
    public void delete(String key) throws IOException {
        Path filePath = resolvePath(key);
        Files.deleteIfExists(filePath);
        log.debug("Deleted local file: {}", filePath);
    }

    @Override
    public boolean exists(String key) {
        Path filePath = resolvePath(key);
        return Files.exists(filePath);
    }

    @Override
    public long size(String key) {
        try {
            Path filePath = resolvePath(key);
            return Files.size(filePath);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    public long lastModified(String key) {
        try {
            Path filePath = resolvePath(key);
            return Files.getLastModifiedTime(filePath).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private Path resolvePath(String key) {
        return Paths.get(baseDir).resolve(key).normalize();
    }

    private void ensureDirectory(Path dir) throws IOException {
        if (dir != null && !Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }
}
