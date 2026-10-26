package com.alasonora.backend.ai;

/** Envuelvo cualquier fallo al hablar con el motor de fotos; lo separo del audio porque fallan por separado. */
public class PhotoClassificationException extends RuntimeException {

    public PhotoClassificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
