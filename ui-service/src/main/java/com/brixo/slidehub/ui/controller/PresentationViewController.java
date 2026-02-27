package com.brixo.slidehub.ui.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller que sirve las vistas públicas de presentación (HU-005, HU-004, HU-010, HU-012).
 *
 * No mantiene estado propio — todo el estado viene de state-service vía polling JS
 * o llamadas WebClient en fases posteriores.
 */
@Controller
public class PresentationViewController {

    @Value("${slidehub.poll.slides.interval-ms:1000}")
    private int slidePollIntervalMs;

    @Value("${slidehub.poll.demo.interval-ms:800}")
    private int demoPollIntervalMs;

    /**
     * Vista del proyector/TV (HU-005) — polling a /api/slide para sincronización.
     */
    @GetMapping("/slides")
    public String slidesView(Model model) {
        model.addAttribute("pollIntervalMs", slidePollIntervalMs);
        return "slides";
    }

    /**
     * Control remoto para smartphone (HU-004).
     */
    @GetMapping("/remote")
    public String remoteView(Model model) {
        model.addAttribute("pollIntervalMs", slidePollIntervalMs);
        return "remote";
    }

    /**
     * Pantalla dual slides/iframe (HU-010, HU-011) — polling a /api/demo.
     */
    @GetMapping("/demo")
    public String demoView(Model model) {
        model.addAttribute("pollIntervalMs", demoPollIntervalMs);
        return "demo";
    }

    /**
     * Landing page pública (HU-012).
     */
    @GetMapping("/showcase")
    public String showcaseView() {
        return "showcase";
    }
}
