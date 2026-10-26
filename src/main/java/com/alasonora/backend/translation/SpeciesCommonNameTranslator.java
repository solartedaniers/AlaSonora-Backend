package com.alasonora.backend.translation;

import java.util.Optional;

/** Puerto que resuelve el nombre común en español desde el científico, en línea y sin catálogo local. */
public interface SpeciesCommonNameTranslator {

    Optional<String> translateToSpanish(String scientificName);
}
