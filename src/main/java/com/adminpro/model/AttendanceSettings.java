package com.adminpro.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalTime;

@Data
@Entity
@Table(name = "attendance_settings")
public class AttendanceSettings {
    @Id
    private Long id = 1L; // Singleton

    private LocalTime openTime;
    private LocalTime closeTime;
    private int gracePeriodMinutes;
    private String timezone;
    private boolean restrictWeekends;

    public AttendanceSettings() {
        this.openTime = LocalTime.of(7, 0);
        this.closeTime = LocalTime.of(17, 0);
        this.gracePeriodMinutes = 15;
        this.timezone = "America/Bogota";
        this.restrictWeekends = true;
    }
}
