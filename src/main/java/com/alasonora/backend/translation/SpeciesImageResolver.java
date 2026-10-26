package com.alasonora.backend.translation;

import java.util.Optional;

/** Puerto que resuelve una imagen de referencia desde el nombre científico, sin catálogo local. */
public interface SpeciesImageResolver {

    Optional<String> resolveImageUrl(String scientificName);
}
