package com.adminpro.controller;

import com.adminpro.model.Document;
import com.adminpro.repository.DocumentRepository;
import com.adminpro.service.PreviewTokenService;
import com.adminpro.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/preview")
@RequiredArgsConstructor
public class PublicPreviewController {

    private final PreviewTokenService tokenService;
    private final DocumentRepository documentRepository;
    private final StorageService storageService;

    private static final String DOCS_PREFIX = "documents/";

    @GetMapping
    public ResponseEntity<Resource> getPublicPreview(@RequestParam String token) {
        Long documentId = tokenService.validateToken(token);
        if (documentId == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Document doc = documentRepository.findById(documentId).orElse(null);
        if (doc == null || doc.getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }

        String key = DOCS_PREFIX + doc.getFilePath();
        Resource resource = storageService.loadAsResource(key);

        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String fileExt = doc.getFilePath().contains(".")
                ? doc.getFilePath().substring(doc.getFilePath().lastIndexOf(".")).toLowerCase()
                : "";

        String contentType = switch (fileExt) {
            case ".docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case ".xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case ".pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case ".doc"  -> "application/msword";
            case ".xls"  -> "application/vnd.ms-excel";
            case ".ppt"  -> "application/vnd.ms-powerpoint";
            default -> "application/octet-stream";
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getName() + "\"")
                .body(resource);
    }
}
