package com.alasonora.backend.exception;

import java.util.Map;

import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

/** Dejo el cuerpo de error de Spring y le agrego errorCode cuando alguna causa lo trae. */
@Component
public class ApiErrorAttributes extends DefaultErrorAttributes {

    static final String ERROR_CODE_ATTRIBUTE = "errorCode";

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);
        // Recorro las causas: la clasificación es asíncrona y llega envuelta en CompletionException.
        for (Throwable error = getError(webRequest); error != null; error = error.getCause()) {
            if (error instanceof CodedResponseStatusException coded) {
                attributes.put(ERROR_CODE_ATTRIBUTE, coded.getErrorCode().name());
                break;
            }
        }
        return attributes;
    }
}
