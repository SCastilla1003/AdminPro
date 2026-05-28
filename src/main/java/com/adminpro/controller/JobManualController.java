package com.adminpro.controller;

import com.adminpro.model.JobManual;
import com.adminpro.repository.JobManualRepository;
import com.adminpro.service.ActivityLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/manual-funciones")
@RequiredArgsConstructor
public class JobManualController {

    private final JobManualRepository jobManualRepository;
    private final ActivityLogService activityLog;

    // ===== LISTA =====
    @GetMapping
    public String index(Model model) {
        model.addAttribute("manuals", jobManualRepository.findAllByOrderByJobTitleAsc());
        model.addAttribute("pageTitle", "Manual de Funciones");
        model.addAttribute("pageSubtitle", "Descripción de funciones y requisitos por cargo");
        model.addAttribute("activePage", "manual-funciones");
        return "manual-funciones/index";
    }

    // ===== DETALLE (ver) =====
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        JobManual manual = jobManualRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Manual no encontrado: " + id));
        model.addAttribute("manual", manual);
        model.addAttribute("pageTitle", manual.getJobTitle());
        model.addAttribute("pageSubtitle", "Manual de funciones — " + (manual.getLevel() != null ? manual.getLevel() : ""));
        model.addAttribute("activePage", "manual-funciones");
        return "manual-funciones/detail";
    }

    // ===== FORMULARIO NUEVO (solo ADMIN) =====
    @GetMapping("/nuevo")
    public String newForm(Model model) {
        model.addAttribute("manual", new JobManual());
        model.addAttribute("pageTitle", "Nuevo Manual de Funciones");
        model.addAttribute("pageSubtitle", "Crear descripción de funciones para un cargo");
        model.addAttribute("activePage", "manual-funciones");
        return "manual-funciones/form";
    }

    // ===== FORMULARIO EDITAR (solo ADMIN) =====
    @GetMapping("/editar/{id}")
    public String editForm(@PathVariable Long id, Model model) {
        JobManual manual = jobManualRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Manual no encontrado: " + id));
        model.addAttribute("manual", manual);
        model.addAttribute("pageTitle", "Editar Manual de Funciones");
        model.addAttribute("pageSubtitle", "Modificar descripción de funciones");
        model.addAttribute("activePage", "manual-funciones");
        return "manual-funciones/form";
    }

    // ===== GUARDAR (solo ADMIN) =====
    @PostMapping("/guardar")
    public String save(@ModelAttribute JobManual manual, RedirectAttributes ra) {
        boolean isNew = (manual.getId() == null);
        jobManualRepository.save(manual);

        if (isNew) {
            activityLog.log("MANUAL", "CREATE", "Manual de funciones '" + manual.getJobTitle() + "' creado");
        } else {
            activityLog.log("MANUAL", "UPDATE", "Manual de funciones '" + manual.getJobTitle() + "' actualizado");
        }

        ra.addFlashAttribute("successMsg", "Manual de funciones guardado correctamente.");
        return "redirect:/manual-funciones";
    }

    // ===== ELIMINAR (solo ADMIN) =====
    @PostMapping("/eliminar/{id}")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        jobManualRepository.findById(id).ifPresent(m ->
            activityLog.log("MANUAL", "DELETE", "Manual de funciones '" + m.getJobTitle() + "' eliminado")
        );
        jobManualRepository.deleteById(id);
        ra.addFlashAttribute("successMsg", "Manual eliminado.");
        return "redirect:/manual-funciones";
    }
}
