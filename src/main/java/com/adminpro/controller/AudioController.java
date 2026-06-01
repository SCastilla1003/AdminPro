package com.adminpro.controller;

import com.adminpro.service.StorageService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private static final Logger log = LoggerFactory.getLogger(AudioController.class);
    private static final String AUDIO_KEY = "audio/whatsapp.mp3";
    private static final String CDN_URL = "https://www.soundjay.com/communication/sounds/whatsapp-incoming-1.mp3";

    private final StorageService storageService;

    public AudioController(StorageService storageService) {
        this.storageService = storageService;
    }

    @PostConstruct
    public void initAudio() {
        if (!storageService.exists(AUDIO_KEY)) {
            try {
                log.info("Downloading WhatsApp audio from CDN...");
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(CDN_URL))
                        .build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() == 200) {
                    storageService.store(response.body(), AUDIO_KEY, "audio/mpeg");
                    log.info("WhatsApp audio stored in bucket: {}", AUDIO_KEY);
                }
            } catch (Exception e) {
                log.warn("Could not download WhatsApp audio: {}", e.getMessage());
            }
        }
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
}
