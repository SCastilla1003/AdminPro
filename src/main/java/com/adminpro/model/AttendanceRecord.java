package com.adminpro.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private LocalDate date;

    private LocalTime timeIn;
    
    private LocalTime timeOut;

    @Column(nullable = false)
    private String status; // A_TIEMPO, TARDE, AUSENTE

    private boolean completedAutomated; // Si la salida fue marcada automáticamente
}
