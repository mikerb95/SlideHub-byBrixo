package com.brixo.slidehub.ai.service;

import com.brixo.slidehub.ai.model.NoteContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP para Groq API — generación de notas del presentador (CLAUDE.md
 * §9.2,
 * PLAN-EXPANSION.md Fase 3, tarea 29).
 *
 * Integración exclusivamente vía HTTP / WebClient — sin SDK de Groq.
 * Usa la API compatible con OpenAI de Groq:
 * {@code POST /openai/v1/chat/completions}.
 */
@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);

    private final WebClient groqClient;
    private final ObjectMapper objectMapper;

    @Value("${slidehub.ai.groq.api-key}")
    private String apiKey;

    @Value("${slidehub.ai.groq.model}")
    private String model;

    public GroqService(@Value("${slidehub.ai.groq.base-url}") String baseUrl,
            ObjectMapper objectMapper) {
        this.groqClient = WebClient.builder()
                .baseUrl(baseUrl)
                .build();
        this.objectMapper = objectMapper;
    }

    // ── Generación de notas del presentador ───────────────────────────────────

    /**
     * Genera notas estructuradas del presentador para un slide (PLAN-EXPANSION.md
     * Fase 3, tarea 29).
     *
     * @param repoContext      contexto técnico extraído del repositorio por Gemini
     * @param slideDescription descripción del slide (de Vision o fallback textual)
     * @param slideNumber      número 1-based del slide
     * @return notas estructuradas parseadas desde la respuesta JSON de Groq
     */
    public NoteContent generateNote(String repoContext, String slideDescription, int slideNumber) {
        String prompt = buildNotePrompt(repoContext, slideDescription, slideNumber);

        var requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system",
                                "content",
                                "Eres un asistente que genera notas de presentación en español. "
                                        + "Responde SIEMPRE en JSON válido, sin markdown ni texto adicional."),
                        Map.of("role", "user", "content", prompt)),
                "temperature", 0.7,
                "max_tokens", 1024);

        try {
            Map<?, ?> response = groqClient.post()
                    .uri("/openai/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String rawJson = extractMessageContent(response);
            rawJson = stripMarkdownJson(rawJson);
            log.debug("Groq generateNote respondió (slide {}): {} chars", slideNumber, rawJson.length());

            return objectMapper.readValue(rawJson, NoteContent.class);

        } catch (Exception e) {
            log.error("Error generando nota con Groq (slide {}): {}", slideNumber, e.getMessage());
            // Devuelve nota de fallback en lugar de propagar la excepción
            return new NoteContent(
                    "Slide " + slideNumber,
                    List.of("No se pudo generar la nota con IA. " + e.getMessage()),
                    "~2 min",
                    List.of(),
                    List.of());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildNotePrompt(String repoContext, String slideDescription, int slideNumber) {
        StringBuilder sb = new StringBuilder();

        if (slideDescription != null && !slideDescription.isBlank()) {
            sb.append("Descripción del slide %d: %s\n\n".formatted(slideNumber, slideDescription));
        }

        if (repoContext != null && !repoContext.isBlank()) {
            sb.append("Contexto técnico del repositorio:\n%s\n\n".formatted(repoContext));
        }

        sb.append("""
                Genera notas del presentador en JSON exactamente con este formato (sin texto extra):
                {
                  "title": "Título corto y descriptivo del slide",
                  "points": ["punto técnico 1", "punto técnico 2", "punto técnico 3"],
                  "suggestedTime": "~2 min",
                  "keyPhrases": ["frase clave 1", "frase clave 2"],
                  "demoTags": ["demo-tag-1"]
                }
                """);

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String extractMessageContent(Map<?, ?> response) {
        if (response == null)
            return "{}";
        List<?> choices = (List<?>) response.get("choices");
        if (choices == null || choices.isEmpty())
            return "{}";
        Map<?, ?> message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
        if (message == null)
            return "{}";
        Object content = message.get("content");
        return content != null ? content.toString() : "{}";
    }

    /**
     * Elimina los marcadores de bloque de código Markdown que los LLMs a veces
     * añaden.
     */
    private String stripMarkdownJson(String text) {
        if (text == null)
            return "{}";
        text = text.strip();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.strip();
    }
}
