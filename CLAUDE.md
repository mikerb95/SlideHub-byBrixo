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
- Redis (estado en memoria), Thymeleaf (UI), Spring Cloud Gateway (enrutamiento)

---

## 2. Cómo Razonar antes de Codificar

Antes de producir cualquier código, recorre este checklist internamente:

1. ¿Esta tarea pertenece a `state-service`, `ui-service` o `gateway-service`?
2. ¿El comportamiento esperado está descrito en `docs/Presentation-Module-Analysis.md`? Si sí, cítalo.
3. ¿Qué paquete Java corresponde según la convención de `AGENTS.md §4`?
4. ¿Requiere una nueva dependencia en `pom.xml`? Si sí, ¿ya está declarada en el parent?
5. ¿El cambio rompe alguna "Decisión Abierta" de `AGENTS.md §8`? Si es así, preguntar antes de proceder.

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
    <module>gateway-service</module>
</modules>
```

### POMs de submódulos

Cada submódulo hereda del parent y declara **solo las dependencias que necesita**.
No copiar todas las dependencias del parent a cada hijo.

| Servicio          | Dependencias clave                                    |
|-------------------|-------------------------------------------------------|
| `state-service`   | `web`, `data-redis`, `actuator`                       |
| `ui-service`      | `web`, `thymeleaf`, `webflux` (solo WebClient), `actuator` |
| `gateway-service` | `spring-cloud-gateway-server-webmvc`, `config-server`, `actuator` |

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
slidehub.poll.slides.interval-ms=1000
slidehub.poll.presenter.interval-ms=1500
slidehub.poll.demo.interval-ms=800

# gateway-service
spring.application.name=gateway-service
server.port=8080
spring.cloud.config.server.native.search-locations=classpath:/config-repo
```

---

## 8. Cómo Abordar Tareas Comunes

### "Implementa el endpoint `GET /api/slide`"

1. Ubicar en `state-service/src/main/java/com/brixo/slidehub/state/`
2. Crear (si no existe): `model/SlideState.java` (record), `service/SlideStateService.java`, `controller/SlideController.java`
3. `SlideStateService` lee/escribe en Redis con clave `"current_slide"`.
4. Comportamiento: si la clave no existe, retornar `{ "slide": 1 }` por defecto.
5. Referencia: `docs/Presentation-Module-Analysis.md §8.1`

### "Implementa la vista `/slides`"

1. Ubicar en `ui-service`
2. Controller `PresentationViewController` llama a `SlideUiService.fetchTotalSlides()` y `fetchCurrentSlide()`
3. Template `src/main/resources/templates/slides.html`
4. JS inline: polling cada `${pollIntervalMs}` ms a `/api/slide` (pasa por el gateway)
5. Referencia: `docs/Presentation-Module-Analysis.md §7.3`

### "Configura el API Gateway"

1. Ubicar en `gateway-service`
2. Rutas en `application.properties` o `@Bean RouteLocator` en `GatewayConfig.java`
3. `/api/**` → `http://localhost:8081`
4. Todo lo demás (rutas de UI) → `http://localhost:8082`
5. Referencia: `AGENTS.md §2.3`

---

## 9. Testing

```java
// Test de controller con MockMvc (state-service)
@WebMvcTest(SlideController.class)
class SlideControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean SlideStateService stateService;  // Spring Boot 4+

    @Test
    void getSlide_whenNoState_returnsDefault() throws Exception {
        given(stateService.getCurrentSlide()).willReturn(new SlideState(1));

        mvc.perform(get("/api/slide"))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.slide").value(1));
    }
}
```

---

## 10. Vocabulario del Dominio

Usar estos términos de forma consistente en código, variables y comentarios:

| Término          | Significado                                                  |
|------------------|--------------------------------------------------------------|
| `slide`          | Número de diapositiva (int, 1-based)                         |
| `totalSlides`    | Total de diapositivas detectadas en `assets/slides/`         |
| `currentSlide`   | La diapositiva activa actualmente                            |
| `demoState`      | Estado del modo demo: `{ mode, slide?, url? }`               |
| `mode`           | `"slides"` o `"url"` — nunca otro valor                     |
| `mainPanel`      | Panel maestro para tablet (no "control panel", no "admin")   |
| `presenter`      | Vista del presentador con notas y timer                      |
| `remote`         | Control remoto para smartphone                               |
| `showcase`       | Landing page del proyecto                                    |
| `pollIntervalMs` | Intervalo de polling en milisegundos                         |

---

## 11. Qué Evitar Explícitamente

- No uses `ObjectMapper` directamente para serializar; deja que Spring lo haga automáticamente.
- No uses `HttpSession` para estado de presentación — Redis es el único store.
- No uses `@Value` en campos estáticos.
- No uses `System.out.println()` — usa SLF4J: `private static final Logger log = LoggerFactory.getLogger(Foo.class);`
- No crees un `DTO` separado si un `record` inmutable es suficiente.
- No hagas `@ComponentScan` extra — Spring Boot lo hace por defecto desde el paquete base.
- No implementes autenticación todavía — `spring-boot-starter-security` está en el parent POM pero no se activará en los submódulos hasta que se decida.

---

*Actualizado: Febrero 2026 — v1 en elaboración*
