package com.alasonora.backend.ai;

/**
 * El motor rechazó el audio por su calidad, no por una falla de infraestructura. UNKNOWN es un 422 sin
 * código (motor anterior): conservo su detalle en el mensaje.
 */
public class AudioQualityException extends RuntimeException {

    public enum Reason { TOO_NOISY, NO_BIRD_SIGNAL, UNKNOWN }

    private final Reason reason;

    public AudioQualityException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
