package com.adminpro.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Service
public class OnlyOfficeService {

    @Value("${onlyoffice.api-key:}")
    private String apiKey;

    @Value("${onlyoffice.document-server-url:https://onlinedocs.onlyoffice.com/}")
    private String documentServerUrl;

    @Value("${upload.dir:uploads/documentos/}")
    private String uploadDir;

    public void createBlankDocument(String filePath, String type) throws IOException {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        Path fullPath = uploadPath.resolve(filePath);

        try {
            switch (type.toLowerCase()) {
                case "docx" -> {
                    try (XWPFDocument doc = new XWPFDocument()) {
                        doc.createParagraph();
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(fullPath.toFile())) {
                            doc.write(fos);
                        }
                    }
                }
                case "xlsx" -> {
                    try (XSSFWorkbook wb = new XSSFWorkbook()) {
                        wb.createSheet("Hoja1");
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(fullPath.toFile())) {
                            wb.write(fos);
                        }
                    }
                }
                case "pptx" -> {
                    try (XMLSlideShow ppt = new XMLSlideShow()) {
                        ppt.createSlide();
                        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(fullPath.toFile())) {
                            ppt.write(fos);
                        }
                    }
                }
                default -> throw new IllegalArgumentException("Tipo no soportado: " + type);
            }
        } catch (IOException e) {
            throw new IOException("Error al crear documento vacío: " + e.getMessage(), e);
        }
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

    public boolean downloadFile(String url, Path destination) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                Files.copy(response.body(), destination, StandardCopyOption.REPLACE_EXISTING);
                return true;
            }
            return false;
        } catch (IOException | InterruptedException e) {
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
