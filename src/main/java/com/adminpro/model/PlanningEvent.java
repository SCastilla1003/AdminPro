package com.adminpro.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "planning_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlanningEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Column(length = 20)
    private String category; // e.g., "Reunión", "Tarea", "Hito"

    @Column(length = 20)
    private String color; // e.g., "blue", "green", "red", "purple"

    @Column(length = 20)
    private String status = "TODO"; // TODO, IN_PROGRESS, DONE

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User assignedUser;
}
