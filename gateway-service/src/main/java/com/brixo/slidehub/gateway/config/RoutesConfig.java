package com.brixo.slidehub.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.FilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;

/**
 * Configuración de rutas del API Gateway (AGENTS.md §2.4).
 *
 * ORDEN IMPORTANTE: /api/ai/** (Order=1) DEBE ir ANTES que /api/** (Order=2).
 * Spring compone múltiples beans RouterFunction en orden por @Order.
 *
 * Tabla de rutas:
 * /api/ai/** → ai-service:8083 (Order 1)
 * /api/** → state-service:8081 (Order 2)
 * /auth/**,
 * /slides, /remote,
 * /presenter, etc. → ui-service:8082 (Order 3)
 * /presentation/** → ui-service:8082 (Order 4)
 */
@Configuration
public class RoutesConfig {

    @Value("${slidehub.ai-service.url:http://localhost:8083}")
    private String aiServiceUrl;

    @Value("${slidehub.state-service.url:http://localhost:8081}")
    private String stateServiceUrl;

    @Value("${slidehub.ui-service.url:http://localhost:8082}")
    private String uiServiceUrl;

    /** IA routes — DEBE evaluarse antes que /api/** (Order=1) */
    @Bean
    @Order(1)
    public RouterFunction<ServerResponse> aiRoutes() {
        return route("ai-service-routes")
                .route(RequestPredicates.path("/api/ai/**"), http())
                .filter(uri(aiServiceUrl))
                .build();
    }

    /** State routes (Order=2) */
    @Bean
    @Order(2)
    public RouterFunction<ServerResponse> stateRoutes() {
        return route("state-service-routes")
                .route(RequestPredicates.path("/api/**"), http())
                .filter(uri(stateServiceUrl))
                .build();
    }

    /** UI application routes + auth + OAuth2 (Order=3) */
    @Bean
    @Order(3)
    public RouterFunction<ServerResponse> uiRoutes() {
        return route("ui-service-routes")
                .route(
                        RequestPredicates.path("/auth/**")
                                .or(RequestPredicates.path("/oauth2/**")) // /oauth2/authorization/{provider}
                                .or(RequestPredicates.path("/login/oauth2/**")) // /login/oauth2/code/{provider}
                                .or(RequestPredicates.path("/slides"))
                                .or(RequestPredicates.path("/remote"))
                                .or(RequestPredicates.path("/presenter"))
                                .or(RequestPredicates.path("/main-panel"))
                                .or(RequestPredicates.path("/demo"))
                                .or(RequestPredicates.path("/showcase"))
                                .or(RequestPredicates.path("/presentations/**")),  // Fase 2
                        http())
                .filter(uri(uiServiceUrl))
                .build();
    }

    /** Presentation static assets (HU-013, Order=4) */
    @Bean
    @Order(4)
    public RouterFunction<ServerResponse> presentationRoutes() {
        return route("presentation-routes")
                .route(RequestPredicates.path("/presentation/**"), http())
                .filter(uri(uiServiceUrl))
                .build();
    }
}
