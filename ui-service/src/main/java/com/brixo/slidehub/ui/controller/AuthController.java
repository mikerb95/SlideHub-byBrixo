package com.brixo.slidehub.ui.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller de autenticación (HU-001, HU-002, HU-003).
 *
 * El POST de login lo procesa Spring Security directamente.
 * Este controller solo gestiona las vistas GET.
 */
@Controller
@RequestMapping("/auth")
public class AuthController {

    /**
     * Muestra el formulario de login.
     * Si la sesión ya está activa, redirige a /presenter (HU-001 §3).
     */
    @GetMapping("/login")
    public String loginPage(Authentication authentication,
                            @RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            Model model) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/presenter";
        }
        if (error != null) {
            // Mensaje genérico — sin indicar qué campo falló (HU-001 §2)
            model.addAttribute("errorMessage", "Credenciales incorrectas. Inténtalo de nuevo.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "Sesión cerrada correctamente.");
        }
        return "auth/login";
    }

    /**
     * Muestra el formulario de registro.
     * Fase 0: solo la vista estática. La lógica de usuario se implementa en Fase 1.
     */
    @GetMapping("/register")
    public String registerPage(Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/presenter";
        }
        return "auth/register";
    }
}
