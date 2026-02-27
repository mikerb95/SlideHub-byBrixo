package com.brixo.slidehub.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifica que el contexto arranca sin necesidad de un MongoDB activo.
 */
@SpringBootTest(webEnvironment = WebEnvironment.MOCK, excludeAutoConfiguration = {
        MongoAutoConfiguration.class,
        MongoDataAutoConfiguration.class
})
@TestPropertySource(properties = {
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
