package com.alasonora.backend.ai;

import org.springframework.web.client.RestClientResponseException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cuerpo de error del motor, {"code": "...", "detail": "..."}. El code falta si el motor es anterior a
 * los códigos o si el error es de validación de FastAPI; en ese caso solo queda el detail, como antes.
 */
record AiEngineError(String code, String detail) {

    static AiEngineError from(RestClientResponseException ex, ObjectMapper objectMapper) {
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsByteArray());
            return new AiEngineError(body.path("code").asString(null), body.path("detail").asString(ex.getMessage()));
        } catch (Exception parseError) {
            return new AiEngineError(null, ex.getMessage());
        }
    }

    boolean is(String expectedCode) {
        return expectedCode.equals(code);
    }
}
