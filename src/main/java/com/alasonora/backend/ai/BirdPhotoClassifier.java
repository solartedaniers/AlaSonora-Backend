package com.alasonora.backend.ai;

import java.util.List;

/** Puerto del motor que identifica aves en una foto; lo separo de BirdSoundClassifier porque falla por su cuenta. */
public interface BirdPhotoClassifier {

    /** @return candidatos ordenados por confianza, el mayor primero. Nunca null; vacío si no se identificó nada. */
    List<RawPhotoClassificationCandidate> classify(byte[] photoBytes);
}
