package com.adminpro.service;

import com.adminpro.model.User;
import com.adminpro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado: " + username));

        // Cargamos ROLE_* y también los permisos PERM_* de cada rol
        var authorities = new HashSet<SimpleGrantedAuthority>();
        user.getRoles().forEach(role -> {
            // Añadir el nombre del rol (ej: ROLE_ADMIN)
            if (role.getName() != null && !role.getName().isBlank()) {
                authorities.add(new SimpleGrantedAuthority(role.getName()));
            }
            // Añadir cada permiso asignado al rol (ej: PERM_PAYROLL)
            if (role.getPermissions() != null) {
                role.getPermissions().forEach(perm -> {
                    if (perm != null && !perm.isBlank()) {
                        authorities.add(new SimpleGrantedAuthority(perm));
                    }
                });
            }
        });

        return org.springframework.security.core.userdetails.User.builder()
            .username(user.getUsername())
            .password(user.getPassword())
            .authorities(authorities)
            .disabled(!user.isEnabled())
            .build();
    }
}
