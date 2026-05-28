package com.adminpro.repository;

import com.adminpro.model.PlanningEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlanningEventRepository extends JpaRepository<PlanningEvent, Long> {
    List<PlanningEvent> findAllByOrderByStartDateAsc();
}
