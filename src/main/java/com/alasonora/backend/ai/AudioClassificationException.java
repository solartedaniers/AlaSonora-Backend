package com.alasonora.backend.ai;

/** Envuelvo cualquier fallo al hablar con el motor (red, respuesta inválida, timeout); el status HTTP lo decide el servicio. */
public class AudioClassificationException extends RuntimeException {

    public AudioClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
