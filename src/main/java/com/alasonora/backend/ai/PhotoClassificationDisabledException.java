package com.alasonora.backend.ai;

/** El motor tiene apagada la identificación por foto (PHOTO_DISABLED): no es una caída del servicio. */
public class PhotoClassificationDisabledException extends RuntimeException {

    public PhotoClassificationDisabledException() {
        super("Photo classification is disabled in the AI engine");
    }
}
