package com.adminpro.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

@Service
@ConditionalOnProperty(name = "aws.s3.bucket")
public class S3StorageService implements StorageService {

    private static final Logger log = LoggerFactory.getLogger(S3StorageService.class);

    @Value("${aws.s3.bucket}")
    private String bucketName;

    @Value("${aws.s3.region:us-east-1}")
    private String region;

    @Value("${aws.s3.endpoint:}")
    private String endpoint;

    @Value("${aws.s3.access-key:}")
    private String accessKey;

    @Value("${aws.s3.secret-key:}")
    private String secretKey;

    private S3Client s3Client;

    @PostConstruct
    public void init() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
            builder.forcePathStyle(false);
        }

        this.s3Client = builder.build();
        log.info("S3StorageService initialized — bucket: {}, region: {}, endpoint: {}",
                bucketName, region, endpoint != null ? endpoint : "AWS default");
    }

    @Override
    public void store(InputStream inputStream, String key, String contentType) throws IOException {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(normalizeKey(key))
                    .contentType(contentType != null ? contentType : "application/octet-stream")
                    .build();

            byte[] bytes = inputStream.readAllBytes();
            s3Client.putObject(request, RequestBody.fromBytes(bytes));
            log.debug("Stored object: {}", key);
        } catch (S3Exception e) {
            throw new IOException("S3 store failed for " + key + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void store(byte[] data, String key, String contentType) throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data)) {
            store(bais, key, contentType);
        }
    }

    @Override
    public void store(MultipartFile file, String key) throws IOException {
        store(file.getInputStream(), key, file.getContentType());
    }

    @Override
    public Resource loadAsResource(String key) {
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(normalizeKey(key))
                    .build();

            var response = s3Client.getObject(request);
            return new InputStreamResource(response) {
                @Override
                public long contentLength() throws IOException {
                    return response.response().contentLength();
                }

                @Override
                public String getFilename() {
                    int idx = key.lastIndexOf('/');
                    return idx >= 0 ? key.substring(idx + 1) : key;
                }
            };
        } catch (NoSuchKeyException e) {
            log.warn("S3 object not found: {}", key);
            return null;
        } catch (S3Exception e) {
            log.error("S3 load failed for {}: {}", key, e.getMessage());
            return null;
        }
    }

    @Override
    public void delete(String key) throws IOException {
        try {
            DeleteObjectRequest request = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(normalizeKey(key))
                    .build();
            s3Client.deleteObject(request);
            log.debug("Deleted object: {}", key);
        } catch (S3Exception e) {
            throw new IOException("S3 delete failed for " + key + ": " + e.getMessage(), e);
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(normalizeKey(key))
                    .build();
            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            log.warn("S3 head failed for {}: {}", key, e.getMessage());
            return false;
        }
    }

    @Override
    public long size(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(normalizeKey(key))
                    .build();
            HeadObjectResponse response = s3Client.headObject(request);
            return response.contentLength();
        } catch (S3Exception e) {
            log.warn("S3 size failed for {}: {}", key, e.getMessage());
            return 0;
        }
    }

    @Override
    public long lastModified(String key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(normalizeKey(key))
                    .build();
            HeadObjectResponse response = s3Client.headObject(request);
            return response.lastModified().toEpochMilli();
        } catch (S3Exception e) {
            log.warn("S3 lastModified failed for {}: {}", key, e.getMessage());
            return 0;
        }
    }

    private String normalizeKey(String key) {
        if (key == null) return "";
        return key.startsWith("/") ? key.substring(1) : key;
    }
}
