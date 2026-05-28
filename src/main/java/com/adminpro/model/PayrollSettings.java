package com.adminpro.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "payroll_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PayrollSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private BigDecimal smmlv; // Salario Mínimo Mensual Legal Vigente

    @Column(nullable = false)
    private BigDecimal transportAllowance; // Auxilio de Transporte

    @Column(nullable = false)
    private Double healthPercentage; // e.g., 4.0

    @Column(nullable = false)
    private Double pensionPercentage; // e.g., 4.0

    public static PayrollSettings defaultSettings() {
        return new PayrollSettings(null, 
            new BigDecimal("1600000"), 
            new BigDecimal("220000"), 
            4.0, 
            4.0);
    }
}
