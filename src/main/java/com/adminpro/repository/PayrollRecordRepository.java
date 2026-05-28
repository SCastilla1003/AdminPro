package com.adminpro.repository;

import com.adminpro.model.PayrollRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PayrollRecordRepository extends JpaRepository<PayrollRecord, Long> {
    List<PayrollRecord> findByEmployeeId(Long employeeId);
    List<PayrollRecord> findByPeriodOrderByEmployeeFullNameAsc(String period);
    List<PayrollRecord> findAllByOrderByPayDateDesc();

    @org.springframework.data.jpa.repository.Query("SELECT SUM(p.netSalary) FROM PayrollRecord p")
    Double sumNetSalary();
}
