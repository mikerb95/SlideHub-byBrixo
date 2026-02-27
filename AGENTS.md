# AGENTS.md — SlideHub

> **Guía de referencia para agentes de IA** que trabajen sobre este repositorio.
> Leer este archivo **antes de tocar cualquier código**.

---

## 1. Visión del Proyecto

**SlideHub** es un sistema de control de diapositivas en tiempo real, multi-pantalla,
extraído y reescrito completamente en Java desde un módulo PHP/CodeIgniter 4.

El módulo original está documentado en [`docs/Presentation-Module-Analysis.md`](docs/Presentation-Module-Analysis.md).
Úsalo **solo como referencia funcional**: no como guía tecnológica.

### Propuesta de valor

| Feature clave              | Descripción                                                          |
|----------------------------|----------------------------------------------------------------------|
| Multi-pantalla sincronizada | N pantallas se sincronizan vía REST API + polling HTTP              |
| Control remoto táctil       | Interfaz para smartphone con swipe y vibración háptica             |
| Presenter notes              | Notas por slide configurable vía JSON externo                      |
| Modo dual slides/URL         | La pantalla `demo` alterna entre diapositivas e iframe de cualquier URL |
| Panel maestro tablet         | Grid optimizado para tablet 11", thumbnails, navegación, links de demo |
| Zero-config                  | Detecta slides del directorio `assets/slides/` automáticamente     |

---

## 2. Arquitectura de Microservicios

Este proyecto es un **monorepo multi-módulo Maven** con 3 microservicios independientes.

```
SlideHub/                          ← Parent POM (aggregator, sin código)
├── state-service/                 ← Servicio de estado (puerto 8081)
├── ui-service/                    ← Servicio de UI Thymeleaf (puerto 8082)
└── gateway-service/               ← API Gateway + Config Server (puerto 8080)
```

### 2.1 `state-service` (Puerto 8081)

**Responsabilidad única:** gestionar y persistir el estado de presentación activo.

| Aspecto        | Decisión                                  |
|----------------|-------------------------------------------|
| Store          | Redis (TTL 3600s por defecto)             |
| API            | REST JSON, Spring WebMVC                  |
| Endpoints      | `GET/POST /api/slide`, `GET/POST /api/demo` |
| Sin UI         | Solo API, sin Thymeleaf                   |

**Modelo de estado:**

```json
// current_slide key
{ "slide": 1 }

// demo_state key
{ "mode": "slides", "slide": 1 }
// o
{ "mode": "url", "url": "/some-path" }
```

### 2.2 `ui-service` (Puerto 8082)

**Responsabilidad única:** servir las vistas HTML a cada tipo de pantalla.

| Aspecto     | Decisión                                               |
|-------------|--------------------------------------------------------|
| UI          | Thymeleaf 3 + Spring WebMVC                           |
| HTTP client | `WebClient` (WebFlux) para llamar a `state-service`   |
| Estáticos   | Archivos de slides en `src/main/resources/static/slides/` |
| Sin estado  | No mantiene estado propio; delega todo a `state-service` |

**Vistas:**

| Ruta          | Vista Thymeleaf         | Para                              |
|---------------|-------------------------|-----------------------------------|
| `/slides`     | `slides.html`           | Proyector/TV fullscreen           |
| `/remote`     | `remote.html`           | Control remoto smartphone         |
| `/presenter`  | `presenter.html`        | Laptop del presentador            |
| `/main-panel` | `main-panel.html`       | Panel maestro tablet 11"          |
| `/demo`       | `demo.html`             | Pantalla dual slides/iframe       |
| `/showcase`   | `showcase.html`         | Landing page                      |

### 2.3 `gateway-service` (Puerto 8080)

**Responsabilidad única:** punto de entrada único + Spring Cloud Config Server.

| Aspecto         | Decisión                                          |
|-----------------|---------------------------------------------------|
| Routing         | Spring Cloud Gateway (WebMVC flavor)             |
| Config Server   | Spring Cloud Config (archivos locales en `/config-repo/`) |
| Sin lógica      | Solo enruta y provee configuración               |

**Tablas de rutas:**

| Prefijo           | Destino                        |
|-------------------|--------------------------------|
| `/api/**`         | `state-service:8081/api/**`    |
| `/slides`, `/remote`, `/presenter`, `/main-panel`, `/demo`, `/showcase` | `ui-service:8082/**` |
| `/actuator/**`    | Endpoints internos del gateway |

---

## 3. Stack Tecnológico

| Capa              | Tecnología                              | Versión            |
|-------------------|-----------------------------------------|--------------------|
| Lenguaje          | Java                                    | 21 (LTS)           |
| Framework         | Spring Boot                             | 4.0.3              |
| Cloud             | Spring Cloud                            | 2025.1.0           |
| UI templating     | Thymeleaf 3                             | incluido en Boot   |
| HTTP client       | Spring WebClient (WebFlux)              | incluido en Boot   |
| Cache / State     | Redis via `spring-boot-starter-data-redis` | incluido en Boot |
| Gateway           | Spring Cloud Gateway (WebMVC)           | incluido en Cloud  |
| Config            | Spring Cloud Config Server              | incluido en Cloud  |
| Build             | Maven (Wrapper `mvnw`)                  | —                  |
| Testing           | JUnit 5, Spring Boot Test, MockMvc      | incluido en Boot   |
| CSS (vistas)      | Bootstrap 5.3 + Tailwind CDN, Font Awesome 6.5 | CDN only |
| JS (vistas)       | Vanilla ES6 (fetch API, DOM, polling)   | sin bundler        |

> **Regla:** No añadir Lombok, MapStruct ni ninguna librería de generación de código
> sin discutirlo primero. Preferir código explícito y legible.

---

## 4. Estructura de Paquetes Java

Convención de paquetes por módulo:

```
com.brixo.slidehub.<service>
├── controller/     ← @RestController o @Controller
├── service/        ← @Service (lógica de negocio)
├── model/          ← Records o POJOs (estado, DTOs)
├── config/         ← @Configuration, beans de infraestructura
└── exception/      ← Excepciones de dominio + @ControllerAdvice
```

Ejemplos concretos:
- `com.brixo.slidehub.state.controller.SlideController`
- `com.brixo.slidehub.state.service.SlideStateService`
- `com.brixo.slidehub.ui.controller.PresentationViewController`
- `com.brixo.slidehub.gateway.config.RoutesConfig`

---

## 5. Convenciones de Código

### 5.1 Java

- **Records** para DTOs/modelos inmutables: `record SlideState(int slide) {}`
- **`@RestController`** en `state-service` (solo JSON)
- **`@Controller`** en `ui-service` (devuelve nombres de vistas Thymeleaf)
- **Programmatic configuration** preferida sobre anotaciones mágicas cuando la intención no es obvia
- **`ResponseEntity<T>`** para todos los endpoints de `state-service`
- Métodos de servicio **síncronos** en `state-service`; `WebClient` con `.block()` en `ui-service`
  (o reactive chain si se evalúa necesario — decidir en contexto)
- **No** `@Autowired` en campos; inyección **únicamente por constructor**

### 5.2 Thymeleaf / HTML

- Variables del modelo con `th:text`, `th:each`, `th:if` — sin lógica compleja en templates
- Datos de configuración (notas, links) inyectados desde el modelo del controller, no hardcodeados en HTML
- El polling JavaScript usa `fetch()` nativo — sin jQuery ni Axios

### 5.3 Configuración

- `application.properties` por perfil: `application-dev.properties`, `application-prod.properties`
- Secrets y URLs de servicio en variables de entorno — jamás hardcodeadas en código
- El Config Server centraliza la configuración compartida entre servicios

### 5.4 Tests

- Cada servicio tiene tests en `src/test/java/...`
- Preferir `@WebMvcTest` para controllers, `@DataRedisTest` para repositorios Redis
- Mocks con `@MockitoBean` (Spring Boot 4+)
- Nombre de test: `methodName_scenario_expectedResult()`

---

## 6. Fuente de Verdad Funcional

Antes de implementar cualquier endpoint o vista, consultar
[`docs/Presentation-Module-Analysis.md`](docs/Presentation-Module-Analysis.md).

Secciones clave a revisar por tarea:

| Si vas a implementar…         | Leer sección del doc     |
|-------------------------------|--------------------------|
| API de estado (slide/demo)    | §2 Endpoints, §8 API REST |
| Vistas HTML                   | §7 Detalle de Componentes |
| Notas del presentador         | §7.5, §9.2               |
| Links del main-panel          | §7.6, §9.2               |
| Lógica de polling             | §3 Arquitectura          |
| Config JSON (notas, links)    | §9.2 Configuración Propuesta |

---

## 7. Flujo de Trabajo para el Agente

1. **Leer AGENTS.md** (este archivo) y `CLAUDE.md` antes de cualquier cambio.
2. **Verificar la sección del doc de análisis** relevante para la tarea.
3. **Seguir la estructura de paquetes** definida en §4.
4. **No crear dependencias entre servicios desde el código** — todo el cross-service
   communication va por HTTP (`WebClient`).
5. **Correr el check de compilación** antes de reportar la tarea como completada:
   ```bash
   ./mvnw clean compile -pl <module> -am
   ```
6. **Reportar qué archivos se crearon/modificaron** al finalizar cada tarea.

---

## 8. Decisiones Abiertas (Pendientes)

> Estas decisiones NO están tomadas todavía. No implementar hasta resolverlas.

| # | Decisión                                   | Opciones                              |
|---|---------------------------------------------|---------------------------------------|
| 1 | Persistence backend para `state-service`    | Redis puro vs Redis + JPA (PostgreSQL) |
| 2 | Gestión de assets (slides PNG)              | Directorio local vs S3-compatible     |
| 3 | Módulo `showcase` vs ruta de `ui-service`  | ¿Servicio propio o endpoint de UI?    |
| 4 | Autenticación en rutas de control           | Opcional, Spring Security en gateway  |
| 5 | Multi-sesión (varias presentaciones)        | Fuera de alcance v1 — no implementar |

---

## 9. Comandos Útiles

```bash
# Compilar todo el proyecto
./mvnw clean compile

# Compilar un módulo específico
./mvnw clean compile -pl state-service -am

# Ejecutar tests de un módulo
./mvnw test -pl state-service

# Correr state-service localmente
./mvnw spring-boot:run -pl state-service

# Correr todos los servicios (cuando existan)
./mvnw spring-boot:run -pl gateway-service &
./mvnw spring-boot:run -pl state-service &
./mvnw spring-boot:run -pl ui-service &
```

---

## 10. Lo que NO hacer

- ❌ No añadir lógica de negocio al `gateway-service`
- ❌ No hacer que `ui-service` acceda a Redis directamente
- ❌ No hardcodear URLs de otros servicios — usar Config Server o variables de entorno
- ❌ No crear un servicio de BD/JPA si Redis es suficiente para el estado
- ❌ No mezclar `@Controller` (MVC) y `@RestController` en el mismo servicio de manera inconsistente
- ❌ No implementar WebSockets todavía — el polling es suficiente para v1
- ❌ No tocar el `pom.xml` raíz sin entender que es el parent de un multi-módulo Maven

---

*Actualizado: Febrero 2026 — v1 en elaboración*
