package com.adminpro.controller;

import com.adminpro.model.AttendanceSettings;
import com.adminpro.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalTime;

@Controller
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final com.adminpro.service.NotificationService notificationService;

    // ===== PÁGINA FUERA DE HORARIO (pública) =====
    @GetMapping("/fuera-de-horario")
    public String outOfHours(Model model) {
        AttendanceSettings s = attendanceService.getSettings();
        model.addAttribute("settings", s);
        return "fuera-de-horario";
    }

    // ===== REGISTRO DE ENTRADA =====
    @PostMapping("/asistencia/entrada")
    public String clockIn(RedirectAttributes ra, org.springframework.security.core.Authentication auth) {
        String msg = attendanceService.registerClockIn();
        if (msg.contains("exitosamente")) {
            notificationService.createNotification(auth.getName(), "Tu entrada ha sido registrada correctamente.", "ATTENDANCE", "/dashboard");
        }
        ra.addFlashAttribute("attendanceMsg", msg);
        return "redirect:/dashboard";
    }

    @PostMapping("/asistencia/salida")
    public String clockOut(RedirectAttributes ra, org.springframework.security.core.Authentication auth) {
        try {
            String msg = attendanceService.registerClockOut();
            if (msg.contains("exitosamente")) {
                notificationService.createNotification(auth.getName(), "Tu salida ha sido registrada correctamente.", "ATTENDANCE", "/dashboard");
            }
            ra.addFlashAttribute("attendanceMsg", msg);
        } catch (RuntimeException e) {
            ra.addFlashAttribute("attendanceError", e.getMessage());
        }
        return "redirect:/dashboard";
    }

    // ===== PANEL DE ADMINISTRACIÓN RRHH =====
    @GetMapping("/asistencia")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'PERM_ATTENDANCE')")
    public String adminPanel(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            Model model) {
        
        java.time.LocalDate today = java.time.LocalDate.now(attendanceService.getConfiguredZoneId());
        if (year == null) year = today.getYear();
        if (month == null) month = today.getMonthValue();

        model.addAttribute("pageTitle", "Control de Asistencia");
        model.addAttribute("pageSubtitle", "Registro y resumen de asistencia del personal");
        model.addAttribute("activePage", "asistencia");
        model.addAttribute("settings", attendanceService.getSettings());

        var records = attendanceService.getTodayRecords();
        model.addAttribute("todayRecords", records);

        model.addAttribute("countOnTime",  records.stream().filter(r -> "A_TIEMPO".equals(r.getStatus())).count());
        model.addAttribute("countLate",    records.stream().filter(r -> "TARDE".equals(r.getStatus())).count());
        model.addAttribute("countAbsent",  records.stream().filter(r -> "AUSENTE".equals(r.getStatus())).count());

        model.addAttribute("currentYear", year);
        model.addAttribute("currentMonth", month);
        model.addAttribute("monthlySummaries", attendanceService.getMonthlySummaries(year, month));
        model.addAttribute("annualSummaries", attendanceService.getAnnualSummaries(year));

        return "asistencia/index";
    }

    // ===== GUARDAR CONFIGURACIÓN DE HORARIO =====
    @PostMapping("/asistencia/config")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String saveConfig(
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime openTime,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime closeTime,
            @RequestParam int gracePeriodMinutes,
            @RequestParam String timezone,
            @RequestParam(value = "restrictWeekends", defaultValue = "false") boolean restrictWeekends,
            RedirectAttributes ra) {

        AttendanceSettings s = attendanceService.getSettings();
        s.setOpenTime(openTime);
        s.setCloseTime(closeTime);
        s.setGracePeriodMinutes(gracePeriodMinutes);
        s.setTimezone(timezone);
        s.setRestrictWeekends(restrictWeekends);
        attendanceService.saveSettings(s);

        ra.addFlashAttribute("successMsg", "Configuración de horario guardada correctamente.");
        return "redirect:/asistencia";
    }
}
