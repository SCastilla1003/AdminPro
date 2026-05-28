package com.adminpro.repository;

import com.adminpro.model.PayrollSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayrollSettingsRepository extends JpaRepository<PayrollSettings, Long> {
}
