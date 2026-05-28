package com.adminpro.init;

import com.adminpro.model.Role;
import com.adminpro.model.User;
import com.adminpro.repository.RoleRepository;
import com.adminpro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 1. Crear roles base si no existen
        Role adminRole = roleRepository.findByName("ROLE_ADMIN")
            .orElseGet(() -> {
                Role r = new Role("ROLE_ADMIN", "Administrador del sistema con acceso total");
                log.info("✅ Rol ROLE_ADMIN creado.");
                return roleRepository.save(r);
            });

        roleRepository.findByName("ROLE_USER")
            .orElseGet(() -> {
                Role r = new Role("ROLE_USER", "Usuario estándar del sistema");
                log.info("✅ Rol ROLE_USER creado.");
                return roleRepository.save(r);
            });

        // 2. Crear usuario administrador por defecto si no existe
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setEmail("admin@adminpro.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setFullName("Administrador del Sistema");
            admin.setJobTitle("Administrador");
            admin.setEnabled(true);
            admin.setRoles(Set.of(adminRole));
            userRepository.save(admin);
            log.info("✅ Usuario 'admin' creado. Contraseña: admin123");
            log.info("⚠️  ¡Cambia la contraseña del admin en producción!");
        }

        log.info("🚀 AdminPro inicializado correctamente.");
    }
}
