package com.adminpro.controller;

import com.adminpro.model.AttendanceSettings;
import com.adminpro.model.PayrollSettings;
import com.adminpro.repository.AttendanceSettingsRepository;
import com.adminpro.repository.PayrollSettingsRepository;
import com.adminpro.service.ActivityLogService;
import com.adminpro.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/configuracion")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class ConfiguracionController {

    private final AttendanceService attendanceService;
    private final AttendanceSettingsRepository attendanceSettingsRepository;
    private final PayrollSettingsRepository payrollSettingsRepository;
    private final ActivityLogService activityLog;

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
}
