package com.adminpro.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@lombok.RequiredArgsConstructor
public class DashboardController {

    private final com.adminpro.repository.EmployeeRepository employeeRepository;
    private final com.adminpro.repository.PayrollRecordRepository payrollRecordRepository;
    private final com.adminpro.repository.ProductRepository productRepository;
    private final com.adminpro.repository.DocumentRepository documentRepository;
    private final com.adminpro.service.ActivityLogService activityLogService;
    private final com.adminpro.service.AttendanceService attendanceService;

    @GetMapping("/dashboard")
    public String dashboard(@AuthenticationPrincipal UserDetails userDetails, Model model) {
        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("pageSubtitle", "Panel de control general");
        model.addAttribute("activePage", "dashboard");

        // Estadísticas reales
        model.addAttribute("totalEmployees", employeeRepository.count());
        
        Double totalPayroll = payrollRecordRepository.sumNetSalary();
        model.addAttribute("totalPayroll", totalPayroll != null ? totalPayroll : 0.0);
        
        model.addAttribute("totalProducts", productRepository.count());
        model.addAttribute("lowStockProducts", productRepository.countLowStock());
        
        model.addAttribute("totalDocuments", documentRepository.count());

        // Actividad reciente filtrada
        model.addAttribute("recentActivity", activityLogService.getRecentForUser(8));

        // Asistencia del día
        model.addAttribute("todayAttendance", attendanceService.getTodayRecordForCurrentUser());
        model.addAttribute("attendanceSettings", attendanceService.getSettings());

        return "dashboard/index";
    }

    @GetMapping("/historial")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public String fullHistory(Model model) {
        model.addAttribute("pageTitle", "Historial Completo");
        model.addAttribute("pageSubtitle", "Registro de todas las acciones del sistema");
        model.addAttribute("activePage", "dashboard");
        model.addAttribute("allLogs", activityLogService.getAll());
        return "dashboard/history";
    }

    @GetMapping("/acceso-denegado")
    public String accessDenied(Model model) {
        model.addAttribute("pageTitle", "Acceso Denegado");
        model.addAttribute("pageSubtitle", "No tienes permisos para ver esta sección");
        model.addAttribute("activePage", "");
        return "acceso-denegado";
    }
}
