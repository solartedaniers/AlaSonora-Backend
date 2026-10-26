package com.alasonora.backend.ai;

import java.util.List;

/** Puerto del motor bioacústico: el servicio depende solo de esta interfaz, así cambio el motor sin tocarlo. */
public interface BirdSoundClassifier {

    /** @return candidatos ordenados por confianza, el mayor primero. Nunca null; vacío si no se identificó nada. */
    List<RawClassificationCandidate> classify(ClassificationRequest request);
}
