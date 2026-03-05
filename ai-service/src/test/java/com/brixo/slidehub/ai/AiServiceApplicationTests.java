package com.brixo.slidehub.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifica que el contexto arranca sin necesidad de un MongoDB activo.
 * En Spring Boot 4, excludeAutoConfiguration fue eliminado de @SpringBootTest.
 * Se usa spring.autoconfigure.exclude como propiedad y los packages actualizados.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration,"
                + "org.springframework.boot.data.mongodb.autoconfigure.DataMongoAutoConfiguration",
        "spring.data.mongodb.uri=mongodb://localhost:27017/slidehub-test",
        "slidehub.ai.gemini.api-key=test-key",
        "slidehub.ai.groq.api-key=test-key"
})
class AiServiceApplicationTests {

    @Test
    void contextLoads() {
        // Si no lanza excepción, el contexto arrancó correctamente
    }
}
