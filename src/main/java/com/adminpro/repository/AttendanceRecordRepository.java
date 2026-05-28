package com.adminpro.repository;

import com.adminpro.model.AttendanceRecord;
import com.adminpro.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, Long> {
    Optional<AttendanceRecord> findByUserAndDate(User user, LocalDate date);
    List<AttendanceRecord> findByDateOrderByTimeInDesc(LocalDate date);
    List<AttendanceRecord> findByUserOrderByDateDesc(User user);
    List<AttendanceRecord> findByDateAndTimeOutIsNull(LocalDate date);

    List<AttendanceRecord> findByUserAndDateBetweenOrderByDateDesc(User user, LocalDate start, LocalDate end);
    
    @org.springframework.data.jpa.repository.Query("SELECT r FROM AttendanceRecord r WHERE r.user = :user AND YEAR(r.date) = :year ORDER BY r.date DESC")
    List<AttendanceRecord> findByUserAndYear(@org.springframework.data.repository.query.Param("user") User user, @org.springframework.data.repository.query.Param("year") int year);
}
