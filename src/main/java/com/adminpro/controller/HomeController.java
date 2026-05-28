package com.adminpro.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {
        return "public/index";
    }

    // Temporary login stub just to avoid 404 for the button link
    @GetMapping("/login")
    public String login(Model model) {
        return "auth/login";
    }
}
