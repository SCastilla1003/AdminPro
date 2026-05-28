package com.adminpro.config;

import com.adminpro.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class WorkingHoursInterceptor implements HandlerInterceptor {

    private final AttendanceService attendanceService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String uri = request.getRequestURI();
        
        // Ignorar recursos estáticos y rutas de login/logout/errores
        if (uri.startsWith("/css/") || uri.startsWith("/js/") || uri.startsWith("/images/") || 
            uri.equals("/login") || uri.equals("/logout") || uri.equals("/fuera-de-horario") ||
            uri.startsWith("/error")) {
            return true;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName().equals("anonymousUser")) {
            return true; // Dejar que Spring Security maneje usuarios no autenticados
        }

        // Si es administrador, tiene acceso libre siempre
        boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            return true;
        }

        // Si la plataforma está cerrada, redirigir a fuera-de-horario
        if (!attendanceService.isPlatformOpen()) {
            response.sendRedirect("/fuera-de-horario");
            return false;
        }

        return true;
    }
}
