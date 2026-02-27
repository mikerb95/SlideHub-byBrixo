# AGENTS.md — SlideHub

> **Guía de referencia para agentes de IA** que trabajen sobre este repositorio.
> Leer este archivo **antes de tocar cualquier código**.

---

## 1. Visión del Proyecto

**SlideHub** es un sistema de control de diapositivas en tiempo real, multi-pantalla,
extraído y reescrito completamente en Java desde un módulo PHP/CodeIgniter 4.

El módulo original está documentado en [`docs/Presentation-Module-Analysis.md`](docs/Presentation-Module-Analysis.md).
Úsalo **solo como referencia funcional**: no como guía tecnológica.

Las historias de usuario definitivas están en
[`docs/Historias de Usuario - SlideHub.csv`](<docs/Historias de Usuario - SlideHub.csv>).
Son la **fuente de verdad de requisitos** — tienen prioridad sobre el doc de análisis PHP
si hay diferencias de comportamiento.

### Propuesta de valor

| Feature clave               | Descripción                                                                     |
|-----------------------------|---------------------------------------------------------------------------------|
| Multi-pantalla sincronizada | N pantallas se sincronizan vía REST API + polling HTTP                         |
| Control remoto táctil       | Interfaz para smartphone con swipe y vibración háptica                         |
| Presenter notes con IA      | Notas generadas automáticamente por Groq a partir del contenido del repo       |
| Ingesta de repo con Gemini  | Gemini lee el repositorio de GitHub y extrae contexto por slide                |
| Modo dual slides/URL        | La pantalla `demo` alterna entre diapositivas e iframe de cualquier URL        |
| Panel maestro tablet        | Grid optimizado para tablet 11", thumbnails, navegación, links de demo         |
| Autenticación por roles     | Vistas de control protegidas (PRESENTER, ADMIN); proyección y demo públicas    |
| Registro de dispositivos    | Admin puede listar y buscar dispositivos conectados por token                  |
| Zero-config                 | Detecta slides del directorio `static/slides/` automáticamente                |

---

## 2. Arquitectura de Microservicios

Este proyecto es un **monorepo multi-módulo Maven** con 4 microservicios independientes.

```
SlideHub/                          ← Parent POM (aggregator, sin código)
├── state-service/                 ← Servicio de estado (puerto 8081)
├── ui-service/                    ← Servicio de UI Thymeleaf (puerto 8082)
├── ai-service/                    ← Servicio de IA: Gemini + Groq + MongoDB (puerto 8083)
└── gateway-service/               ← API Gateway + Config Server (puerto 8080)
```

### 2.1 `state-service` (Puerto 8081)

**Responsabilidad única:** gestionar y persistir el estado de presentación activo
y el registro de dispositivos.

| Aspecto        | Decisión                                                          |
|----------------|-------------------------------------------------------------------|
| Store          | Redis (TTL 3600s por defecto)                                     |
| API            | REST JSON, Spring WebMVC                                          |
| Endpoints clave | `GET/POST /api/slide`, `GET/POST /api/demo`, `GET /api/devices` |
| Sin UI         | Solo API, sin Thymeleaf                                           |

**Modelo de estado en Redis:**

```json
// Clave: current_slide
{ "slide": 1 }

// Clave: demo_state
{ "mode": "slides", "slide": 1 }
// o
{ "mode": "url", "url": "/some-path" }
```

**Respuesta de `GET /api/slide` (HU-008):**

```json
{ "slide": 5, "totalSlides": 11 }
```

> El campo `totalSlides` se determina escaneando el directorio de slides en cada respuesta
> (o se cachea brevemente). No se persiste en Redis.

**Registro de dispositivos (HU-014, HU-015):**

| Endpoint                          | Acceso  | Descripción                           |
|-----------------------------------|---------|---------------------------------------|
| `GET /api/devices`                | ADMIN   | Lista todos los dispositivos          |
| `GET /api/devices/token/{token}`  | ADMIN   | Busca un dispositivo por token único  |

Cada dispositivo tiene: `name`, `type`, `token`, `lastIp`, `lastConnection`.

### 2.2 `ui-service` (Puerto 8082)

**Responsabilidad única:** servir las vistas HTML y gestionar autenticación de sesión.

| Aspecto       | Decisión                                                                     |
|---------------|------------------------------------------------------------------------------|
| UI            | Thymeleaf 3 + Spring WebMVC                                                  |
| Seguridad     | Spring Security — sesiones HTTP, BCrypt para passwords                       |
| HTTP client   | `WebClient` (WebFlux) para llamar a `state-service` y `ai-service`          |
| Estáticos     | Archivos de slides en `src/main/resources/static/slides/`                   |
| Sin estado    | No mantiene estado de presentación propio; delega todo a `state-service`    |

**Vistas y acceso:**

| Ruta             | Vista Thymeleaf    | Acceso    | Para                              |
|------------------|--------------------|-----------|-----------------------------------|
| `/slides`        | `slides.html`      | Público   | Proyector/TV fullscreen           |
| `/remote`        | `remote.html`      | Público   | Control remoto smartphone         |
| `/demo`          | `demo.html`        | Público   | Pantalla dual slides/iframe       |
| `/showcase`      | `showcase.html`    | Público   | Landing page                      |
| `/presenter`     | `presenter.html`   | PRESENTER | Laptop del presentador con notas  |
| `/main-panel`    | `main-panel.html`  | PRESENTER | Panel maestro tablet 11"          |
| `/auth/login`    | `login.html`       | Público   | Formulario de login               |
| `/auth/register` | `register.html`    | Público   | Registro de nueva cuenta          |

**Rutas de autenticación (HU-001, HU-002, HU-003):**
- Login con usuario/contraseña → valida con BCrypt → crea sesión → redirige a `/presenter`
- Si sesión ya activa → redirige directo a `/presenter` sin mostrar el formulario
- Logout → invalida sesión → redirige a `/auth/login`
- Roles: `PRESENTER` (acceso a control), `ADMIN` (además del panel de devices)

### 2.3 `ai-service` (Puerto 8083)

**Responsabilidad única:** integración con IA externa (Gemini + Groq) y persistencia de
notas del presentador en MongoDB.

| Aspecto         | Decisión                                                              |
|-----------------|-----------------------------------------------------------------------|
| Store           | MongoDB — colección `presenter_notes`                                 |
| API             | REST JSON, Spring WebMVC                                              |
| IA externa      | Google Gemini (lectura de repos GitHub) + Groq (generación de notas) |
| Sin UI          | Solo API                                                              |

**Flujo de generación de notas (HU-016):**

```
1. Cliente envía POST /api/ai/notes/generate con { presentationId, slideNumber, repoUrl, slideContext }
2. ai-service llama a Gemini API → extrae contenido relevante del repo de GitHub para ese slide
3. ai-service envía ese contenido a Groq API → genera nota estructurada
4. Nota se guarda en MongoDB: { presentationId, slideNumber, title, points[], suggestedTime, keyPhrases[], demoTags[] }
5. Responde { success: true } o { success: false, errorMessage: "..." }
```

**Endpoints:**

| Método   | Ruta                                         | Descripción                          |
|----------|----------------------------------------------|--------------------------------------|
| `POST`   | `/api/ai/notes/generate`                     | Genera nota para un slide vía IA     |
| `GET`    | `/api/ai/notes/{presentationId}`             | Lista todas las notas de una presentación |
| `GET`    | `/api/ai/notes/{presentationId}/{slideNumber}` | Obtiene nota de un slide específico |
| `DELETE` | `/api/ai/notes/{presentationId}`             | Elimina todas las notas de una presentación |
| `GET`    | `/api/ai/notes/health`                       | Health check del servicio de IA      |

> Si la nota ya existe para un slide, `generate` la **sobreescribe** (HU-016 §2).
> `DELETE` responde `204 No Content` aunque no existan notas previas (HU-019 §2).
> `GET /{presentationId}/{slideNumber}` responde `204 No Content` si no existe la nota (HU-018 §2).

### 2.4 `gateway-service` (Puerto 8080)

**Responsabilidad única:** punto de entrada único + Spring Cloud Config Server.

| Aspecto         | Decisión                                          |
|-----------------|---------------------------------------------------|
| Routing         | Spring Cloud Gateway (WebMVC flavor)             |
| Config Server   | Spring Cloud Config (archivos locales en `/config-repo/`) |
| Sin lógica      | Solo enruta y provee configuración               |

**Tabla de rutas:**

| Prefijo                          | Destino                             |
|----------------------------------|-------------------------------------|
| `/api/ai/**`                     | `ai-service:8083/api/ai/**`         |
| `/api/**`                        | `state-service:8081/api/**`         |
| `/auth/**`                       | `ui-service:8082/auth/**`           |
| `/slides`, `/remote`, `/presenter`, `/main-panel`, `/demo`, `/showcase` | `ui-service:8082/**` |
| `/presentation/**`               | `ui-service:8082/presentation/**`   |
| `/actuator/**`                   | Endpoints internos del gateway      |

> **Orden importa:** la ruta `/api/ai/**` debe definirse **antes** de `/api/**` en la configuración
> del gateway para que los requests de IA no sean capturados por el `state-service`.

---

## 3. Stack Tecnológico

| Capa              | Tecnología                                  | Versión / Nota               |
|-------------------|---------------------------------------------|------------------------------|
| Lenguaje          | Java                                        | 21 (LTS)                     |
| Framework         | Spring Boot                                 | 4.0.3                        |
| Cloud             | Spring Cloud                                | 2025.1.0                     |
| UI templating     | Thymeleaf 3                                 | incluido en Boot             |
| HTTP client       | Spring WebClient (WebFlux)                  | incluido en Boot             |
| Cache / State     | Redis via `spring-boot-starter-data-redis`  | incluido en Boot             |
| Base de datos IA  | MongoDB via `spring-boot-starter-mongodb`   | incluido en Boot             |
| Autenticación     | Spring Security + BCrypt                    | `spring-boot-starter-security` |
| Gateway           | Spring Cloud Gateway (WebMVC)               | incluido en Cloud            |
| Config            | Spring Cloud Config Server                  | incluido en Cloud            |
| IA — Ingesta repo | Google Gemini API                           | llamadas HTTP vía WebClient  |
| IA — Generación   | Groq API (LLM)                              | llamadas HTTP vía WebClient  |
| Build             | Maven (Wrapper `mvnw`)                      | —                            |
| Testing           | JUnit 5, Spring Boot Test, MockMvc          | incluido en Boot             |
| CSS (vistas)      | Bootstrap 5.3 + Tailwind CDN, Font Awesome 6.5 | CDN only                  |
| JS (vistas)       | Vanilla ES6 (fetch API, DOM, polling)       | sin bundler                  |

> **Regla:** No añadir Lombok, MapStruct ni ninguna librería de generación de código
> sin discutirlo primero. Preferir código explícito y legible.
>
> Gemini y Groq se integran **únicamente vía HTTP** (`WebClient`) — no se usan SDKs de terceros
> para mantener control total sobre el payload y evitar dependencias pesadas.

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
- `com.brixo.slidehub.state.service.DeviceRegistryService`
- `com.brixo.slidehub.ui.controller.PresentationViewController`
- `com.brixo.slidehub.ui.controller.AuthController`
- `com.brixo.slidehub.ai.controller.NotesController`
- `com.brixo.slidehub.ai.service.GeminiService`
- `com.brixo.slidehub.ai.service.GroqService`
- `com.brixo.slidehub.ai.model.PresenterNote`
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

## 6. Catálogo de Historias de Usuario

Fuente canónica: [`docs/Historias de Usuario - SlideHub.csv`](<docs/Historias de Usuario - SlideHub.csv>)

| ID     | Rol               | Funcionalidad                                         | Servicio destino                 |
|--------|-------------------|-------------------------------------------------------|----------------------------------|
| HU-001 | Presentador       | Login con usuario y contraseña                        | `ui-service` (Spring Security)   |
| HU-002 | Presentador       | Registro de cuenta nueva                              | `ui-service` (Spring Security)   |
| HU-003 | Presentador       | Cierre de sesión                                      | `ui-service` (Spring Security)   |
| HU-004 | Presentador       | Avanzar/retroceder slides desde smartphone (`/remote`) | `state-service` + `ui-service`  |
| HU-005 | Audiencia         | Ver slide activo en proyector (`/slides`, polling)    | `state-service` + `ui-service`   |
| HU-006 | Presentador       | Ver slide actual + notas + preview en `/presenter`    | `ai-service` + `ui-service`      |
| HU-007 | Responsable tec.  | Navegar a cualquier slide desde `/main-panel`         | `state-service` + `ui-service`   |
| HU-008 | Dispositivo       | `GET /api/slide` devuelve `{slide, totalSlides}`      | `state-service`                  |
| HU-009 | Dispositivo       | _(duplicado funcional de HU-008 — no implementar por separado)_ | —               |
| HU-010 | Responsable tec.  | Desactivar modo URL y volver a slides en `/demo`      | `state-service` + `ui-service`   |
| HU-011 | Audiencia         | `/demo` sincroniza modo slides o iframe automáticamente | `state-service` + `ui-service` |
| HU-012 | Visitante         | Ver landing page pública en `/showcase`               | `ui-service`                     |
| HU-013 | Vista HTML        | Cargar imágenes `GET /presentation/Slide*.PNG`        | `ui-service` (recurso estático)  |
| HU-014 | Admin             | `GET /api/devices` — listar todos los dispositivos    | `state-service`                  |
| HU-015 | Admin             | `GET /api/devices/token/{token}` — buscar por token   | `state-service`                  |
| HU-016 | Presentador       | `POST /api/ai/notes/generate` — generar nota con IA   | `ai-service`                     |
| HU-017 | Presentador       | `GET /api/ai/notes/{presentationId}` — listar notas   | `ai-service`                     |
| HU-018 | Vista presentador | `GET /api/ai/notes/{presentationId}/{slideNumber}`    | `ai-service`                     |
| HU-019 | Presentador       | `DELETE /api/ai/notes/{presentationId}` — borrar notas | `ai-service`                    |
| HU-020 | Ops               | `GET /api/ai/notes/health` + `GET /actuator/health`   | `ai-service` + todos            |

**Comportamientos críticos a respetar:**
- `GET /api/slide` siempre responde `{ "slide": N, "totalSlides": M }` — nunca solo `{ "slide": N }` (HU-008)
- Si no se ha navegado ningún slide, slide por defecto = 1 (HU-008 §2)
- Login incorrecto → error genérico sin indicar qué campo falló (HU-001 §2)
- Sesión activa + acceso a `/auth/login` → redirect directo a `/presenter` (HU-001 §3)
- Nota de slide inexistente → `204 No Content`, no `404` (HU-018 §2)
- `DELETE` de presentación sin notas → `204 No Content`, no error (HU-019 §2)
- Slide en limite superior/inferior → no avanza/retrocede, sin mostrar error (HU-004 §3 y §4)

---

## 7. Fuente de Verdad Funcional

Antes de implementar cualquier endpoint o vista, consultar el CSV de historias y
[`docs/Presentation-Module-Analysis.md`](docs/Presentation-Module-Analysis.md).

Secciones clave del doc de análisis por tarea:

| Si vas a implementar…         | Leer sección del doc     |
|-------------------------------|--------------------------|
| API de estado (slide/demo)    | §2 Endpoints, §8 API REST |
| Vistas HTML                   | §7 Detalle de Componentes |
| Notas del presentador         | §7.5, §9.2               |
| Links del main-panel          | §7.6, §9.2               |
| Lógica de polling             | §3 Arquitectura          |
| Config JSON (notas, links)    | §9.2 Configuración Propuesta |

---

## 8. Flujo de Trabajo para el Agente

1. **Leer AGENTS.md** (este archivo) y `CLAUDE.md` antes de cualquier cambio.
2. **Verificar la historia de usuario** relevante en `§6` y el doc de análisis.
3. **Seguir la estructura de paquetes** definida en `§4`.
4. **No crear dependencias entre servicios desde el código** — todo el cross-service
   communication va por HTTP (`WebClient`).
5. **Correr el check de compilación** antes de reportar la tarea como completada:
   ```bash
   ./mvnw clean compile -pl <module> -am
   ```
6. **Reportar qué archivos se crearon/modificados** al finalizar cada tarea.

---

## 9. Decisiones Abiertas (Pendientes)

> Estas decisiones NO están tomadas todavía. No implementar hasta resolverlas.

| # | Decisión                                   | Opciones                              |
|---|---------------------------------------------|---------------------------------------|
| 1 | Persistence backend para `state-service`    | Redis puro vs Redis + JPA (PostgreSQL) |
| 2 | Gestión de assets (slides PNG)              | Directorio local vs S3-compatible     |
| 3 | Módulo `showcase` vs ruta de `ui-service`  | ¿Servicio propio o endpoint de UI?    |
| 4 | Multi-sesión (varias presentaciones)        | Fuera de alcance v1 — no implementar |

**Decisiones ya tomadas (no reabrir):**

| Decisión                        | Resolución                                                 |
|---------------------------------|------------------------------------------------------------|
| Autenticación                   | Spring Security + BCrypt + sesiones HTTP en `ui-service`   |
| Roles                           | `PRESENTER` (control) y `ADMIN` (devices + control)        |
| Store de notas IA               | MongoDB en `ai-service`                                    |
| Integración Gemini y Groq       | HTTP puro vía `WebClient` — sin SDKs de terceros           |

---

## 10. Comandos Útiles

```bash
# Compilar todo el proyecto
./mvnw clean compile

# Compilar un módulo específico
./mvnw clean compile -pl state-service -am

# Ejecutar tests de un módulo
./mvnw test -pl ai-service

# Correr state-service localmente
./mvnw spring-boot:run -pl state-service

# Correr todos los servicios (cuando existan)
./mvnw spring-boot:run -pl gateway-service &
./mvnw spring-boot:run -pl state-service &
./mvnw spring-boot:run -pl ui-service &
./mvnw spring-boot:run -pl ai-service &
```

---

## 11. Lo que NO hacer

- ❌ No añadir lógica de negocio al `gateway-service`
- ❌ No hacer que `ui-service` acceda a Redis directamente
- ❌ No usar SDKs oficiales de Gemini o Groq — integrar solo vía HTTP con `WebClient`
- ❌ No hardcodear API keys de Gemini/Groq — siempre leer de variables de entorno
- ❌ No hardcodear URLs de otros servicios — usar Config Server o variables de entorno
- ❌ No crear un servicio de BD/JPA si Redis es suficiente para el estado
- ❌ No mezclar `@Controller` (MVC) y `@RestController` en el mismo servicio de manera inconsistente
- ❌ No implementar WebSockets todavía — el polling es suficiente para v1
- ❌ No tocar el `pom.xml` raíz sin entender que es el parent de un multi-módulo Maven
- ❌ No almacenar notas del presentador en Redis — pertenecen a MongoDB en `ai-service`
- ❌ No implementar multi-sesión (varias presentaciones en paralelo) — fuera de alcance v1

---

*Actualizado: Febrero 2026 — v1 en elaboración*
