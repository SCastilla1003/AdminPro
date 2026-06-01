package com.adminpro.controller;

import com.adminpro.service.StorageService;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private static final String AUDIO_KEY = "audio/whatsapp.mp3";

    private final StorageService storageService;

    public AudioController(StorageService storageService) {
        this.storageService = storageService;
    }

    @GetMapping("/whatsapp")
    public ResponseEntity<Resource> getWhatsAppAudio() {
        Resource resource = storageService.loadAsResource(AUDIO_KEY);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .body(resource);
    }

    @PostMapping("/upload")
    public String uploadAudio(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || file.getContentType() == null || !file.getContentType().startsWith("audio/")) {
            return "Archivo inválido. Sube un archivo de audio MP3.";
        }
        try {
            storageService.store(file, AUDIO_KEY);
            return "Audio subido correctamente.";
        } catch (IOException e) {
            return "Error al subir: " + e.getMessage();
        }
    }
}
