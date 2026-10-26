package com.alasonora.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** La URL base del motor queda sin espacios ni barras finales, y un valor inválido no tiene host. */
class AiPropertiesEngineTest {

    @Test
    void stripsWhitespaceAndTrailingSlashes() {
        assertEquals("https://alasonora-ai-engine.onrender.com", engine(" https://alasonora-ai-engine.onrender.com// ").baseUrl());
    }

    @Test
    void exposesOnlyTheHost() {
        assertEquals("alasonora-ai-engine.onrender.com", engine("https://alasonora-ai-engine.onrender.com/").host());
    }

    @Test
    void invalidOrMissingUrlHasNoHost() {
        assertNull(engine("not a url").host());
        assertNull(engine("alasonora-ai-engine.onrender.com").host());
        assertNull(engine("ftp://alasonora-ai-engine.onrender.com").host());
        assertNull(engine(null).host());
    }

    private static AiProperties.Engine engine(String baseUrl) {
        return new AiProperties.Engine(baseUrl, "key", 2000, 0.1, 3, 1000, 1, 10, 1000);
    }
}
