package com.adminpro.repository;

import com.adminpro.model.JobManual;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface JobManualRepository extends JpaRepository<JobManual, Long> {
    List<JobManual> findAllByOrderByJobTitleAsc();
    List<JobManual> findByLevelContainingIgnoreCase(String level);
}
