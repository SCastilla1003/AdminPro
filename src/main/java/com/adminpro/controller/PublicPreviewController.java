package com.adminpro.controller;

import com.adminpro.model.Document;
import com.adminpro.repository.DocumentRepository;
import com.adminpro.service.PreviewTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/public/preview")
@RequiredArgsConstructor
public class PublicPreviewController {

    private final PreviewTokenService tokenService;
    private final DocumentRepository documentRepository;

    private static final String UPLOAD_DIR = "uploads/documentos/";

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

        Path filePath = Paths.get(UPLOAD_DIR).resolve(doc.getFilePath());
        Resource resource = new FileSystemResource(filePath);

        if (!resource.exists()) {
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
