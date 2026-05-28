package com.adminpro.service;

import com.adminpro.model.ActivityLog;
import com.adminpro.repository.ActivityLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private final ActivityLogRepository repository;

    /**
     * Registra un evento de actividad obteniendo el usuario actual de Spring Security.
     * @param module  Módulo: USERS, PAYROLL, INVENTORY, ROLES, PLANNING
     * @param action  Acción: CREATE, UPDATE, DELETE
     * @param description Texto legible, ej: "Usuario 'juan' creado"
     */
    public void log(String module, String action, String description) {
        String performer = "sistema";
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                performer = auth.getName();
            }
        } catch (Exception ignored) {}

        repository.save(ActivityLog.builder()
                .module(module)
                .action(action)
                .description(description)
                .performedBy(performer)
                .build());
    }

    /** Devuelve los N eventos más recientes para el dashboard. */
    public List<ActivityLog> getRecent(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
    }

    /** Devuelve los eventos recientes filtrados por los permisos del usuario. */
    public List<ActivityLog> getRecentForUser(int limit) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return List.of();

        // Si es ADMIN, ver todo
        if (auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            return getRecent(limit);
        }

        // Mapear permisos a módulos
        java.util.List<String> allowedModules = new java.util.ArrayList<>();
        auth.getAuthorities().forEach(a -> {
            String authority = a.getAuthority();
            if (authority.equals("PERM_USERS")) allowedModules.add("USERS");
            if (authority.equals("PERM_USERS")) allowedModules.add("ROLES");
            if (authority.equals("PERM_PAYROLL")) allowedModules.add("PAYROLL");
            if (authority.equals("PERM_INVENTORY")) allowedModules.add("INVENTORY");
            if (authority.equals("PERM_PLANNING")) allowedModules.add("PLANNING");
            if (authority.equals("PERM_CHAT")) allowedModules.add("CHAT");
            if (authority.equals("PERM_DASHBOARD")) allowedModules.add("DOCUMENTS");
        });

        if (allowedModules.isEmpty()) return List.of();
        return repository.findAllByModuleInOrderByCreatedAtDesc(allowedModules, PageRequest.of(0, limit));
    }

    /** Devuelve todos los eventos (para la vista de historial completo). */
    public List<ActivityLog> getAll() {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 200));
    }
}
