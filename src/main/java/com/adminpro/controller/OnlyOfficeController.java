package com.adminpro.controller;

import com.adminpro.model.Document;
import com.adminpro.repository.DocumentRepository;
import com.adminpro.repository.UserRepository;
import com.adminpro.service.OnlyOfficeService;
import com.adminpro.service.PreviewTokenService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/onlyoffice")
@RequiredArgsConstructor
public class OnlyOfficeController {

    private final DocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final OnlyOfficeService onlyOfficeService;
    private final PreviewTokenService tokenService;

    @Value("${upload.dir:uploads/documentos/}")
    private String uploadDir;

    @Value("${preview.base-url}")
    private String previewBaseUrl;

    @GetMapping("/config/{token}")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable String token) {
        Long docId = tokenService.validateToken(token);
        if (docId == null) {
            return ResponseEntity.status(403).build();
        }

        Document doc = documentRepository.findById(docId).orElse(null);
        if (doc == null) {
            return ResponseEntity.notFound().build();
        }

        String ext = doc.getFilePath().contains(".")
                ? doc.getFilePath().substring(doc.getFilePath().lastIndexOf(".") + 1).toLowerCase()
                : "";

        String documentType = switch (ext) {
            case "doc", "docx" -> "word";
            case "xls", "xlsx" -> "cell";
            case "ppt", "pptx" -> "slide";
            default -> "word";
        };

        Map<String, Object> document = new HashMap<>();
        document.put("fileType", ext);
        document.put("key", doc.getId() + "_" + doc.getFilePath());
        document.put("title", doc.getName());
        document.put("url", previewBaseUrl + "/api/onlyoffice/download/" + token);

        Map<String, Object> editorConfig = new HashMap<>();
        editorConfig.put("callbackUrl", previewBaseUrl + "/api/onlyoffice/callback?token=" + token);
        editorConfig.put("mode", "edit");
        editorConfig.put("lang", "es");

        Map<String, Object> user = new HashMap<>();
        user.put("id", "admin");
        user.put("name", "Admin User");
        editorConfig.put("user", user);

        Map<String, Object> config = new HashMap<>();
        config.put("document", document);
        config.put("editorConfig", editorConfig);
        config.put("documentType", documentType);
        config.put("type", "desktop");
        config.put("width", "100%");
        config.put("height", "100%");

        if (onlyOfficeService.isApiKeyConfigured()) {
            String jwt = onlyOfficeService.generateJwt(config);
            config.put("token", jwt);
        }

        return ResponseEntity.ok(config);
    }

    @GetMapping("/download/{token}")
    public ResponseEntity<Resource> download(@PathVariable String token) {
        Long docId = tokenService.validateToken(token);
        if (docId == null) {
            return ResponseEntity.status(403).build();
        }

        Document doc = documentRepository.findById(docId).orElse(null);
        if (doc == null || doc.getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        Path filePath = Paths.get(uploadDir).resolve(doc.getFilePath());
        Resource resource = new FileSystemResource(filePath);

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String ext = doc.getFilePath().contains(".")
                ? doc.getFilePath().substring(doc.getFilePath().lastIndexOf(".")).toLowerCase()
                : "";

        String contentType = switch (ext) {
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getName() + "\"")
                .body(resource);
    }

    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestBody Map<String, Object> body,
            @RequestParam String token) {

        Long docId = tokenService.validateToken(token);
        if (docId == null) {
            return ResponseEntity.status(403).build();
        }

        if (onlyOfficeService.isApiKeyConfigured()) {
            String jwtToken = (String) body.get("token");
            if (jwtToken != null) {
                try {
                    Claims claims = onlyOfficeService.validateJwt(jwtToken);
                    if (claims == null) {
                        return ResponseEntity.status(403).build();
                    }
                    Object payloadDocId = claims.get("documentId");
                    if (payloadDocId != null && !payloadDocId.equals(docId)) {
                        return ResponseEntity.status(403).build();
                    }
                } catch (Exception e) {
                    return ResponseEntity.status(403).build();
                }
            }
        }

        Integer status = (Integer) body.get("status");
        if (status == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", 0);
            return ResponseEntity.ok(response);
        }

        if (status == 2 || status == 6) {
            String downloadUrl = (String) body.get("url");
            if (downloadUrl != null) {
                Document doc = documentRepository.findById(docId).orElse(null);
                if (doc != null) {
                    Path filePath = Paths.get(uploadDir).resolve(doc.getFilePath());
                    boolean success = onlyOfficeService.downloadFile(downloadUrl, filePath);

                    if (success) {
                        try {
                            long sizeBytes = Files.size(filePath);
                            String sizeFormatted;
                            if (sizeBytes < 1024) {
                                sizeFormatted = sizeBytes + " B";
                            } else if (sizeBytes < 1024 * 1024) {
                                sizeFormatted = String.format("%.1f KB", sizeBytes / 1024.0);
                            } else {
                                sizeFormatted = String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
                            }
                            doc.setSize(sizeFormatted);
                            documentRepository.save(doc);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("error", 0);
        return ResponseEntity.ok(response);
    }
}
