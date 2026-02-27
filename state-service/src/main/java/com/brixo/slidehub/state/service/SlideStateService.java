package com.brixo.slidehub.state.service;

import com.brixo.slidehub.state.model.SlideStateResponse;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Gestiona el estado del slide actual en Redis.
 * La clave Redis "current_slide" guarda: { "slide": N }
 * El campo totalSlides se calcula dinámicamente desde el directorio de slides.
 */
@Service
public class SlideStateService {

    private static final Logger log = LoggerFactory.getLogger(SlideStateService.class);
    private static final String SLIDE_KEY = "current_slide";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${slidehub.slides.directory:./slides}")
    private String slidesDirectory;

    public SlideStateService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Devuelve el slide actual y el total de slides (HU-008).
     * Si no hay estado previo, retorna slide=1.
     */
    public SlideStateResponse getCurrentSlide() {
        String raw = redis.opsForValue().get(SLIDE_KEY);
        int slide = 1;
        if (raw != null) {
            try {
                slide = objectMapper.readTree(raw).path("slide").asInt(1);
            } catch (Exception e) {
                log.warn("Error parseando estado de slide desde Redis: {}", e.getMessage());
            }
        }
        return new SlideStateResponse(slide, countSlides());
    }

    /**
     * Establece el slide actual. Respeta los límites [1, totalSlides] (HU-004
     * §3,§4).
     * Si totalSlides = 0, guarda el valor tal cual (sin slides importados aún).
     */
    public SlideStateResponse setSlide(int requestedSlide) {
        int total = countSlides();
        int bounded = total > 0
                ? Math.max(1, Math.min(requestedSlide, total))
                : Math.max(1, requestedSlide);
        try {
            String json = objectMapper.writeValueAsString(Map.of("slide", bounded));
            redis.opsForValue().set(SLIDE_KEY, json);
        } catch (Exception e) {
            log.error("Error guardando estado de slide en Redis: {}", e.getMessage());
        }
        return new SlideStateResponse(bounded, total);
    }

    /**
     * Cuenta los archivos de imagen en el directorio de slides configurado.
     * Retorna 0 si el directorio no existe o no se puede leer.
     */
    private int countSlides() {
        Path dir = Path.of(slidesDirectory);
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var stream = Files.list(dir)) {
            return (int) stream
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
                    })
                    .count();
        } catch (IOException e) {
            log.warn("Error contando slides en {}: {}", dir, e.getMessage());
            return 0;
        }
    }
}
