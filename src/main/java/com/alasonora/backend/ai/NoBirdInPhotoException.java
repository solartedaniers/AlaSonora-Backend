package com.alasonora.backend.ai;

/** El motor no encontró un ave en la foto: es un resultado, no una falla de infraestructura. */
public class NoBirdInPhotoException extends RuntimeException {

    public NoBirdInPhotoException(String message) {
        super(message);
    }
}
