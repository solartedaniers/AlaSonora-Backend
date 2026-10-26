package com.alasonora.backend.translation;

import java.util.List;

/** Puerto que resuelve los tipos de vocalización documentados de una especie; es referencia, no análisis de una grabación. */
public interface SpeciesVocalizationReferenceResolver {

    List<String> resolveTypicalVocalizations(String scientificName);
}
