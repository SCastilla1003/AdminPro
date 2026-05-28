package com.adminpro.controller;

import com.adminpro.dto.LiquidationItemDTO;
import com.adminpro.model.Employee;
import com.adminpro.model.PayrollRecord;
import com.adminpro.model.PayrollSettings;
import com.adminpro.service.PayrollService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/nomina")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;
    private final com.adminpro.service.ActivityLogService activityLog;
    private final com.adminpro.service.NotificationService notificationService;

    @GetMapping
    public String index(Model model, 
                        @RequestParam(required = false) Integer year,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false, defaultValue = "liquidate") String tab) {
        
        LocalDate now = LocalDate.now();
        if (year == null) year = now.getYear();
        if (month == null) month = now.getMonthValue();

        String[] months = {"", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
        String period = months[month] + " " + year;

        model.addAttribute("pageTitle", "Gestión de Nómina Colombia");
        model.addAttribute("pageSubtitle", "Cálculo legal de devengados y deducciones");
        model.addAttribute("activePage", "nomina");
        
        model.addAttribute("employees", payrollService.getActiveEmployees());
        model.addAttribute("history", payrollService.getHistory(year, month)); // Filter history by period too
        model.addAttribute("liquidationItems", payrollService.prepareLiquidation(year, month));
        model.addAttribute("settings", payrollService.getSettings());
        model.addAttribute("users", payrollService.getAllUsers());
        model.addAttribute("currentYear", year);
        model.addAttribute("currentMonth", month);
        model.addAttribute("currentPeriod", period);
        model.addAttribute("currentTab", tab);
        
        return "nomina/index";
    }

    @PostMapping("/settings")
    public String saveSettings(@ModelAttribute PayrollSettings settings, RedirectAttributes ra) {
        payrollService.updateSettings(settings);
        activityLog.log("PAYROLL", "UPDATE", "Configuración de nómina actualizada");
        ra.addFlashAttribute("successMsg", "Configuración actualizada correctamente.");
        return "redirect:/nomina";
    }

    @PostMapping("/empleado")
    public String saveEmployee(@ModelAttribute Employee employee, @RequestParam(required = false) Long userId, RedirectAttributes ra) {
        if (userId != null) {
            employee.setSystemUser(payrollService.getUserById(userId));
        }
        payrollService.saveEmployee(employee);
        activityLog.log("PAYROLL", employee.getId() == null ? "CREATE" : "UPDATE", "Empleado registrado/actualizado: " + employee.getFullName());
        ra.addFlashAttribute("successMsg", "Empleado guardado exitosamente.");
        return "redirect:/nomina";
    }

    @PostMapping("/empleado/eliminar/{id}")
    public String deleteEmployee(@PathVariable Long id, RedirectAttributes ra) {
        payrollService.deleteEmployee(id);
        activityLog.log("PAYROLL", "DELETE", "Empleado eliminado (ID: " + id + ")");
        ra.addFlashAttribute("successMsg", "Empleado eliminado correctamente.");
        return "redirect:/nomina";
    }

    @PostMapping("/liquidar")
    public String saveLiquidation(@ModelAttribute LiquidationWrapper wrapper, 
                                  @RequestParam String period,
                                  RedirectAttributes ra) {
        try {
            payrollService.saveLiquidation(wrapper.getItems(), period);
            activityLog.log("PAYROLL", "CREATE", "Liquidación de nómina masiva realizada para el periodo: " + period);
            notificationService.createNotificationForAll("Nómina liquidada para el periodo: " + period, "PAYROLL", "/nomina");
            ra.addFlashAttribute("successMsg", "Nómina liquidada exitosamente para todos los empleados.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Error al liquidar la nómina: " + e.getMessage());
        }
        return "redirect:/nomina";
    }

    @PostMapping("/liquidar-individual")
    public String saveIndividualLiquidation(@ModelAttribute LiquidationItemDTO item, 
                                            @RequestParam String period,
                                            RedirectAttributes ra) {
        try {
            payrollService.saveLiquidation(List.of(item), period);
            activityLog.log("PAYROLL", "CREATE", "Liquidación individual realizada para: " + item.getEmployeeName());
            ra.addFlashAttribute("successMsg", "Nómina liquidada para " + item.getEmployeeName());
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/nomina";
    }

    @PostMapping("/eliminar/{id}")
    public String deleteRecord(@PathVariable Long id, RedirectAttributes ra) {
        payrollService.deleteRecord(id);
        ra.addFlashAttribute("successMsg", "Registro eliminado del historial.");
        return "redirect:/nomina";
    }

    @GetMapping("/exportar/{id}")
    public void exportToPDF(@PathVariable Long id, HttpServletResponse response) throws Exception {
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=Volante_Nomina_" + id + ".pdf");
        payrollService.exportToPdf(id, response);
    }

    @Data
    public static class LiquidationWrapper {
        private List<LiquidationItemDTO> items;
    }
}
