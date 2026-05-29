package com.adminpro.controller;

import com.adminpro.model.Document;
import com.adminpro.model.Folder;
import com.adminpro.repository.DocumentRepository;
import com.adminpro.repository.FolderRepository;
import com.adminpro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/documentos")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final com.adminpro.service.ActivityLogService activityLog;
    private final com.adminpro.service.NotificationService notificationService;
    private final com.adminpro.service.PreviewTokenService tokenService;
    private final com.adminpro.service.OnlyOfficeService onlyOfficeService;

    @Value("${preview.base-url:http://localhost:25565}")
    private String previewBaseUrl;

    @Value("${upload.dir:uploads/documentos/}")
    private String uploadDir;

    @Value("${onlyoffice.document-server-url:https://onlinedocs.onlyoffice.com/}")
    private String onlyOfficeUrl;

    @GetMapping
    public String index(@RequestParam(required = false) Long folderId, Model model) {
        Folder currentFolder = null;
        List<Folder> breadcrumbs = new ArrayList<>();

        if (folderId != null) {
            currentFolder = folderRepository.findById(folderId).orElse(null);
            if (currentFolder != null) {
                Folder cursor = currentFolder;
                while (cursor != null) {
                    breadcrumbs.add(0, cursor);
                    cursor = cursor.getParent();
                }
            }
        }

        List<Folder> subfolders = folderRepository.findByParentOrderByNameAsc(currentFolder);
        List<Document> documents = (currentFolder == null)
            ? documentRepository.findByFolderIsNullOrderByNameAsc()
            : documentRepository.findByFolderOrderByNameAsc(currentFolder);

        model.addAttribute("pageTitle", "Gestor Documental");
        model.addAttribute("pageSubtitle", currentFolder != null ? currentFolder.getName() : "Archivos y carpetas");
        model.addAttribute("activePage", "documentos");
        model.addAttribute("currentFolder", currentFolder);
        model.addAttribute("breadcrumbs", breadcrumbs);
        model.addAttribute("subfolders", subfolders);
        model.addAttribute("documents", documents);
        model.addAttribute("onlyOfficeUrl", onlyOfficeUrl.endsWith("/") ? onlyOfficeUrl.substring(0, onlyOfficeUrl.length() - 1) : onlyOfficeUrl);
        return "documentos/index";
    }

    @PostMapping("/carpeta/crear")
    public String createFolder(@RequestParam String name,
                               @RequestParam(required = false) Long parentFolderId,
                               Authentication authentication,
                               RedirectAttributes ra) {
        Folder folder = new Folder();
        folder.setName(name);
        if (parentFolderId != null) {
            folderRepository.findById(parentFolderId).ifPresent(folder::setParent);
        }
        userRepository.findByUsername(authentication.getName()).ifPresent(folder::setCreatedBy);
        folderRepository.save(folder);
        activityLog.log("DOCUMENTS", "CREATE", "Carpeta '" + name + "' creada");
        ra.addFlashAttribute("successMsg", "Carpeta creada correctamente.");
        if (parentFolderId != null) {
            return "redirect:/documentos?folderId=" + parentFolderId;
        }
        return "redirect:/documentos";
    }

    @PostMapping("/carpeta/eliminar/{id}")
    public String deleteFolder(@PathVariable Long id, RedirectAttributes ra) {
        folderRepository.findById(id).ifPresent(f -> {
            activityLog.log("DOCUMENTS", "DELETE", "Carpeta '" + f.getName() + "' eliminada");
        });
        folderRepository.deleteById(id);
        ra.addFlashAttribute("successMsg", "Carpeta eliminada.");
        return "redirect:/documentos";
    }

    @PostMapping("/subir")
    public String uploadDocument(@RequestParam("file") MultipartFile file,
                                 @RequestParam String name,
                                 @RequestParam String type,
                                 @RequestParam(required = false) Long folderId,
                                 Authentication authentication,
                                 RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("errorMsg", "Selecciona un archivo para subir.");
            return "redirect:/documentos" + (folderId != null ? "?folderId=" + folderId : "");
        }

        try {
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
            String filename = UUID.randomUUID() + ext;

            Path filePath = uploadPath.resolve(filename);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            long sizeBytes = file.getSize();
            String sizeFormatted;
            if (sizeBytes < 1024) {
                sizeFormatted = sizeBytes + " B";
            } else if (sizeBytes < 1024 * 1024) {
                sizeFormatted = String.format("%.1f KB", sizeBytes / 1024.0);
            } else {
                sizeFormatted = String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0));
            }

            Document doc = new Document();
            doc.setName(name + ext);
            doc.setType(detectFileType(ext, type));
            doc.setSize(sizeFormatted);
            doc.setFilePath(filename);

            if (folderId != null) {
                folderRepository.findById(folderId).ifPresent(doc::setFolder);
            }

            userRepository.findByUsername(authentication.getName()).ifPresent(doc::setUploadedBy);

            documentRepository.save(doc);
            activityLog.log("DOCUMENTS", "CREATE", "Documento '" + name + "' subido al sistema");
            notificationService.createNotification(authentication.getName(), "Documento '" + name + "' subido correctamente.", "SYSTEM", "/documentos");

            ra.addFlashAttribute("successMsg", "Documento subido correctamente.");
        } catch (IOException e) {
            ra.addFlashAttribute("errorMsg", "Error al subir el archivo: " + e.getMessage());
        }

        return "redirect:/documentos" + (folderId != null ? "?folderId=" + folderId : "");
    }

    @PostMapping("/crear-oficina")
    @ResponseBody
    public java.util.Map<String, Object> createOfficeDocument(
            @RequestParam String name,
            @RequestParam String type,
            @RequestParam(required = false) Long folderId,
            Authentication authentication) {

        java.util.Map<String, Object> response = new java.util.HashMap<>();

        try {
            String ext = switch (type.toLowerCase()) {
                case "word" -> ".docx";
                case "excel" -> ".xlsx";
                case "powerpoint" -> ".pptx";
                default -> ".docx";
            };

            String filename = UUID.randomUUID() + ext;
            onlyOfficeService.createBlankDocument(filename, ext.substring(1));

            Document doc = new Document();
            doc.setName(name + ext);
            doc.setType(detectFileType(ext, type));
            doc.setSize("0 B");
            doc.setFilePath(filename);

            if (folderId != null) {
                folderRepository.findById(folderId).ifPresent(doc::setFolder);
            }

            userRepository.findByUsername(authentication.getName()).ifPresent(doc::setUploadedBy);

            documentRepository.save(doc);
            activityLog.log("DOCUMENTS", "CREATE", "Documento Office '" + name + "' creado");
            notificationService.createNotification(authentication.getName(), "Documento '" + name + "' creado correctamente.", "SYSTEM", "/documentos");

            String token = tokenService.generateToken(doc.getId());

            response.put("success", true);
            response.put("documentId", doc.getId());
            response.put("token", token);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }

        return response;
    }

    @GetMapping("/ver/{id}")
    public ResponseEntity<Resource> viewDocument(@PathVariable Long id) {
        Document doc = documentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        Path filePath = Paths.get(uploadDir).resolve(doc.getFilePath());
        Resource resource = new FileSystemResource(filePath);

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String ext = doc.getFilePath() != null && doc.getFilePath().contains(".")
            ? doc.getFilePath().substring(doc.getFilePath().lastIndexOf(".") + 1).toLowerCase()
            : "";

        String contentType = switch (ext) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
            .body(resource);
    }

    @GetMapping("/descargar/{id}")
    public ResponseEntity<Resource> downloadDocument(@PathVariable Long id) {
        Document doc = documentRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Documento no encontrado"));

        Path filePath = Paths.get(uploadDir).resolve(doc.getFilePath());
        Resource resource = new FileSystemResource(filePath);

        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        String contentType = switch (doc.getType() != null ? doc.getType().toLowerCase() : "") {
            case "pdf" -> "application/pdf";
            case "excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "word" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "imagen" -> "image/png";
            default -> "application/octet-stream";
        };

        String downloadName = doc.getName();
        if (doc.getFilePath() != null && doc.getFilePath().contains(".")) {
            String fileExt = doc.getFilePath().substring(doc.getFilePath().lastIndexOf("."));
            if (!downloadName.toLowerCase().endsWith(fileExt.toLowerCase())) {
                downloadName = downloadName + fileExt;
            }
        }

        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(contentType))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + downloadName + "\"")
            .body(resource);
    }

    @PostMapping("/renombrar/{id}")
    public String renameDocument(@PathVariable Long id, @RequestParam String newName, RedirectAttributes ra) {
        documentRepository.findById(id).ifPresent(doc -> {
            String oldName = doc.getName();
            String ext = oldName.contains(".") ? oldName.substring(oldName.lastIndexOf(".")) : "";
            doc.setName(newName + ext);
            documentRepository.save(doc);
            activityLog.log("DOCUMENTS", "RENAME", "Documento '" + oldName + "' renombrado a '" + doc.getName() + "'");
        });
        ra.addFlashAttribute("successMsg", "Documento renombrado.");
        return "redirect:/documentos";
    }

    @PostMapping("/eliminar/{id}")
    public String deleteDocument(@PathVariable Long id, RedirectAttributes ra) {
        Document doc = documentRepository.findById(id).orElse(null);
        Long folderId = null;
        if (doc != null) {
            folderId = doc.getFolder() != null ? doc.getFolder().getId() : null;
            if (doc.getFilePath() != null) {
                try {
                    Files.deleteIfExists(Paths.get(uploadDir).resolve(doc.getFilePath()));
                } catch (IOException ignored) {}
            }
            activityLog.log("DOCUMENTS", "DELETE", "Documento '" + doc.getName() + "' eliminado");
        }
        documentRepository.deleteById(id);
        ra.addFlashAttribute("successMsg", "Documento eliminado.");
        return "redirect:/documentos" + (folderId != null ? "?folderId=" + folderId : "");
    }


    @GetMapping("/generar-token/{id}")
    @ResponseBody
    public java.util.Map<String, String> generatePreviewToken(@PathVariable Long id) {
        String token = tokenService.generateToken(id);
        String publicUrl = previewBaseUrl + "/api/public/preview?token=" + token;
        String encodedUrl = java.net.URLEncoder.encode(publicUrl, java.nio.charset.StandardCharsets.UTF_8);
        String microsoftUrl = "https://view.officeapps.live.com/op/embed.aspx?src=" + encodedUrl;

        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("url", microsoftUrl);
        return response;
    }

    @GetMapping("/generar-token-onlyoffice/{id}")
    @ResponseBody
    public java.util.Map<String, String> generateOnlyOfficeToken(@PathVariable Long id) {
        String token = tokenService.generateLongLivedToken(id);
        java.util.Map<String, String> response = new java.util.HashMap<>();
        response.put("token", token);
        return response;
    }

    private String detectFileType(String ext, String fallback) {
        if (ext == null) return fallback;
        return switch (ext.toLowerCase()) {
            case ".pdf" -> "PDF";
            case ".doc", ".docx" -> "Word";
            case ".xls", ".xlsx" -> "Excel";
            case ".ppt", ".pptx" -> "PowerPoint";
            case ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".svg" -> "Imagen";
            case ".txt", ".csv", ".json", ".xml" -> "Texto";
            default -> "Otro";
        };
    }
}
