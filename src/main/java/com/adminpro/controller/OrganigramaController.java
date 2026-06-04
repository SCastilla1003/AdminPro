package com.adminpro.controller;

import com.adminpro.model.OrganigramaDiagram;
import com.adminpro.repository.OrganigramaDiagramRepository;
import com.adminpro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/organigrama")
@RequiredArgsConstructor
public class OrganigramaController {

    private final UserRepository userRepository;
    private final OrganigramaDiagramRepository organigramaDiagramRepository;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("pageTitle", "Organigrama");
        model.addAttribute("pageSubtitle", "Estructura interactiva de la empresa");
        model.addAttribute("activePage", "organigrama");
        model.addAttribute("users", userRepository.findAll());

        // Verificar si el usuario actual tiene permisos de edición
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean canEdit = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("PERM_EDIT_ORGANIGRAMA"));
        model.addAttribute("canEdit", canEdit);

        return "organigrama/index";
    }

    @GetMapping("/data")
    @ResponseBody
    public ResponseEntity<Map<String, String>> getData() {
        OrganigramaDiagram diagram = organigramaDiagramRepository.findById(1L)
                .orElse(new OrganigramaDiagram(1L, "{\"nodes\":[],\"connections\":[]}"));
        Map<String, String> response = new HashMap<>();
        response.put("jsonData", diagram.getJsonData());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/save")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'PERM_EDIT_ORGANIGRAMA')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, String> payload) {
        String jsonData = payload.get("jsonData");
        OrganigramaDiagram diagram = organigramaDiagramRepository.findById(1L)
                .orElse(new OrganigramaDiagram(1L, "{\"nodes\":[],\"connections\":[]}"));
        diagram.setJsonData(jsonData);
        organigramaDiagramRepository.save(diagram);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Organigrama guardado con éxito");
        return ResponseEntity.ok(response);
    }
}