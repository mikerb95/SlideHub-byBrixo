# CLAUDE.md — SlideHub

> **Instrucciones específicas para Claude** al trabajar en este repositorio.
> Complementa `AGENTS.md` — ambos deben leerse juntos.

---

## 1. Contexto del Proyecto

SlideHub es un sistema de presentación de diapositivas multi-pantalla escrito en Java.
Es la reescritura de un módulo PHP/CodeIgniter 4 documentado en
[`docs/Presentation-Module-Analysis.md`](docs/Presentation-Module-Analysis.md).

**Stack actual:**
- Spring Boot 4.0.3 / Spring Cloud 2025.1.0 / Java 21
- Maven multi-módulo (estructura a construir — el `pom.xml` raíz existe pero los submódulos aún no)
- Redis (estado en memoria), MongoDB (notas IA), Thymeleaf (UI), Spring Security (auth), Spring Cloud Gateway (enrutamiento)
- 4 microservicios: `state-service` (8081), `ui-service` (8082), `ai-service` (8083), `gateway-service` (8080)

---

## 2. Cómo Razonar antes de Codificar

Antes de producir cualquier código, recorre este checklist internamente:

1. ¿Esta tarea pertenece a `state-service`, `ui-service`, `ai-service` o `gateway-service`?
2. ¿Qué historia de usuario cubre esto? Verificar `AGENTS.md §6` y los criterios de aceptación en el CSV.
3. ¿El comportamiento detallado está en `docs/Presentation-Module-Analysis.md`? Si sí, cítalo.
4. ¿Qué paquete Java corresponde según la convención de `AGENTS.md §4`?
5. ¿Requiere una nueva dependencia en `pom.xml`? Si sí, ¿ya está declarada en el parent?
6. ¿El cambio contradice una decisión ya tomada de `AGENTS.md §9`? Si es así, no proceder.

---

## 3. Formato de Respuesta

### Al implementar código

- Mostrar cada archivo nuevo/modificado con su path completo desde la raíz del repo.
- Si el archivo es **nuevo**, indicarlo explícitamente.
- Si creas un bloque de código, siempre especificar el lenguaje (` ```java`, ` ```xml`, ` ```html`, etc.).
- Seguir la estructura:
  1. Breve justificación de las decisiones tomadas (2-5 líneas máximo).
  2. Los archivos en orden lógico (config → model → service → controller → test).
  3. Lista de archivos creados/modificados al final.

### Al investigar o responder preguntas

- Citar la sección exacta del doc de análisis cuando aplique.
- Respuestas concisas — sin relleno introductorio.

---

## 4. Guías de Código Java

### 4.1 Records para modelos

```java
// CORRECTO — record inmutable
public record SlideState(int slide) {}

public record DemoState(String mode, Integer slide, String url) {
    public static DemoState defaultSlides() {
        return new DemoState("slides", 1, null);
    }
}

// INCORRECTO — POJO mutable innecesario para DTOs simples
public class SlideState {
    private int slide;
    public void setSlide(int s) { this.slide = s; }
}
```

### 4.2 Inyección por constructor

```java
// CORRECTO
@Service
public class SlideStateService {
    private final RedisTemplate<String, String> redis;

    public SlideStateService(RedisTemplate<String, String> redis) {
        this.redis = redis;
    }
}

// INCORRECTO
@Service
public class SlideStateService {
    @Autowired
    private RedisTemplate<String, String> redis; // ← nunca
}
```

### 4.3 ResponseEntity en state-service

```java
// CORRECTO
@GetMapping("/api/slide")
public ResponseEntity<SlideState> getSlide() {
    return ResponseEntity.ok(stateService.getCurrentSlide());
}

// INCORRECTO — no retornar el objeto directamente sin ResponseEntity
@GetMapping("/api/slide")
public SlideState getSlide() { ... }
```

### 4.4 WebClient en ui-service

```java
// Definir el bean una sola vez en @Configuration
@Bean
public WebClient stateServiceClient(@Value("${slidehub.state-service.url}") String url) {
    return WebClient.builder().baseUrl(url).build();
}

// Usar en el servicio
public SlideState fetchCurrentSlide() {
    return stateServiceClient
        .get()
        .uri("/api/slide")
        .retrieve()
        .bodyToMono(SlideState.class)
        .block(); // síncrono intencionalmente — MVC thread model
}
```

### 4.5 Manejo de errores

```java
// Excepción de dominio específica
public class SlideRangeException extends RuntimeException {
    public SlideRangeException(int slide, int max) {
        super("Slide %d fuera de rango [1, %d]".formatted(slide, max));
    }
}

// Handler global en @ControllerAdvice
@ExceptionHandler(SlideRangeException.class)
public ResponseEntity<Map<String, String>> handleSlideRange(SlideRangeException ex) {
    return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
}
```

---

## 5. Guías para Templates Thymeleaf

- El controller inyecta **todos** los datos que la vista necesita. La vista no hace cálculos.
- Usar `th:object` + `th:field` solo en formularios. Para datos de solo lectura, `th:text` y `th:each` directos.
- Los fragmentos reutilizables van en `src/main/resources/templates/fragments/`.
- El polling JavaScript llama a `/api/slide` y `/api/demo` — estos son endpoints del gateway (puerto 8080), no del state-service directamente.

Ejemplo mínimo de una vista:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title th:text="${pageTitle}">SlideHub</title>
</head>
<body>
    <div id="slide-container">
        <img th:each="i : ${#numbers.sequence(1, totalSlides)}"
             th:src="@{/slides/__${i}__.PNG}"
             th:id="|slide-${i}|"
             class="slide"
             alt="">
    </div>
    <script th:inline="javascript">
        const totalSlides = /*[[${totalSlides}]]*/ 11;
        const pollInterval = /*[[${pollIntervalMs}]]*/ 1000;
    </script>
    <script src="/js/slides-poll.js"></script>
</body>
</html>
```

---

## 6. Guías para `pom.xml`

### Parent POM (raíz)

El `pom.xml` raíz debe tener `<packaging>pom</packaging>` y listar los módulos.
**No debe tener dependencias en `<dependencies>`** — solo `<dependencyManagement>`.

```xml
<modules>
    <module>state-service</module>
    <module>ui-service</module>
    <module>ai-service</module>
    <module>gateway-service</module>
</modules>
```

### POMs de submódulos

Cada submódulo hereda del parent y declara **solo las dependencias que necesita**.
No copiar todas las dependencias del parent a cada hijo.

| Servicio          | Dependencias clave                                                                      |
|-------------------|-----------------------------------------------------------------------------------------|
| `state-service`   | `web`, `data-redis`, `actuator`                                                         |
| `ui-service`      | `web`, `thymeleaf`, `thymeleaf-extras-springsecurity6`, `security`, `webflux`, `actuator` |
| `ai-service`      | `web`, `data-mongodb`, `webflux` (solo WebClient), `actuator`                           |
| `gateway-service` | `spring-cloud-gateway-server-webmvc`, `config-server`, `actuator`                       |

---

## 7. Configuración por Servicio

Cada servicio tiene su propio `application.properties`:

```properties
# state-service
spring.application.name=state-service
server.port=8081
spring.data.redis.host=${REDIS_HOST:localhost}
spring.data.redis.port=${REDIS_PORT:6379}
slidehub.slides.total-scan-enabled=true

# ui-service
spring.application.name=ui-service
server.port=8082
slidehub.state-service.url=${STATE_SERVICE_URL:http://localhost:8081}
slidehub.ai-service.url=${AI_SERVICE_URL:http://localhost:8083}
slidehub.poll.slides.interval-ms=1000
slidehub.poll.presenter.interval-ms=1500
slidehub.poll.demo.interval-ms=800

# ai-service
spring.application.name=ai-service
server.port=8083
spring.data.mongodb.uri=${MONGODB_URI:mongodb://localhost:27017/slidehub}
slidehub.ai.gemini.api-key=${GEMINI_API_KEY}
slidehub.ai.gemini.base-url=https://generativelanguage.googleapis.com
slidehub.ai.groq.api-key=${GROQ_API_KEY}
slidehub.ai.groq.base-url=https://api.groq.com
slidehub.ai.groq.model=${GROQ_MODEL:llama3-8b-8192}

# gateway-service
spring.application.name=gateway-service
server.port=8080
spring.cloud.config.server.native.search-locations=classpath:/config-repo
```

---

## 8. Cómo Abordar Tareas Comunes

### "Implementa el endpoint `GET /api/slide`"

1. Ubicar en `state-service/src/main/java/com/brixo/slidehub/state/`
2. Crear: `model/SlideStateResponse.java` (record con `slide` y `totalSlides`), `service/SlideStateService.java`, `controller/SlideController.java`
3. `SlideStateService` lee `current_slide` de Redis; cuenta archivos del directorio slides para `totalSlides`.
4. Comportamiento por defecto: si la clave no existe → `{ "slide": 1, "totalSlides": N }` (HU-008 §2)
5. Referencia: `docs/Presentation-Module-Analysis.md §8.1`, HU-008

### "Implementa la vista `/slides`"

1. Ubicar en `ui-service`
2. Controller `PresentationViewController` llama a `SlideUiService.fetchSlideState()` → devuelve `{ slide, totalSlides }`
3. Template `src/main/resources/templates/slides.html`
4. JS inline: polling cada `${pollIntervalMs}` ms a `/api/slide` (pasa por el gateway en 8080)
5. Referencia: `docs/Presentation-Module-Analysis.md §7.3`, HU-005

### "Implementa el login (`/auth/login`)"

1. Ubicar en `ui-service`
2. `AuthController` maneja `GET /auth/login` (formulario) y delega a Spring Security el `POST`
3. Si sesión activa al entrar → redirect a `/presenter` (HU-001 §3)
4. En error → mismo formulario con mensaje genérico, sin indicar campo fallido (HU-001 §2)
5. Referencia: HU-001, HU-002, HU-003

### "Implementa generación de notas IA (`POST /api/ai/notes/generate`)"

1. Ubicar en `ai-service/src/main/java/com/brixo/slidehub/ai/`
2. `NotesController` recibe `GenerateNoteRequest(presentationId, slideNumber, repoUrl, slideContext)`
3. `GeminiService.extractRepoContext(repoUrl, slideContext)` → llama a Gemini API vía `WebClient`
4. `GroqService.generateNote(geminiContext)` → llama a Groq API vía `WebClient`
5. `NotesService.save(presenterNote)` → guarda en MongoDB
6. Si nota ya existe para `presentationId + slideNumber` → sobreescribir (HU-016 §2)
7. Referencia: HU-016

### "Configura el API Gateway"

1. Ubicar en `gateway-service`
2. Definir rutas en `RoutesConfig.java` con `@Bean RouteLocator`
3. **Orden obligatorio:** `/api/ai/**` → `ai-service:8083` ANTES de `/api/**` → `state-service:8081`
4. Rutas de UI → `ui-service:8082`
5. Referencia: `AGENTS.md §2.4`

---

## 9. Integración con IA (Gemini + Groq)

Ambas IAs se consumen **únicamente vía HTTP** con `WebClient`. Sin SDKs de terceros.

### 9.1 GeminiService — Lectura de repositorio GitHub

Gemini recibe la URL del repo y el contexto del slide, y devuelve contenido relevante.

```java
@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private final WebClient geminiClient;

    @Value("${slidehub.ai.gemini.api-key}")
    private String apiKey;

    public GeminiService(@Value("${slidehub.ai.gemini.base-url}") String baseUrl) {
        this.geminiClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public String extractRepoContext(String repoUrl, String slideContext) {
        String prompt = """
            Analiza el repositorio en %s y extrae el contenido más relevante
            para un slide con el siguiente contexto: %s
            Devuelve solo los puntos clave en formato estructurado.
            """.formatted(repoUrl, slideContext);

        var requestBody = Map.of(
            "contents", List.of(Map.of(
                "parts", List.of(Map.of("text", prompt))
            ))
        );

        return geminiClient.post()
            .uri("/v1beta/models/gemini-pro:generateContent?key={key}", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(json -> json
                .path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText())
            .block();
    }
}
```

### 9.2 GroqService — Generación de notas estructuradas

Groq recibe el contexto extraído por Gemini y genera la nota del presentador.

```java
@Service
public class GroqService {

    private static final Logger log = LoggerFactory.getLogger(GroqService.class);
    private final WebClient groqClient;

    @Value("${slidehub.ai.groq.api-key}")
    private String apiKey;

    @Value("${slidehub.ai.groq.model}")
    private String model;

    public GroqService(@Value("${slidehub.ai.groq.base-url}") String baseUrl) {
        this.groqClient = WebClient.builder().baseUrl(baseUrl).build();
    }

    public NoteContent generateNote(String repoContext, int slideNumber) {
        String prompt = """
            Basándote en este contenido de repositorio:
            %s
            
            Genera notas estructuradas para el slide %d en JSON con esta forma exacta:
            {
              "title": "Título corto del slide",
              "points": ["punto 1", "punto 2", "punto 3"],
              "suggestedTime": "~2 min",
              "keyPhrases": ["frase clave 1", "frase clave 2"],
              "demoTags": ["demo-tag-1"]
            }
            Responde SOLO el JSON, sin texto adicional.
            """.formatted(repoContext, slideNumber);

        var requestBody = Map.of(
            "model", model,
            "messages", List.of(Map.of("role", "user", "content", prompt)),
            "temperature", 0.7
        );

        String rawJson = groqClient.post()
            .uri("/openai/v1/chat/completions")
            .header("Authorization", "Bearer " + apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(json -> json
                .path("choices").get(0)
                .path("message").path("content").asText())
            .block();

        // Parsear el JSON devuelto por Groq
        return objectMapper.readValue(rawJson, NoteContent.class);
    }
}
```

### 9.3 Manejo de errores en integraciones IA

```java
// En NotesController — respuesta ante fallo de IA (HU-016 §3)
try {
    notesService.generate(request);
    return ResponseEntity.ok(Map.of("success", true));
} catch (AiServiceException ex) {
    log.error("Error generando nota con IA: {}", ex.getMessage());
    return ResponseEntity.ok(Map.of("success", false, "errorMessage", ex.getMessage()));
}
```

---

## 10. MongoDB — Notas del Presentador

El documento `PresenterNote` vive en la colección `presenter_notes` de MongoDB.

### 10.1 Modelo

```java
// ENTIDAD — usa @Document, no record (necesita @Id y serialización por Jackson/MongoDB)
@Document(collection = "presenter_notes")
public class PresenterNote {

    @Id
    private String id;

    private String presentationId;
    private int slideNumber;
    private String title;
    private List<String> points;
    private String suggestedTime;
    private List<String> keyPhrases;
    private List<String> demoTags;

    // constructor con todos los campos + getters — sin setters innecesarios
}
```

### 10.2 Repositorio

```java
public interface PresenterNoteRepository extends MongoRepository<PresenterNote, String> {

    Optional<PresenterNote> findByPresentationIdAndSlideNumber(String presentationId, int slideNumber);

    List<PresenterNote> findByPresentationIdOrderBySlideNumberAsc(String presentationId);

    void deleteByPresentationId(String presentationId);
}
```

### 10.3 Lógica de sobreescritura (HU-016 §2) 

```java
public void saveOrUpdate(PresenterNote note) {
    repository.findByPresentationIdAndSlideNumber(note.getPresentationId(), note.getSlideNumber())
        .ifPresent(existing -> note.setId(existing.getId())); // preservar el _id para hacer upsert
    repository.save(note);
}
```

### 10.4 Endpoint `GET /{presentationId}/{slideNumber}` — 204 si no existe

```java
@GetMapping("/{presentationId}/{slideNumber}")
public ResponseEntity<PresenterNote> getNote(@PathVariable String presentationId,
                                              @PathVariable int slideNumber) {
    return notesService.findNote(presentationId, slideNumber)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.noContent().build()); // 204 No Content (HU-018 §2)
}
```

---

## 11. Autenticación (Spring Security en `ui-service`)

### 11.1 Reglas de acceso

| Ruta                          | Acceso requerido  |
|-------------------------------|-------------------|
| `/slides`, `/remote`, `/demo`, `/showcase` | Público (sin auth) |
| `/auth/login`, `/auth/register` | Público          |
| `/presenter`, `/main-panel`   | `PRESENTER` o `ADMIN` |
| `/api/devices/**`             | `ADMIN`           |
| `/api/**` (slide, demo)       | Público (dispositivos cliente) |

### 11.2 SecurityConfig en `ui-service`

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/slides", "/remote", "/demo", "/showcase").permitAll()
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/presentation/**").permitAll()
                .requestMatchers("/api/**").permitAll()    // polling de dispositivos
                .requestMatchers("/presenter", "/main-panel").hasAnyRole("PRESENTER", "ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/auth/login")
                .loginProcessingUrl("/auth/login")
                .defaultSuccessUrl("/presenter", true)
                .failureUrl("/auth/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessUrl("/auth/login?logout=true")
                .invalidateHttpSession(true)
            );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

### 11.3 Redirect si sesión activa (HU-001 §3)

```java
// En AuthController — evitar mostrar login si ya está autenticado
@GetMapping("/auth/login")
public String loginPage(Authentication authentication) {
    if (authentication != null && authentication.isAuthenticated()) {
        return "redirect:/presenter";
    }
    return "login";
}
```

---

## 12. Testing

```java
// Test de controller con MockMvc (state-service)
@WebMvcTest(SlideController.class)
class SlideControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SlideStateService stateService;  // Spring Boot 4+

    @Test
    void getSlide_whenNoState_returnsDefault() throws Exception {
        given(stateService.getCurrentSlide()).willReturn(new SlideStateResponse(1, 11));

        mvc.perform(get("/api/slide"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.slide").value(1))
           .andExpect(jsonPath("$.totalSlides").value(11));
    }
}

// Test de controller de notas (ai-service)
@WebMvcTest(NotesController.class)
class NotesControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean NotesService notesService;

    @Test
    void getNote_whenNotExists_returns204() throws Exception {
        given(notesService.findNote("pres-1", 5)).willReturn(Optional.empty());

        mvc.perform(get("/api/ai/notes/pres-1/5"))
           .andExpect(status().isNoContent());
    }
}
```

---

## 13. Vocabulario del Dominio

Usar estos términos de forma consistente en código, variables y comentarios:

| Término             | Significado                                                  |
|---------------------|--------------------------------------------------------------|
| `slide`             | Número de diapositiva (int, 1-based)                         |
| `totalSlides`       | Total de diapositivas detectadas en `static/slides/`         |
| `currentSlide`      | La diapositiva activa actualmente                            |
| `demoState`         | Estado del modo demo: `{ mode, slide?, url? }`               |
| `mode`              | `"slides"` o `"url"` — nunca otro valor                     |
| `mainPanel`         | Panel maestro para tablet (no "control panel", no "admin")   |
| `presenter`         | Vista del presentador con notas y timer                      |
| `remote`            | Control remoto para smartphone                               |
| `showcase`          | Landing page del proyecto                                    |
| `pollIntervalMs`    | Intervalo de polling en milisegundos                         |
| `presentationId`    | Identificador único de la presentación (string, para MongoDB) |
| `slideNumber`       | Número de slide dentro de una presentación (1-based)         |
| `repoUrl`           | URL del repositorio GitHub del que Gemini extrae contexto    |
| `slideContext`      | Descripción breve del contenido del slide enviada a Gemini   |
| `keyPhrases`        | Frases clave destacadas en las notas del presentador         |
| `demoTags`          | Tags que indican qué demos hacer durante el slide            |

---

## 14. Qué Evitar Explícitamente

- No uses `ObjectMapper` directamente para serializar; deja que Spring lo haga automáticamente.
- No uses `HttpSession` para estado de presentación — Redis es el único store.
- No uses `@Value` en campos estáticos.
- No uses `System.out.println()` — usa SLF4J: `private static final Logger log = LoggerFactory.getLogger(Foo.class);`
- No crees un `DTO` separado si un `record` inmutable es suficiente.
- No hagas `@ComponentScan` extra — Spring Boot lo hace por defecto desde el paquete base.
- No implementes SDKs de Gemini ni Groq — toda la integración va por HTTP vía `WebClient`.
- No hardcodees API keys — siempre `${GEMINI_API_KEY}` y `${GROQ_API_KEY}` desde environment.
- No almacenes notas de presentador en Redis — pertenecen a MongoDB en `ai-service`.
- El `PresenterNote` usa `@Document` de Spring Data MongoDB, no `record` (necesita `@Id` y mutabilidad para upsert).

---

*Actualizado: Febrero 2026 — v1 en elaboración*
