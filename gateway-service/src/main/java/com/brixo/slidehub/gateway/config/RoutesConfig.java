package com.brixo.slidehub.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * Configuración de rutas del API Gateway (AGENTS.md §2.4).
 *
 * ORDEN IMPORTANTE: /api/ai/** DEBE ir ANTES que /api/** para que las rutas
 * de IA no sean capturadas por el state-service.
 *
 * Tabla de rutas:
 * /api/ai/** → ai-service:8083
 * /api/** → state-service:8081
 * /auth/** → ui-service:8082
 * /slides, /remote, etc. → ui-service:8082
 * /presentation/** → ui-service:8082
 */
@Configuration
public class RoutesConfig {

    @Value("${slidehub.ai-service.url:http://localhost:8083}")
    private String aiServiceUrl;

    @Value("${slidehub.state-service.url:http://localhost:8081}")
    private String stateServiceUrl;

    @Value("${slidehub.ui-service.url:http://localhost:8082}")
    private String uiServiceUrl;

    @Bean
    public RouterFunction<ServerResponse> gatewayRoutes() {
        return RouterFunctions.route()
                // IA routes — DEBE ir antes que /api/**
                .route(RequestPredicates.path("/api/ai/**"), http(aiServiceUrl))
                // State routes
                .route(RequestPredicates.path("/api/**"), http(stateServiceUrl))
                // Auth routes
                .route(RequestPredicates.path("/auth/**"), http(uiServiceUrl))
                // OAuth2 routes (Fase 1)
                .route(RequestPredicates.path("/login/oauth2/**"), http(uiServiceUrl))
                // UI application routes
                .route(
                        RequestPredicates.path("/slides")
                                .or(RequestPredicates.path("/remote"))
                                .or(RequestPredicates.path("/presenter"))
                                .or(RequestPredicates.path("/main-panel"))
                                .or(RequestPredicates.path("/demo"))
                                .or(RequestPredicates.path("/showcase")),
                        http(uiServiceUrl))
                // Presentation static assets (HU-013)
                .route(RequestPredicates.path("/presentation/**"), http(uiServiceUrl))
                .build();
    }
}
