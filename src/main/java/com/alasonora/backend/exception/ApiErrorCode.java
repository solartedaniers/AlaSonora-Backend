package com.alasonora.backend.exception;

/**
 * Códigos de error estables para el cliente. A diferencia del texto del
 * mensaje, no cambian al reescribirlo o traducirlo.
 */
public enum ApiErrorCode {
    NO_SPECIES_IDENTIFIED,
    AI_ENGINE_UNAVAILABLE,
    AI_ENGINE_BUSY,
    USERNAME_INVALID,
    USERNAME_TAKEN,
    AUDIO_TOO_LARGE,
    AUDIO_UNSUPPORTED,
    AUDIO_TOO_NOISY,
    NO_BIRD_SIGNAL,
    // 422 del motor sin código (motor anterior): el audio fue rechazado sin saber la causa exacta.
    AUDIO_REJECTED,
    NO_BIRD_IN_PHOTO,
    PHOTO_UNAVAILABLE,
    DETECTION_NOT_FOUND
}
