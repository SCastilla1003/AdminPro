package com.adminpro.controller;

import com.adminpro.model.PlanningEvent;
import com.adminpro.repository.PlanningEventRepository;
import com.adminpro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/planeacion")
@RequiredArgsConstructor
public class PlanningController {

    private final PlanningEventRepository eventRepository;
    private final UserRepository userRepository;
    private final com.adminpro.service.ActivityLogService activityLog;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("pageTitle", "Planeación Inteligente");
        model.addAttribute("pageSubtitle", "Organiza tareas y eventos de la empresa");
        model.addAttribute("activePage", "planeacion");
        model.addAttribute("users", userRepository.findAll());
        return "planeacion/index";
    }

    @GetMapping("/api/eventos")
    @ResponseBody
    public List<PlanningEvent> getEvents() {
        return eventRepository.findAllByOrderByStartDateAsc();
    }

    @PostMapping("/api/eventos")
    @ResponseBody
    public ResponseEntity<PlanningEvent> createEvent(@RequestBody PlanningEvent event) {
        PlanningEvent saved = eventRepository.save(event);
        activityLog.log("PLANNING", "CREATE", "Evento/Tarea '" + saved.getTitle() + "' creada");
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/api/eventos/{id}")
    @ResponseBody
    public ResponseEntity<PlanningEvent> updateEvent(@PathVariable Long id, @RequestBody PlanningEvent eventDetails) {
        PlanningEvent event = eventRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Evento no encontrado"));
        
        event.setTitle(eventDetails.getTitle());
        event.setDescription(eventDetails.getDescription());
        event.setStartDate(eventDetails.getStartDate());
        event.setEndDate(eventDetails.getEndDate());
        event.setCategory(eventDetails.getCategory());
        event.setColor(eventDetails.getColor());
        if (eventDetails.getStatus() != null) {
            event.setStatus(eventDetails.getStatus());
        }
        
        PlanningEvent updated = eventRepository.save(event);
        activityLog.log("PLANNING", "UPDATE", "Evento/Tarea '" + updated.getTitle() + "' actualizada");
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/api/eventos/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteEvent(@PathVariable Long id) {
        eventRepository.findById(id).ifPresent(e -> {
            activityLog.log("PLANNING", "DELETE", "Evento/Tarea '" + e.getTitle() + "' eliminada");
        });
        eventRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
