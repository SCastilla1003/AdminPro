package com.adminpro.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payroll_records")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    private LocalDate payDate;

    @Column(nullable = false)
    private String period; // Ej: "Mayo 2026"

    private int daysWorked;

    @Column(nullable = false)
    private BigDecimal baseSalary; // Salario base del contrato

    private BigDecimal proportionalSalary; // Salario según días trabajados
    private BigDecimal transportAllowance; // Auxilio de transporte
    private BigDecimal commissions; // Comisiones
    
    private BigDecimal healthDeduction; // 4% Salud
    private BigDecimal pensionDeduction; // 4% Pensión
    private BigDecimal libranza; 
    private BigDecimal embargo;

    private BigDecimal totalEarnings; // Total Devengado
    private BigDecimal totalDeductions; // Total Deducido

    @Column(nullable = false)
    private BigDecimal netSalary; // Neto a pagar

    private String status; // "PAGADO", "PENDIENTE"
}
