package com.adminpro.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_manuals")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobManual {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ===== IDENTIFICACIÓN =====
    @Column(nullable = false, length = 200)
    private String jobTitle; // Denominación del Empleo

    private String level;       // Nivel (Profesional, Técnico, etc.)
    private String code;        // Código del empleo
    private String grade;       // Grado
    private String numPositions;// Número de Cargos
    private String regional;    // Regional
    private String department;  // Dependencia
    private String supervisorTitle; // Cargo del Jefe Inmediato

    // ===== ÁREA FUNCIONAL =====
    @Column(length = 500)
    private String functionalArea; // Área Funcional

    @Column(length = 500)
    private String process; // Proceso

    @Column(length = 500)
    private String program; // Programa

    // ===== PROPÓSITO PRINCIPAL =====
    @Column(columnDefinition = "TEXT")
    private String mainPurpose;

    // ===== FUNCIONES ESENCIALES (una por línea) =====
    @Column(columnDefinition = "TEXT")
    private String essentialFunctions;

    // ===== CONOCIMIENTOS BÁSICOS (uno por línea) =====
    @Column(columnDefinition = "TEXT")
    private String basicKnowledge;

    // ===== COMPETENCIAS =====
    @Column(columnDefinition = "TEXT")
    private String commonCompetencies; // Comportamentales Comunes

    @Column(columnDefinition = "TEXT")
    private String hierarchicalCompetencies; // Comportamentales Nivel Jerárquico

    // ===== REQUISITOS ACADÉMICOS (texto libre con alternativas) =====
    @Column(columnDefinition = "TEXT")
    private String academicRequirements;

    // ===== AUDITORÍA =====
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
