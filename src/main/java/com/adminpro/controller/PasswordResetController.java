package com.adminpro.controller;

import com.adminpro.model.PasswordResetToken;
import com.adminpro.model.User;
import com.adminpro.repository.PasswordResetTokenRepository;
import com.adminpro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class PasswordResetController {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/recuperar-password")
    public String showRequestForm() {
        return "auth/recuperar-password";
    }

    @PostMapping("/recuperar-password")
    public String requestReset(@RequestParam String email, Model model, RedirectAttributes ra) {
        userRepository.findByEmail(email).ifPresent(user -> {
            PasswordResetToken token = new PasswordResetToken();
            token.setToken(UUID.randomUUID().toString());
            token.setUser(user);
            token.setExpiryDate(LocalDateTime.now().plusHours(2));
            tokenRepository.save(token);
        });

        model.addAttribute("pageTitle", "Recuperar Contraseña");
        model.addAttribute("emailSent", true);
        return "auth/recuperar-password";
    }

    @GetMapping("/restablecer-password")
    public String showResetForm(@RequestParam String token, Model model, RedirectAttributes ra) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token).orElse(null);

        if (resetToken == null || resetToken.isExpired() || resetToken.isUsed()) {
            ra.addFlashAttribute("errorMsg", "El enlace de recuperación no es válido o ha expirado.");
            return "redirect:/login";
        }

        model.addAttribute("pageTitle", "Restablecer Contraseña");
        model.addAttribute("token", token);
        return "auth/restablecer-password";
    }

    @PostMapping("/restablecer-password")
    public String resetPassword(@RequestParam String token,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                RedirectAttributes ra) {
        PasswordResetToken resetToken = tokenRepository.findByToken(token).orElse(null);

        if (resetToken == null || resetToken.isExpired() || resetToken.isUsed()) {
            ra.addFlashAttribute("errorMsg", "El enlace de recuperación no es válido o ha expirado.");
            return "redirect:/login";
        }

        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMsg", "Las contraseñas no coinciden.");
            return "redirect:/restablecer-password?token=" + token;
        }

        if (newPassword.length() < 6) {
            ra.addFlashAttribute("errorMsg", "La contraseña debe tener al menos 6 caracteres.");
            return "redirect:/restablecer-password?token=" + token;
        }

        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetToken.setUsed(true);
        tokenRepository.save(resetToken);

        ra.addFlashAttribute("successMsg", "Contraseña actualizada correctamente. Ya puedes iniciar sesión.");
        return "redirect:/login";
    }
}
