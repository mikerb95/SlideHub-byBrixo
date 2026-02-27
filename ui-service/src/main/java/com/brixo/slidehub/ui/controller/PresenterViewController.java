package com.brixo.slidehub.ui.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller de vistas protegidas del presentador (HU-006, HU-007).
 *
 * Fase 0: sin llamadas a ai-service — notas del presentador se añadirán en Fase 2.
 */
@Controller
public class PresenterViewController {

    @Value("${slidehub.poll.presenter.interval-ms:1500}")
    private int presenterPollIntervalMs;

    @Value("${slidehub.poll.slides.interval-ms:1000}")
    private int slidePollIntervalMs;

    /**
     * Vista del presentador con notas y preview (HU-006).
     * Requiere rol PRESENTER o ADMIN (configurado en SecurityConfig).
     */
    @GetMapping("/presenter")
    public String presenterView(Model model) {
        model.addAttribute("pollIntervalMs", presenterPollIntervalMs);
        return "presenter";
    }

    /**
     * Panel maestro para tablet 11" (HU-007).
     * Requiere rol PRESENTER o ADMIN (configurado en SecurityConfig).
     */
    @GetMapping("/main-panel")
    public String mainPanelView(Model model) {
        model.addAttribute("pollIntervalMs", slidePollIntervalMs);
        return "main-panel";
    }
}
