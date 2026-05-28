package com.adminpro.service;

import com.adminpro.model.AttendanceRecord;
import com.adminpro.model.AttendanceSettings;
import com.adminpro.model.User;
import com.adminpro.repository.AttendanceRecordRepository;
import com.adminpro.repository.AttendanceSettingsRepository;
import com.adminpro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import com.adminpro.dto.AttendanceSummaryDTO;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private final AttendanceSettingsRepository settingsRepository;
    private final AttendanceRecordRepository recordRepository;
    private final UserRepository userRepository;

    public AttendanceSettings getSettings() {
        return settingsRepository.findById(1L).orElseGet(() -> {
            AttendanceSettings defaultSettings = new AttendanceSettings();
            return settingsRepository.save(defaultSettings);
        });
    }

    public AttendanceSettings saveSettings(AttendanceSettings settings) {
        settings.setId(1L);
        return settingsRepository.save(settings);
    }

    public ZoneId getConfiguredZoneId() {
        return ZoneId.of(getSettings().getTimezone());
    }

    public boolean isPlatformOpen() {
        AttendanceSettings settings = getSettings();
        ZonedDateTime now = ZonedDateTime.now(getConfiguredZoneId());
        
        // Comprobar fines de semana
        if (settings.isRestrictWeekends() && (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY)) {
            return false;
        }

        LocalTime currentTime = now.toLocalTime();
        return !currentTime.isBefore(settings.getOpenTime()) && !currentTime.isAfter(settings.getCloseTime());
    }

    public AttendanceRecord getTodayRecordForCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return null;

        LocalDate today = LocalDate.now(getConfiguredZoneId());
        return recordRepository.findByUserAndDate(userOpt.get(), today).orElse(null);
    }

    public String registerClockIn() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        AttendanceSettings settings = getSettings();
        ZoneId zone = getConfiguredZoneId();
        LocalDate today = LocalDate.now(zone);
        LocalTime currentTime = LocalTime.now(zone);

        Optional<AttendanceRecord> existing = recordRepository.findByUserAndDate(user, today);
        if (existing.isPresent()) {
            return "Ya has registrado tu entrada hoy.";
        }

        AttendanceRecord record = new AttendanceRecord();
        record.setUser(user);
        record.setDate(today);
        record.setTimeIn(currentTime);
        record.setCompletedAutomated(false);

        LocalTime maxOnTime = settings.getOpenTime().plusMinutes(settings.getGracePeriodMinutes());
        if (currentTime.isBefore(maxOnTime) || currentTime.equals(maxOnTime)) {
            record.setStatus("A_TIEMPO");
        } else {
            record.setStatus("TARDE");
        }

        recordRepository.save(record);
        return "Entrada registrada exitosamente.";
    }

    public String registerClockOut() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
        
        LocalDate today = LocalDate.now(getConfiguredZoneId());
        AttendanceRecord record = recordRepository.findByUserAndDate(user, today)
                .orElseThrow(() -> new RuntimeException("No has registrado tu entrada hoy."));

        if (record.getTimeOut() != null) {
            return "Ya has registrado tu salida hoy.";
        }

        record.setTimeOut(LocalTime.now(getConfiguredZoneId()));
        recordRepository.save(record);
        return "Salida registrada exitosamente.";
    }

    // Tarea programada que se ejecuta cada minuto, pero solo actúa cuando es hora de cierre
    @Scheduled(cron = "0 * * * * *")
    public void processDailyAttendanceClosure() {
        AttendanceSettings settings = getSettings();
        ZoneId zone = getConfiguredZoneId();
        ZonedDateTime now = ZonedDateTime.now(zone);

        // Si es fin de semana y está restringido, no procesamos ausencias
        if (settings.isRestrictWeekends() && (now.getDayOfWeek() == DayOfWeek.SATURDAY || now.getDayOfWeek() == DayOfWeek.SUNDAY)) {
            return;
        }

        LocalTime currentTime = now.toLocalTime();
        
        // Verificamos si acaba de pasar la hora de cierre (por ejemplo, es exactamente el minuto del cierre)
        if (currentTime.getHour() == settings.getCloseTime().getHour() && 
            currentTime.getMinute() == settings.getCloseTime().getMinute()) {
            
            log.info("Iniciando proceso automático de cierre de asistencia...");
            LocalDate today = now.toLocalDate();
            
            List<User> activeUsers = userRepository.findAll().stream()
                    .filter(User::isEnabled)
                    .toList();

            for (User user : activeUsers) {
                // Saltar ADMINs
                if (user.getRoles().stream().anyMatch(r -> r.getName().equals("ROLE_ADMIN"))) {
                    continue;
                }

                Optional<AttendanceRecord> recordOpt = recordRepository.findByUserAndDate(user, today);
                if (recordOpt.isEmpty()) {
                    // Marcar Ausente
                    AttendanceRecord absent = new AttendanceRecord();
                    absent.setUser(user);
                    absent.setDate(today);
                    absent.setStatus("AUSENTE");
                    absent.setCompletedAutomated(true);
                    recordRepository.save(absent);
                    log.info("Marcado AUSENTE: {}", user.getUsername());
                } else {
                    // Completar salida si falta
                    AttendanceRecord record = recordOpt.get();
                    if (record.getTimeOut() == null && !record.getStatus().equals("AUSENTE")) {
                        record.setTimeOut(settings.getCloseTime());
                        record.setCompletedAutomated(true);
                        recordRepository.save(record);
                        log.info("Salida automática completada: {}", user.getUsername());
                    }
                }
            }
        }
    }
    
    public List<AttendanceRecord> getTodayRecords() {
        return recordRepository.findByDateOrderByTimeInDesc(LocalDate.now(getConfiguredZoneId()));
    }

    public List<AttendanceSummaryDTO> getMonthlySummaries(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = YearMonth.of(year, month).atEndOfMonth();
        
        List<User> users = userRepository.findAll();

        List<AttendanceSummaryDTO> summaries = new ArrayList<>();
        for (User user : users) {
            List<AttendanceRecord> records = recordRepository.findByUserAndDateBetweenOrderByDateDesc(user, start, end);
            summaries.add(buildSummary(user, records));
        }
        return summaries;
    }

    public List<AttendanceSummaryDTO> getAnnualSummaries(int year) {
        List<User> users = userRepository.findAll();

        List<AttendanceSummaryDTO> summaries = new ArrayList<>();
        for (User user : users) {
            List<AttendanceRecord> records = recordRepository.findByUserAndYear(user, year);
            summaries.add(buildSummary(user, records));
        }
        return summaries;
    }

    private AttendanceSummaryDTO buildSummary(User user, List<AttendanceRecord> records) {
        long onTime = records.stream().filter(r -> "A_TIEMPO".equals(r.getStatus())).count();
        long late = records.stream().filter(r -> "TARDE".equals(r.getStatus())).count();
        long absent = records.stream().filter(r -> "AUSENTE".equals(r.getStatus())).count();

        return AttendanceSummaryDTO.builder()
                .username(user.getUsername())
                .fullName(user.getFullName() != null ? user.getFullName() : user.getUsername())
                .onTimeCount(onTime)
                .lateCount(late)
                .absentCount(absent)
                .totalDays(records.size())
                .build();
    }
}
