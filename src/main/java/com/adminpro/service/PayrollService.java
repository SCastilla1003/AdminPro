package com.adminpro.service;

import com.adminpro.model.Employee;
import com.adminpro.model.PayrollRecord;
import com.adminpro.model.PayrollSettings;
import com.adminpro.repository.EmployeeRepository;
import com.adminpro.repository.PayrollRecordRepository;
import com.adminpro.repository.PayrollSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

import com.adminpro.dto.AttendanceSummaryDTO;
import com.adminpro.dto.LiquidationItemDTO;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;

import java.awt.Color;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private final EmployeeRepository employeeRepository;
    private final PayrollRecordRepository payrollRepository;
    private final AttendanceService attendanceService;
    private final PayrollSettingsRepository settingsRepository;
    private final com.adminpro.repository.UserRepository userRepository;

    private String formatMoney(BigDecimal value) {
        if (value == null) return "$0";
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("es", "CO"));
        symbols.setGroupingSeparator('.');
        DecimalFormat df = new DecimalFormat("$#,###", symbols);
        return df.format(value.setScale(0, RoundingMode.HALF_UP));
    }

    public com.adminpro.model.User getUserById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    public List<com.adminpro.model.User> getAllUsers() {
        return userRepository.findAll();
    }

    public void saveEmployee(Employee employee) {
        employeeRepository.save(employee);
    }

    public void deleteEmployee(Long id) {
        Employee emp = employeeRepository.findById(id).orElse(null);
        if (emp != null) {
            emp.setActive(false);
            employeeRepository.save(emp);
        }
    }

    public Employee getEmployeeById(Long id) {
        return employeeRepository.findById(id).orElse(null);
    }

    public PayrollSettings getSettings() {
        return settingsRepository.findAll().stream().findFirst()
                .orElseGet(() -> settingsRepository.save(PayrollSettings.defaultSettings()));
    }

    public void updateSettings(PayrollSettings settings) {
        PayrollSettings current = getSettings();
        current.setSmmlv(settings.getSmmlv());
        current.setTransportAllowance(settings.getTransportAllowance());
        current.setHealthPercentage(settings.getHealthPercentage());
        current.setPensionPercentage(settings.getPensionPercentage());
        settingsRepository.save(current);
    }

    public List<Employee> getActiveEmployees() {
        return employeeRepository.findAll().stream()
                .filter(Employee::isActive)
                .collect(Collectors.toList());
    }

    public List<LiquidationItemDTO> prepareLiquidation(int year, int month) {
        List<Employee> employees = getActiveEmployees();
        List<AttendanceSummaryDTO> attendance = attendanceService.getMonthlySummaries(year, month);
        PayrollSettings settings = getSettings();
        
        List<LiquidationItemDTO> items = new ArrayList<>();
        for (Employee emp : employees) {
            LiquidationItemDTO dto = new LiquidationItemDTO();
            dto.setEmployeeId(emp.getId());
            dto.setEmployeeName(emp.getFullName());
            dto.setBaseSalary(emp.getBaseSalary());
            
            // Buscar días trabajados en asistencia
            int days = 0;
            if (emp.getSystemUser() != null) {
                String username = emp.getSystemUser().getUsername();
                days = attendance.stream()
                        .filter(a -> a.getUsername().equals(username))
                        .findFirst()
                        .map(a -> (int)a.getPresentCount())
                        .orElse(0);
            }
            
            dto.setDaysWorked(days);
            
            // Cálculos base
            BigDecimal propSalary = emp.getBaseSalary().multiply(new BigDecimal(days)).divide(new BigDecimal(30), 2, RoundingMode.HALF_UP);
            dto.setProportionalSalary(propSalary);
            
            // Aux Transporte (si gana <= 2 SMMLV)
            BigDecimal transport = BigDecimal.ZERO;
            if (emp.getBaseSalary().compareTo(settings.getSmmlv().multiply(new BigDecimal(2))) <= 0) {
                transport = settings.getTransportAllowance().multiply(new BigDecimal(days)).divide(new BigDecimal(30), 2, RoundingMode.HALF_UP);
            }
            dto.setTransportAllowance(transport);
            
            // Inicializar otros campos en 0
            dto.setCommissions(BigDecimal.ZERO);
            dto.setLibranza(BigDecimal.ZERO);
            dto.setEmbargo(BigDecimal.ZERO);
            
            recalculate(dto, settings);
            items.add(dto);
        }
        return items;
    }

    public void recalculate(LiquidationItemDTO dto, PayrollSettings settings) {
        BigDecimal baseIBC = dto.getProportionalSalary().add(dto.getCommissions());
        
        BigDecimal healthPct = new BigDecimal(settings.getHealthPercentage().toString()).divide(new BigDecimal("100"));
        BigDecimal pensionPct = new BigDecimal(settings.getPensionPercentage().toString()).divide(new BigDecimal("100"));

        BigDecimal health = baseIBC.multiply(healthPct).setScale(2, RoundingMode.HALF_UP);
        BigDecimal pension = baseIBC.multiply(pensionPct).setScale(2, RoundingMode.HALF_UP);
        
        dto.setHealth(health);
        dto.setPension(pension);
        
        BigDecimal earnings = dto.getProportionalSalary().add(dto.getTransportAllowance()).add(dto.getCommissions());
        BigDecimal deductions = health.add(pension).add(dto.getLibranza()).add(dto.getEmbargo());
        
        dto.setTotalEarnings(earnings);
        dto.setTotalDeductions(deductions);
        dto.setNetPay(earnings.subtract(deductions));
    }

    public void saveLiquidation(List<LiquidationItemDTO> items, String period) {
        for (LiquidationItemDTO item : items) {
            Employee emp = employeeRepository.findById(item.getEmployeeId()).orElseThrow();
            
            PayrollRecord record = new PayrollRecord();
            record.setEmployee(emp);
            record.setPayDate(LocalDate.now());
            record.setPeriod(period);
            record.setDaysWorked(item.getDaysWorked());
            record.setBaseSalary(item.getBaseSalary());
            record.setProportionalSalary(item.getProportionalSalary());
            record.setTransportAllowance(item.getTransportAllowance());
            record.setCommissions(item.getCommissions());
            record.setHealthDeduction(item.getHealth());
            record.setPensionDeduction(item.getPension());
            record.setLibranza(item.getLibranza());
            record.setEmbargo(item.getEmbargo());
            record.setTotalEarnings(item.getTotalEarnings());
            record.setTotalDeductions(item.getTotalDeductions());
            record.setNetSalary(item.getNetPay());
            record.setStatus("PAGADO");
            
            payrollRepository.save(record);
        }
    }

    public List<PayrollRecord> getHistory(Integer year, Integer month) {
        if (year != null && month != null) {
            String[] months = {"", "Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre"};
            String period = months[month] + " " + year;
            return payrollRepository.findAllByOrderByPayDateDesc().stream()
                    .filter(r -> period.equals(r.getPeriod()))
                    .collect(Collectors.toList());
        }
        return payrollRepository.findAllByOrderByPayDateDesc();
    }

    public void deleteRecord(Long id) {
        payrollRepository.deleteById(id);
    }

    public void exportToPdf(Long recordId, HttpServletResponse response) throws Exception {
        PayrollRecord record = payrollRepository.findById(recordId).orElseThrow();
        
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, response.getOutputStream());
        
        document.open();

        // Add Logo
        try {
            java.net.URL logoUrl = getClass().getResource("/static/images/logo.png");
            if (logoUrl != null) {
                com.lowagie.text.Image logo = com.lowagie.text.Image.getInstance(logoUrl);
                logo.scaleToFit(80, 80);
                logo.setAlignment(com.lowagie.text.Image.ALIGN_CENTER);
                document.add(logo);
            }
        } catch (Exception e) {
            System.err.println("Could not load logo: " + e.getMessage());
        }
        
        Paragraph companyName = new Paragraph("ADMINPRO S.A.S", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.GRAY));
        companyName.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(companyName);
        
        Font fontTitle = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        fontTitle.setSize(18);
        fontTitle.setColor(Color.BLUE);
        
        Paragraph p = new Paragraph("Comprobante de Nómina", fontTitle);
        p.setAlignment(Paragraph.ALIGN_CENTER);
        document.add(p);
        
        document.add(new Paragraph(" "));
        document.add(new Paragraph("Empleado: " + record.getEmployee().getFullName()));
        document.add(new Paragraph("Documento: " + record.getEmployee().getDocumentId()));
        document.add(new Paragraph("Periodo: " + record.getPeriod()));
        document.add(new Paragraph("Días Trabajados: " + record.getDaysWorked()));
        document.add(new Paragraph(" "));
        
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100f);
        table.setSpacingBefore(10);
        
        // Header
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(Color.LIGHT_GRAY);
        cell.setPadding(5);
        
        cell.setPhrase(new Phrase("Conceptos Devengados"));
        table.addCell(cell);
        cell.setPhrase(new Phrase("Conceptos Deducidos"));
        table.addCell(cell);
        
        // Data rows
        table.addCell("Sueldo Básico: " + formatMoney(record.getProportionalSalary()));
        table.addCell("Salud: " + formatMoney(record.getHealthDeduction()));
        
        table.addCell("Aux. Transporte: " + formatMoney(record.getTransportAllowance()));
        table.addCell("Pensión: " + formatMoney(record.getPensionDeduction()));
        
        table.addCell("Comisiones: " + formatMoney(record.getCommissions()));
        table.addCell("Libranza: " + formatMoney(record.getLibranza()));
        
        table.addCell(" ");
        table.addCell("Embargo: " + formatMoney(record.getEmbargo()));
        
        cell.setBackgroundColor(Color.WHITE);
        cell.setPhrase(new Phrase("Total Devengado: " + formatMoney(record.getTotalEarnings())));
        table.addCell(cell);
        cell.setPhrase(new Phrase("Total Deducido: " + formatMoney(record.getTotalDeductions())));
        table.addCell(cell);
        
        document.add(table);
        
        document.add(new Paragraph(" "));
        Font fontNet = FontFactory.getFont(FontFactory.HELVETICA_BOLD);
        fontNet.setSize(14);
        document.add(new Paragraph("NETO A PAGAR: " + formatMoney(record.getNetSalary()), fontNet));
        
        document.close();
    }
}
