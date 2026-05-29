package com.adminpro.config;

import com.adminpro.service.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsServiceImpl userDetailsService;

    @Value("${onlyoffice.document-server-url:https://onlinedocs.onlyoffice.com/}")
    private String onlyOfficeUrl;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/onlyoffice/callback")
            )
            .authenticationProvider(authenticationProvider())
            .authorizeHttpRequests(requests -> requests
                // Recursos públicos
                .requestMatchers("/", "/css/**", "/images/**", "/js/**", "/uploads/**").permitAll()
                .requestMatchers("/login").permitAll()
                .requestMatchers("/recuperar-password", "/restablecer-password").permitAll()
                .requestMatchers("/api/public/preview/**").permitAll()
                .requestMatchers("/api/onlyoffice/**").permitAll()
                .requestMatchers("/oo-proxy/**").permitAll()
                .requestMatchers("/fuera-de-horario").authenticated()

                // Dashboard: cualquier autenticado
                .requestMatchers("/dashboard").authenticated()

                // Asistencia: RRHH
                .requestMatchers("/asistencia/entrada", "/asistencia/salida").authenticated()
                .requestMatchers("/asistencia/config").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/asistencia/**").hasAnyAuthority("ROLE_ADMIN", "PERM_ATTENDANCE")

                // Nómina
                .requestMatchers("/nomina/**").hasAnyAuthority("ROLE_ADMIN", "PERM_PAYROLL")

                // Usuarios y Roles
                .requestMatchers("/usuarios/**").hasAnyAuthority("ROLE_ADMIN", "PERM_USERS")

                // Inventario
                .requestMatchers("/inventario/**").hasAnyAuthority("ROLE_ADMIN", "PERM_INVENTORY")

                // Planeación
                .requestMatchers("/planeacion/**").hasAnyAuthority("ROLE_ADMIN", "PERM_PLANNING")

                // Chat
                .requestMatchers("/chat/**").hasAnyAuthority("ROLE_ADMIN", "PERM_CHAT")

                // Documentos (si existe el módulo)
                .requestMatchers("/documentos/**").hasAnyAuthority("ROLE_ADMIN", "PERM_DASHBOARD")

                // Organigrama
                .requestMatchers("/organigrama/**").hasAnyAuthority("ROLE_ADMIN", "PERM_DASHBOARD")

                // Manual de Funciones — edición solo ADMIN, lectura con PERM_MANUAL
                .requestMatchers("/manual-funciones/nuevo", "/manual-funciones/guardar",
                                 "/manual-funciones/editar/**", "/manual-funciones/eliminar/**").hasAuthority("ROLE_ADMIN")
                .requestMatchers("/manual-funciones", "/manual-funciones/**").hasAnyAuthority("ROLE_ADMIN", "PERM_MANUAL")

                // Configuración: solo ADMIN
                .requestMatchers("/configuracion/**").hasAuthority("ROLE_ADMIN")

                // Notificaciones: cualquier autenticado
                .requestMatchers("/notificaciones/**").authenticated()

                // Perfil propio: cualquier autenticado
                .requestMatchers("/perfil/**").authenticated()

                // Todo lo demás requiere autenticación
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/dashboard", true)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .exceptionHandling(ex -> ex
                .accessDeniedPage("/acceso-denegado")
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.disable())
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net https://cdnjs.cloudflare.com; style-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://fonts.googleapis.com " + onlyOfficeUrl + "; font-src 'self' https://fonts.gstatic.com; img-src 'self' data: " + onlyOfficeUrl + "; connect-src 'self' wss: ws: https://cdn.jsdelivr.net https://cdnjs.cloudflare.com " + onlyOfficeUrl + "; frame-src 'self' " + onlyOfficeUrl + " https://view.officeapps.live.com; frame-ancestors 'self';")
                )
            );

        return http.build();
    }
}
