package com.adminpro.controller;

import com.adminpro.model.User;
import com.adminpro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Controller
@RequestMapping("/perfil")
@RequiredArgsConstructor
public class ProfileController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String UPLOAD_DIR = "uploads/perfiles/";

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

        // Create upload directory if needed
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename
        String originalName = photo.getOriginalFilename();
        String ext = (originalName != null && originalName.contains("."))
            ? originalName.substring(originalName.lastIndexOf("."))
            : ".jpg";
        String filename = "perfil_" + UUID.randomUUID() + ext;

        Path filePath = uploadPath.resolve(filename);
        Files.copy(photo.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Update user
        User user = userRepository.findByUsername(userDetails.getUsername())
            .orElseThrow(() -> new IllegalStateException("Usuario no encontrado"));

        // Delete old photo if exists
        if (user.getProfilePhoto() != null) {
            Path oldFile = Paths.get("uploads/" + user.getProfilePhoto());
            try { Files.deleteIfExists(oldFile); } catch (IOException ignored) {}
        }

        user.setProfilePhoto("perfiles/" + filename);
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
            Path oldFile = Paths.get("uploads/" + user.getProfilePhoto());
            try { Files.deleteIfExists(oldFile); } catch (IOException ignored) {}
            user.setProfilePhoto(null);
            userRepository.save(user);
        }

        redirectAttributes.addFlashAttribute("successMsg", "Foto eliminada.");
        return "redirect:/perfil";
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
