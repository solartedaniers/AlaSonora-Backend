package com.alasonora.backend.service;

import com.alasonora.backend.exception.ApiErrorMessages;
import com.alasonora.backend.exception.ApiErrorCode;
import com.alasonora.backend.exception.CodedResponseStatusException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.alasonora.backend.ai.AiEngineReadiness;
import com.alasonora.backend.ai.AiEngineUnavailableException;
import com.alasonora.backend.ai.AudioFileRejectedException;
import com.alasonora.backend.ai.AudioClassificationException;
import com.alasonora.backend.ai.AudioQualityException;
import com.alasonora.backend.ai.BirdSoundClassifier;
import com.alasonora.backend.ai.ClassificationRequest;
import com.alasonora.backend.ai.RawClassificationCandidate;
import com.alasonora.backend.async.AiTaskRunner;
import com.alasonora.backend.config.AiProperties;
import com.alasonora.backend.dto.ClassificationResultDto;
import com.alasonora.backend.dto.ClassifyDetectionRequest;
import com.alasonora.backend.dto.DetectionCandidateDto;
import com.alasonora.backend.dto.GeoLocationDto;
import com.alasonora.backend.dto.SpeciesDto;

/**
 * Orquesta una clasificación: corre en el bulkhead de IA vía {@link AiTaskRunner} (nunca en un
 * hilo de Tomcat), llama al puerto {@link BirdSoundClassifier} y deja crecer el catálogo
 * con {@link SpeciesService} para las especies de BirdNET que aún no están en la base.
 */
@Service
public class DetectionClassificationService {

    private static final Logger log = LoggerFactory.getLogger(DetectionClassificationService.class);

    private final BirdSoundClassifier classifier;
    private final SpeciesService speciesService;
    private final AiTaskRunner aiTaskRunner;
    private final AiProperties.Catalog catalogProperties;
    private final AiEngineReadiness engineReadiness;

    public DetectionClassificationService(
        BirdSoundClassifier classifier,
        SpeciesService speciesService,
        AiTaskRunner aiTaskRunner,
        AiProperties aiProperties,
        AiEngineReadiness engineReadiness
    ) {
        this.classifier = classifier;
        this.speciesService = speciesService;
        this.aiTaskRunner = aiTaskRunner;
        this.catalogProperties = aiProperties.catalog();
        this.engineReadiness = engineReadiness;
    }

    public CompletableFuture<ClassificationResultDto> classify(ClassifyDetectionRequest request) {
        return aiTaskRunner.supply(() -> runClassification(request));
    }

    private ClassificationResultDto runClassification(ClassifyDetectionRequest request) {
        GeoLocationDto location = request.location();
        List<RawClassificationCandidate> raw;
        try {
            raw = classifier.classify(new ClassificationRequest(
                request.audioUrl(),
                location != null ? location.latitude() : null,
                location != null ? location.longitude() : null,
                request.recordedAt()
            ));
        } catch (AudioQualityException ex) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), ex);
        } catch (AudioFileRejectedException ex) {
            throw ex.reason() == AudioFileRejectedException.Reason.TOO_LARGE
                ? new CodedResponseStatusException(HttpStatus.CONTENT_TOO_LARGE, ApiErrorMessages.AUDIO_TOO_LARGE, ApiErrorCode.AUDIO_TOO_LARGE)
                : new CodedResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiErrorMessages.AUDIO_UNSUPPORTED, ApiErrorCode.AUDIO_UNSUPPORTED);
        } catch (AiEngineUnavailableException ex) {
            // Código estable: el frontend espera a que el motor despierte y reintenta solo.
            log.warn("AI engine unavailable: {}", ex.getMessage());
            // Se durmió: olvido el "listo" para que el próximo /api/ai/status lo despierte.
            engineReadiness.markUnavailable();
            throw new CodedResponseStatusException(HttpStatus.BAD_GATEWAY, ApiErrorMessages.BIOACOUSTIC_ENGINE_UNAVAILABLE, ApiErrorCode.AI_ENGINE_UNAVAILABLE);
        } catch (AudioClassificationException ex) {
            log.warn("Classification failed", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, ApiErrorMessages.BIOACOUSTIC_ENGINE_UNAVAILABLE, ex);
        }

        if (raw.isEmpty()) {
            throw new CodedResponseStatusException(HttpStatus.UNPROCESSABLE_CONTENT, ApiErrorMessages.NO_SPECIES_IN_RECORDING, ApiErrorCode.NO_SPECIES_IDENTIFIED);
        }

        List<DetectionCandidateDto> ranked = raw.stream()
            .map(candidate -> new DetectionCandidateDto(
                SpeciesDto.fromEntity(speciesService.findOrCreateByScientificName(candidate.scientificName(), candidate.commonName())),
                candidate.confidence()
            ))
            .toList();

        DetectionCandidateDto top = ranked.get(0);
        List<DetectionCandidateDto> alternatives = ranked.subList(1, ranked.size());
        return new ClassificationResultDto(top.species(), top.confidence(), alternatives, catalogProperties.disclaimer());
    }
}
