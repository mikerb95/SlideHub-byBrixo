package com.brixo.slidehub.ai.controller;

import com.brixo.slidehub.ai.model.PresenterNote;
import com.brixo.slidehub.ai.repository.PresenterNoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Controller de notas del presentador (HU-016, HU-017, HU-018, HU-019, HU-020).
 *
 * Fase 0: implementa solo endpoints de lectura/borrado con MongoDB.
 * Fase 2 añadirá GeminiService + GroqService para el endpoint generate.
 */
@RestController
@RequestMapping("/api/ai/notes")
public class NotesController {

    private static final Logger log = LoggerFactory.getLogger(NotesController.class);

    private final PresenterNoteRepository repository;

    public NotesController(PresenterNoteRepository repository) {
        this.repository = repository;
    }

    /**
     * Health check del servicio de IA (HU-020).
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "ai-service"));
    }

    /**
     * Genera nota para un slide vía IA (HU-016).
     * Fase 0: devuelve 501 Not Implemented — se implementará en Fase 2 con Gemini + Groq.
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generate(@RequestBody Map<String, Object> request) {
        log.info("generate() invocado — Gemini+Groq pendientes de Fase 2. Payload: {}", request);
        return ResponseEntity.status(501)
                .body(Map.of("success", false, "errorMessage",
                        "Generación de notas con IA disponible en Fase 2."));
    }

    /**
     * Lista todas las notas de una presentación (HU-017).
     */
    @GetMapping("/{presentationId}")
    public ResponseEntity<List<PresenterNote>> listNotes(@PathVariable String presentationId) {
        List<PresenterNote> notes = repository.findByPresentationIdOrderBySlideNumberAsc(presentationId);
        return ResponseEntity.ok(notes);
    }

    /**
     * Obtiene la nota de un slide específico (HU-018).
     * Responde 204 No Content si no existe — nunca 404 (HU-018 §2).
     */
    @GetMapping("/{presentationId}/{slideNumber}")
    public ResponseEntity<PresenterNote> getNote(@PathVariable String presentationId,
                                                  @PathVariable int slideNumber) {
        return repository.findByPresentationIdAndSlideNumber(presentationId, slideNumber)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Elimina todas las notas de una presentación (HU-019).
     * Responde 204 No Content aunque no existan notas (HU-019 §2).
     */
    @DeleteMapping("/{presentationId}")
    public ResponseEntity<Void> deleteNotes(@PathVariable String presentationId) {
        repository.deleteByPresentationId(presentationId);
        return ResponseEntity.noContent().build();
    }
}
