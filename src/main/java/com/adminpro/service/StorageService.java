package com.adminpro.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

public interface StorageService {

    void store(InputStream inputStream, String key, String contentType) throws IOException;

    void store(byte[] data, String key, String contentType) throws IOException;

    void store(MultipartFile file, String key) throws IOException;

    Resource loadAsResource(String key);

    void delete(String key) throws IOException;

    boolean exists(String key);

    long size(String key);

    long lastModified(String key);
}
