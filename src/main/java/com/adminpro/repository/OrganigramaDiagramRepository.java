package com.adminpro.repository;

import com.adminpro.model.OrganigramaDiagram;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganigramaDiagramRepository extends JpaRepository<OrganigramaDiagram, Long> {
}