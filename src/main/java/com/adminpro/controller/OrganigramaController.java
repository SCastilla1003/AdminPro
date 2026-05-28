package com.adminpro.controller;

import com.adminpro.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/organigrama")
@RequiredArgsConstructor
public class OrganigramaController {

    private final EmployeeRepository employeeRepository;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("pageTitle", "Organigrama Institucional");
        model.addAttribute("pageSubtitle", "Estructura jerárquica de la empresa");
        model.addAttribute("activePage", "organigrama");
        model.addAttribute("employees", employeeRepository.findAll());
        return "organigrama/index";
    }
}
