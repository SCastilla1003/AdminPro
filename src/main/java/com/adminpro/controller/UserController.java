package com.adminpro.controller;

import com.adminpro.model.Role;
import com.adminpro.model.User;
import com.adminpro.repository.RoleRepository;
import com.adminpro.repository.UserRepository;
import com.adminpro.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/usuarios")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final ActivityLogService activityLog;

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("users", userRepository.findAll());
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("pageTitle", "Usuarios y Roles");
        model.addAttribute("pageSubtitle", "Gestión de usuarios y permisos del sistema");
        model.addAttribute("activePage", "usuarios");
        return "usuarios/index";
    }

    @GetMapping("/nuevo")
    public String newUserForm(Model model) {
        model.addAttribute("user", new User());
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("pageTitle", "Nuevo Usuario");
        model.addAttribute("pageSubtitle", "Crear un nuevo usuario en el sistema");
        model.addAttribute("activePage", "usuarios");
        return "usuarios/form";
    }

    @PostMapping("/guardar")
    public String saveUser(@ModelAttribute User user,
                           @RequestParam(value = "roleIds", required = false) List<Long> roleIds,
                           @RequestParam(value = "rawPassword", required = false) String rawPassword,
                           RedirectAttributes redirectAttributes) {

        boolean isNew = (user.getId() == null);
        User userToSave = user;

        if (!isNew) {
            User existingUser = userRepository.findById(user.getId()).orElse(user);
            existingUser.setFullName(user.getFullName());
            existingUser.setUsername(user.getUsername());
            existingUser.setEmail(user.getEmail());
            existingUser.setJobTitle(user.getJobTitle());
            existingUser.setBirthDate(user.getBirthDate());
            existingUser.setEnabled(user.isEnabled());
            userToSave = existingUser;
        }

        // Asignar roles
        Set<Role> selectedRoles = new HashSet<>();
        if (roleIds != null) {
            roleIds.forEach(rid -> {
                if (rid != null) {
                    roleRepository.findById(rid).ifPresent(selectedRoles::add);
                }
            });
        }
        userToSave.setRoles(selectedRoles);

        // Contraseña solo si fue provista
        if (rawPassword != null && !rawPassword.isBlank()) {
            userToSave.setPassword(passwordEncoder.encode(rawPassword));
        }

        userRepository.save(userToSave);

        // Registrar actividad
        String displayName = userToSave.getFullName() != null ? userToSave.getFullName() : userToSave.getUsername();
        if (isNew) {
            activityLog.log("USERS", "CREATE", "Usuario '" + displayName + "' creado");
        } else {
            activityLog.log("USERS", "UPDATE", "Usuario '" + displayName + "' actualizado");
        }

        redirectAttributes.addFlashAttribute("successMsg", "Usuario guardado exitosamente.");
        return "redirect:/usuarios";
    }

    @GetMapping("/editar/{id}")
    public String editUserForm(@PathVariable Long id, Model model) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado: " + id));
        model.addAttribute("user", user);
        model.addAttribute("roles", roleRepository.findAll());
        model.addAttribute("pageTitle", "Editar Usuario");
        model.addAttribute("pageSubtitle", "Modificar datos del usuario");
        model.addAttribute("activePage", "usuarios");
        return "usuarios/form";
    }

    @PostMapping("/eliminar/{id}")
    public String deleteUser(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            userRepository.findById(id).ifPresent(u -> {
                String name = u.getFullName() != null ? u.getFullName() : u.getUsername();
                activityLog.log("USERS", "DELETE", "Usuario '" + name + "' eliminado");
            });
            userRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMsg", "Usuario eliminado exitosamente.");
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            redirectAttributes.addFlashAttribute("errorMsg", "No se puede eliminar el usuario porque tiene registros asociados (asistencia, nómina, etc.). Se recomienda deshabilitarlo editando su perfil.");
        }
        return "redirect:/usuarios";
    }

    // ===== ROLES =====

    @GetMapping("/roles/nuevo")
    public String newRoleForm(Model model) {
        model.addAttribute("role", new Role());
        model.addAttribute("allPerms", List.of("PERM_DASHBOARD","PERM_PAYROLL","PERM_USERS","PERM_CHAT","PERM_INVENTORY","PERM_PLANNING","PERM_ATTENDANCE","PERM_MANUAL","PERM_VIEW_ORGANIGRAMA","PERM_EDIT_ORGANIGRAMA"));
        model.addAttribute("pageTitle", "Nuevo Rol");
        model.addAttribute("pageSubtitle", "Crear un nuevo rol en el sistema");
        model.addAttribute("activePage", "usuarios");
        return "roles/form";
    }

    @GetMapping("/roles/editar/{id}")
    public String editRoleForm(@PathVariable Long id, Model model) {
        Role role = roleRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado: " + id));
        model.addAttribute("role", role);
        model.addAttribute("allPerms", List.of("PERM_DASHBOARD","PERM_PAYROLL","PERM_USERS","PERM_CHAT","PERM_INVENTORY","PERM_PLANNING","PERM_ATTENDANCE","PERM_MANUAL","PERM_VIEW_ORGANIGRAMA","PERM_EDIT_ORGANIGRAMA"));
        model.addAttribute("pageTitle", "Editar Rol");
        model.addAttribute("pageSubtitle", "Modificar permisos del rol");
        model.addAttribute("activePage", "usuarios");
        return "roles/form";
    }

    @PostMapping("/roles/guardar")
    public String saveRole(@RequestParam(value = "id", required = false) Long id,
                           @RequestParam("name") String name,
                           @RequestParam("description") String description,
                           @RequestParam(value = "perms", required = false) List<String> perms,
                           RedirectAttributes redirectAttributes) {
        boolean isNew = (id == null);
        Role role = isNew ? new Role() : roleRepository.findById(id).orElse(new Role());

        String roleName = name.startsWith("ROLE_") ? name : "ROLE_" + name;
        role.setName(roleName.toUpperCase().replaceAll("\\s+", "_"));
        role.setDescription(description);
        role.setPermissions(perms != null ? new HashSet<>(perms) : new HashSet<>());
        
        System.out.println("DEBUG: Guardando rol " + name + " con permisos: " + perms);

        roleRepository.save(role);

        if (isNew) {
            activityLog.log("ROLES", "CREATE", "Rol '" + role.getName() + "' creado");
        } else {
            activityLog.log("ROLES", "UPDATE", "Rol '" + role.getName() + "' actualizado");
        }

        redirectAttributes.addFlashAttribute("successMsg", "Rol guardado exitosamente.");
        return "redirect:/usuarios";
    }

    @PostMapping("/roles/eliminar/{id}")
    public String deleteRole(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Role role = roleRepository.findById(id).orElse(null);
        if (role != null && !role.getName().equals("ROLE_ADMIN")) {
            activityLog.log("ROLES", "DELETE", "Rol '" + role.getName() + "' eliminado");
            roleRepository.deleteById(id);
            redirectAttributes.addFlashAttribute("successMsg", "Rol eliminado.");
        } else {
            redirectAttributes.addFlashAttribute("errorMsg", "No se puede eliminar el rol ADMIN.");
        }
        return "redirect:/usuarios";
    }
}
