package com.adminpro.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Módulo que generó el evento: USERS, PAYROLL, INVENTORY, ROLES */
    @Column(nullable = false, length = 30)
    private String module;

    /** Tipo de acción: CREATE, UPDATE, DELETE */
    @Column(nullable = false, length = 20)
    private String action;

    /** Descripción legible del evento */
    @Column(nullable = false, length = 255)
    private String description;

    /** Usuario que realizó la acción */
    @Column(nullable = false, length = 100)
    private String performedBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
