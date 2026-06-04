package com.adminpro.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "organigrama_diagram")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganigramaDiagram {

    @Id
    private Long id = 1L; // Solo necesitamos un registro global de organigrama

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String jsonData;
}