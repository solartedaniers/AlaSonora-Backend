package com.alasonora.backend.service;

import com.alasonora.backend.exception.ApiErrorMessages;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.alasonora.backend.dto.SpeciesDto;
import com.alasonora.backend.entity.IucnStatus;
import com.alasonora.backend.entity.Species;
import com.alasonora.backend.repository.SpeciesRepository;
import com.alasonora.backend.translation.SpeciesCommonNameTranslator;
import com.alasonora.backend.translation.SpeciesImageResolver;
import com.alasonora.backend.translation.SpeciesVocalizationReferenceResolver;

@Service
public class SpeciesService {

    private final SpeciesRepository speciesRepository;
    private final SpeciesCommonNameTranslator nameTranslator;
    private final SpeciesImageResolver imageResolver;
    private final SpeciesVocalizationReferenceResolver vocalizationReferenceResolver;

    public SpeciesService(
        SpeciesRepository speciesRepository,
        SpeciesCommonNameTranslator nameTranslator,
        SpeciesImageResolver imageResolver,
        SpeciesVocalizationReferenceResolver vocalizationReferenceResolver
    ) {
        this.speciesRepository = speciesRepository;
        this.nameTranslator = nameTranslator;
        this.imageResolver = imageResolver;
        this.vocalizationReferenceResolver = vocalizationReferenceResolver;
    }

    public List<SpeciesDto> getAll() {
        return speciesRepository.findAll().stream().map(SpeciesDto::fromEntity).toList();
    }

    public SpeciesDto getById(Long id) {
        return SpeciesDto.fromEntity(findEntity(id));
    }

    Species findEntity(Long id) {
        return speciesRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ApiErrorMessages.SPECIES_NOT_FOUND + id));
    }

    /**
     * Reutilizo la especie por nombre científico o registro una nueva; lo que el motor no da (UICN, taxonomía)
     * queda con valores de relleno honestos para que un curador lo complete.
     */
    @Transactional
    public Species findOrCreateByScientificName(String scientificName, String commonNameEn) {
        Optional<Species> existing = speciesRepository.findByScientificNameIgnoreCase(scientificName);
        existing.filter(SpeciesService::lacksSpanishName).ifPresent(this::backfillSpanishName);
        return existing.orElseGet(() -> speciesRepository.save(newAutoRegisteredSpecies(scientificName, commonNameEn)));
    }

    /** Sin nombre en español: vacío o, en registros antiguos, igual al inglés. */
    static boolean lacksSpanishName(Species species) {
        String name = species.getCommonName();
        return name == null || name.isBlank() || name.equalsIgnoreCase(species.getCommonNameEn());
    }

    /**
     * Si la especie quedó sin nombre en español, reintento la traducción en segundo plano cuando se vuelve a usar,
     * sin retrasar la clasificación en curso.
     */
    private void backfillSpanishName(Species species) {
        Long id = species.getId();
        String scientificName = species.getScientificName();
        CompletableFuture.supplyAsync(() -> nameTranslator.translateToSpanish(scientificName))
            .thenAccept(name -> name.ifPresent(spanish -> speciesRepository.findById(id)
                .filter(SpeciesService::lacksSpanishName)
                .ifPresent(stored -> {
                    stored.setCommonName(spanish);
                    speciesRepository.save(stored);
                })));
    }

    private Species newAutoRegisteredSpecies(String scientificName, String commonNameEn) {
        Species species = new Species();
        species.setScientificName(scientificName);
        species.setCommonNameEn(commonNameEn);
        species.setIucnStatus(IucnStatus.NE);

        // Lanzo las tres consultas externas a la vez: el tiempo total queda en la más lenta y no en la suma.
        CompletableFuture<Optional<String>> commonNameFuture =
            CompletableFuture.supplyAsync(() -> nameTranslator.translateToSpanish(scientificName));
        CompletableFuture<Optional<String>> imageFuture =
            CompletableFuture.supplyAsync(() -> imageResolver.resolveImageUrl(scientificName));
        CompletableFuture<List<String>> vocalizationsFuture =
            CompletableFuture.supplyAsync(() -> vocalizationReferenceResolver.resolveTypicalVocalizations(scientificName));

        // Traduzco con Wikidata; sin nombre en español lo dejo vacío (la columna es NOT NULL), nunca en inglés,
        // y backfillSpanishName lo completa después.
        species.setCommonName(commonNameFuture.join().orElse(""));
        // Foto de referencia real vía Wikimedia Commons; sin foto queda null y el frontend muestra su
        // ilustración con el aviso. Guardar una ruta de placeholder del frontend la rompía al cambiar.
        species.setImageUrl(imageFuture.join().orElse(null));
        // Tipos de vocalización típicos según Xeno-canto, resueltos una sola
        // vez aquí; nunca se vuelve a consultar por cada grabación del usuario.
        species.setTypicalVocalizations(String.join(", ", vocalizationsFuture.join()));

        return species;
    }
}
