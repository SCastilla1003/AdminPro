package com.adminpro.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class OnlyOfficeService {

    private static final Logger log = LoggerFactory.getLogger(OnlyOfficeService.class);

    @Value("${onlyoffice.api-key:}")
    private String apiKey;

    @Value("${onlyoffice.document-server-url:https://onlinedocs.onlyoffice.com/}")
    private String documentServerUrl;

    private final StorageService storageService;

    public OnlyOfficeService(StorageService storageService) {
        this.storageService = storageService;
    }

    public void createBlankDocument(String filePath, String type) throws IOException {
        String key = "documents/" + filePath;
        byte[] data;

        try {
            switch (type.toLowerCase()) {
                case "docx" -> {
                    try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        doc.createParagraph();
                        doc.write(baos);
                        data = baos.toByteArray();
                    }
                }
                case "xlsx" -> {
                    try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        wb.createSheet("Hoja1");
                        wb.write(baos);
                        data = baos.toByteArray();
                    }
                }
                case "pptx" -> {
                    try (XMLSlideShow ppt = new XMLSlideShow(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                        ppt.createSlide();
                        ppt.write(baos);
                        data = baos.toByteArray();
                    }
                }
                default -> throw new IllegalArgumentException("Tipo no soportado: " + type);
            }
        } catch (IOException e) {
            throw new IOException("Error al crear documento vacío: " + e.getMessage(), e);
        }

        String contentType = switch (type.toLowerCase()) {
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };

        storageService.store(data, key, contentType);
        log.debug("Created blank document: {}", key);
    }

    public String generateJwt(Map<String, Object> payload) {
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }

        SecretKey key = Keys.hmacShaKeyFor(apiKey.getBytes(StandardCharsets.UTF_8));

        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(86400); // 24 hours

        return Jwts.builder()
                .claims(payload)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .signWith(key)
                .compact();
    }

    public Claims validateJwt(String token) {
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }

        SecretKey key = Keys.hmacShaKeyFor(apiKey.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean downloadFile(String url, String key) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                String contentType = response.headers().firstValue("Content-Type")
                        .orElse("application/octet-stream");
                storageService.store(response.body(), key, contentType);
                return true;
            }
            return false;
        } catch (IOException | InterruptedException e) {
            log.error("Failed to download file from {}: {}", url, e.getMessage());
            return false;
        }
    }

    public String getDocumentServerUrl() {
        return documentServerUrl;
    }

    public boolean isApiKeyConfigured() {
        return apiKey != null && !apiKey.isEmpty();
    }
}
