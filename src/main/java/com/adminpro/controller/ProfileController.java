package com.adminpro.controller;

import com.adminpro.model.User;
import com.adminpro.repository.UserRepository;
import com.adminpro.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.UUID;

@Controller
@RequestMapping("/perfil")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StorageService storageService;

    private static final String PROFILES_PREFIX = "profiles/";

    @GetMapping
    public String showProfile(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("Usuario no encontrado"));
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", "Mi Perfil");
        model.addAttribute("pageSubtitle", "Configura tu información personal");
        model.addAttribute("activePage", "perfil");
        return "perfil/index";
    }

    @PostMapping("/guardar")
    public String saveProfile(@AuthenticationPrincipal UserDetails userDetails,
                              @RequestParam("fullName") String fullName,
                              @RequestParam("email") String email,
                              @RequestParam("jobTitle") String jobTitle,
                              @RequestParam("birthDate") String birthDate,
                              RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("Usuario no encontrado"));

        user.setFullName(fullName);
        user.setEmail(email);
        user.setJobTitle(jobTitle);
        if (birthDate != null && !birthDate.isBlank()) {
            user.setBirthDate(java.time.LocalDate.parse(birthDate));
        }
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("successMsg", "Perfil actualizado correctamente.");
        return "redirect:/perfil";
    }

    @PostMapping("/foto")
    public String uploadPhoto(@AuthenticationPrincipal UserDetails userDetails,
                              @RequestParam("photo") MultipartFile photo,
                              RedirectAttributes redirectAttributes) throws IOException {
        if (photo.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMsg", "Selecciona una imagen.");
            return "redirect:/perfil";
        }

        String contentType = photo.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            redirectAttributes.addFlashAttribute("errorMsg", "Solo se permiten imágenes.");
            return "redirect:/perfil";
        }

        String originalName = photo.getOriginalFilename();
        String ext = (originalName != null && originalName.contains("."))
            ? originalName.substring(originalName.lastIndexOf("."))
            : ".jpg";
        String filename = "perfil_" + UUID.randomUUID() + ext;
        String key = PROFILES_PREFIX + filename;

        storageService.store(photo, key);

        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("Usuario no encontrado"));

        if (user.getProfilePhoto() != null) {
            try { storageService.delete(PROFILES_PREFIX + user.getProfilePhoto()); } catch (IOException ignored) {}
        }

        user.setProfilePhoto(filename);
        userRepository.save(user);

        redirectAttributes.addFlashAttribute("successMsg", "Foto de perfil actualizada.");
        return "redirect:/perfil";
    }

    @PostMapping("/foto/eliminar")
    public String deletePhoto(@AuthenticationPrincipal UserDetails userDetails,
                              RedirectAttributes redirectAttributes) throws IOException {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("Usuario no encontrado"));

        if (user.getProfilePhoto() != null) {
            try { storageService.delete(PROFILES_PREFIX + user.getProfilePhoto()); } catch (IOException ignored) {}
            user.setProfilePhoto(null);
            userRepository.save(user);
        }

        redirectAttributes.addFlashAttribute("successMsg", "Foto eliminada.");
        return "redirect:/perfil";
    }

    @GetMapping("/foto/{filename}")
    @ResponseBody
    public ResponseEntity<Resource> servePhoto(@PathVariable String filename) {
        if (filename.contains("/")) {
            filename = filename.substring(filename.lastIndexOf('/') + 1);
        }
        String key = PROFILES_PREFIX + filename;
        Resource resource = storageService.loadAsResource(key);
        if (resource == null || !resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(resource);
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal UserDetails userDetails,
                                 @RequestParam("currentPassword") String currentPassword,
                                 @RequestParam("newPassword") String newPassword,
                                 @RequestParam("confirmPassword") String confirmPassword,
                                 RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("Usuario no encontrado"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            redirectAttributes.addFlashAttribute("passwordError", "La contraseña actual es incorrecta.");
            return "redirect:/perfil";
        }
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("passwordError", "Las contraseñas nuevas no coinciden.");
            return "redirect:/perfil";
        }
        if (newPassword.length() < 6) {
            redirectAttributes.addFlashAttribute("passwordError", "La contraseña debe tener al menos 6 caracteres.");
            return "redirect:/perfil";
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        redirectAttributes.addFlashAttribute("passwordSuccess", "Contraseña actualizada correctamente.");
        return "redirect:/perfil";
    }
}
