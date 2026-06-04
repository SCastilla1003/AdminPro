package com.adminpro.controller;

import com.adminpro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/organigrama")
@RequiredArgsConstructor
public class OrganigramaController {

    private final UserRepository userRepository;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("pageTitle", "Organigrama Institucional");
        model.addAttribute("pageSubtitle", "Lista de usuarios del sistema");
        model.addAttribute("activePage", "organigrama");
        model.addAttribute("users", userRepository.findAll());
        return "organigrama/index";
    }
}
