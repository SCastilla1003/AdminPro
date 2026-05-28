package com.adminpro.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LiquidationItemDTO {
    private Long employeeId;
    private String employeeName;
    private int daysWorked;
    private BigDecimal baseSalary;
    private BigDecimal proportionalSalary;
    private BigDecimal transportAllowance;
    private BigDecimal commissions;
    private BigDecimal health;
    private BigDecimal pension;
    private BigDecimal libranza;
    private BigDecimal embargo;
    private BigDecimal totalEarnings;
    private BigDecimal totalDeductions;
    private BigDecimal netPay;
}
