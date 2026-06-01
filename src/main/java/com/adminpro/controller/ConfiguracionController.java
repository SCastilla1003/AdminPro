package com.adminpro.controller;

import com.adminpro.model.AttendanceSettings;
import com.adminpro.model.PayrollSettings;
import com.adminpro.repository.AttendanceSettingsRepository;
import com.adminpro.repository.PayrollSettingsRepository;
import com.adminpro.service.ActivityLogService;
import com.adminpro.service.AttendanceService;
import com.adminpro.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;

@Controller
@RequestMapping("/configuracion")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ConfiguracionController {

    private final AttendanceService attendanceService;
    private final AttendanceSettingsRepository attendanceSettingsRepository;
    private final PayrollSettingsRepository payrollSettingsRepository;
    private final ActivityLogService activityLog;
    private final StorageService storageService;

    private static final String AUDIO_KEY = "audio/whatsapp.mp3";

    @GetMapping
    public String index(Model model) {
        AttendanceSettings attendanceSettings = attendanceService.getSettings();
        PayrollSettings payrollSettings = payrollSettingsRepository.findById(1L)
            .orElseGet(PayrollSettings::defaultSettings);

        model.addAttribute("pageTitle", "Configuración del Sistema");
        model.addAttribute("pageSubtitle", "Ajustes generales de la plataforma");
        model.addAttribute("activePage", "configuracion");
        model.addAttribute("attendanceSettings", attendanceSettings);
        model.addAttribute("payrollSettings", payrollSettings);
        model.addAttribute("audioUploaded", storageService.exists(AUDIO_KEY));
        return "configuracion/index";
    }

    @PostMapping("/asistencia")
    public String saveAttendanceSettings(
            @RequestParam String openTime,
            @RequestParam String closeTime,
            @RequestParam int gracePeriodMinutes,
            @RequestParam String timezone,
            @RequestParam(required = false) String restrictWeekends,
            RedirectAttributes ra) {
        
        AttendanceSettings settings = attendanceService.getSettings();
        settings.setOpenTime(java.time.LocalTime.parse(openTime));
        settings.setCloseTime(java.time.LocalTime.parse(closeTime));
        settings.setGracePeriodMinutes(gracePeriodMinutes);
        settings.setTimezone(timezone);
        settings.setRestrictWeekends(restrictWeekends != null);
        
        attendanceSettingsRepository.save(settings);
        activityLog.log("CONFIG", "UPDATE", "Configuración de asistencia actualizada");
        
        ra.addFlashAttribute("successMsg", "Configuración de asistencia guardada.");
        return "redirect:/configuracion";
    }

    @PostMapping("/nomina")
    public String savePayrollSettings(
            @RequestParam String smmlv,
            @RequestParam String transportAllowance,
            @RequestParam Double healthPercentage,
            @RequestParam Double pensionPercentage,
            RedirectAttributes ra) {
        
        PayrollSettings settings = payrollSettingsRepository.findById(1L)
            .orElseGet(PayrollSettings::defaultSettings);
        settings.setSmmlv(new java.math.BigDecimal(smmlv));
        settings.setTransportAllowance(new java.math.BigDecimal(transportAllowance));
        settings.setHealthPercentage(healthPercentage);
        settings.setPensionPercentage(pensionPercentage);
        
        payrollSettingsRepository.save(settings);
        activityLog.log("CONFIG", "UPDATE", "Configuración de nómina actualizada");
        
        ra.addFlashAttribute("successMsg", "Configuración de nómina guardada.");
        return "redirect:/configuracion";
    }

    @PostMapping("/audio")
    public String uploadAudio(@RequestParam("file") MultipartFile file, RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("errorMsg", "Selecciona un archivo de audio.");
            return "redirect:/configuracion";
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("audio/")) {
            ra.addFlashAttribute("errorMsg", "Solo se permiten archivos de audio (MP3).");
            return "redirect:/configuracion";
        }
        try {
            storageService.store(file, AUDIO_KEY);
            activityLog.log("CONFIG", "UPDATE", "Audio de notificación actualizado");
            ra.addFlashAttribute("successMsg", "Audio de notificación subido correctamente.");
        } catch (IOException e) {
            ra.addFlashAttribute("errorMsg", "Error al subir: " + e.getMessage());
        }
        return "redirect:/configuracion";
    }
}
