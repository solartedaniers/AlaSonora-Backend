package com.alasonora.backend.service;

import com.alasonora.backend.exception.ApiErrorMessages;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.alasonora.backend.config.SupabaseProperties;
import com.alasonora.backend.dto.CreateDetectionRequest;
import com.alasonora.backend.dto.DetectionDto;
import com.alasonora.backend.entity.Detection;
import com.alasonora.backend.entity.DetectionCandidate;
import com.alasonora.backend.entity.Species;
import com.alasonora.backend.entity.Visibility;
import com.alasonora.backend.repository.DetectionRepository;
import com.alasonora.backend.storage.PrivateObjectSignedUrlResolver;
import com.alasonora.backend.storage.SignedUrlException;

@Service
public class DetectionService {

    private final DetectionRepository detectionRepository;
    private final SpeciesService speciesService;
    private final PrivateObjectSignedUrlResolver signedUrlResolver;
    private final SupabaseProperties.Storage storageProperties;
    private final ObserverNames observerNames;

    public DetectionService(
        DetectionRepository detectionRepository,
        SpeciesService speciesService,
        PrivateObjectSignedUrlResolver signedUrlResolver,
        SupabaseProperties supabaseProperties,
        ObserverNames observerNames
    ) {
        this.observerNames = observerNames;
        this.detectionRepository = detectionRepository;
        this.speciesService = speciesService;
        this.signedUrlResolver = signedUrlResolver;
        this.storageProperties = supabaseProperties.storage();
    }

    // Con readOnly mantengo abierta la sesión al mapear a DTO: species y alternatives son lazy y open-in-view está apagado.
    @Transactional(readOnly = true)
    public List<DetectionDto> getPublicDetections() {
        return toDtos(detectionRepository.findByVisibilityOrderByRecordedAtDesc(Visibility.PUBLIC));
    }

    @Transactional(readOnly = true)
    public List<DetectionDto> getMyDetections(UUID ownerId) {
        return toDtos(detectionRepository.findByOwnerIdOrderByRecordedAtDesc(ownerId));
    }

    /** Mapa con sesión: públicas de todos más las privadas propias (el filtro lo hace la consulta). */
    @Transactional(readOnly = true)
    public List<DetectionDto> getFeedFor(UUID requesterId) {
        return toDtos(detectionRepository.findVisibleTo(Visibility.PUBLIC, requesterId));
    }

    // Para quien no es el dueño, una detección privada no existe: no confirmo con un 403 que el id es real.
    @Transactional(readOnly = true)
    public DetectionDto getByIdForRequester(Long id, UUID requesterId) {
        Detection detection = detectionRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ApiErrorMessages.DETECTION_NOT_FOUND));
        if (detection.getVisibility() == Visibility.PRIVATE && !detection.getOwnerId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ApiErrorMessages.DETECTION_NOT_FOUND);
        }
        return toDto(detection);
    }

    public DetectionDto createDetection(UUID ownerId, CreateDetectionRequest request) {
        Detection detection = new Detection();
        requireOwnedStoragePath(ownerId, request.audioStoragePath());
        detection.setOwnerId(ownerId);
        detection.setRecordedAt(request.recordedAt());
        detection.setAudioStoragePath(request.audioStoragePath());
        detection.setDurationSeconds(request.durationSeconds());
        detection.setSpecies(speciesService.findEntity(Long.valueOf(request.speciesId())));
        detection.setConfidence(request.confidence());
        detection.setPeakFrequencyHz(request.peakFrequencyHz());
        detection.setLocation(request.location().toEntity());
        detection.setObserverName(request.observerName());
        detection.setFieldNotes(request.fieldNotes());
        detection.setVisibility(request.visibility());

        if (request.alternatives() != null) {
            request.alternatives().forEach(candidate -> {
                DetectionCandidate entity = new DetectionCandidate();
                entity.setDetection(detection);
                Species species = speciesService.findEntity(Long.valueOf(candidate.speciesId()));
                entity.setSpecies(species);
                entity.setConfidence(candidate.confidence());
                detection.getAlternatives().add(entity);
            });
        }

        return toDto(detectionRepository.save(detection));
    }

    // Solo el dueño adjunta su foto: el frontend la sube a Storage y aquí solo guardo el path.
    @Transactional
    public DetectionDto setObserverPhoto(Long id, UUID requesterId, String observerPhotoStoragePath) {
        Detection detection = detectionRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ApiErrorMessages.DETECTION_NOT_FOUND));
        if (!detection.getOwnerId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ApiErrorMessages.DETECTION_NOT_FOUND);
        }
        requireOwnedStoragePath(requesterId, observerPhotoStoragePath);
        detection.setObserverPhotoStoragePath(observerPhotoStoragePath);
        return toDto(detection);
    }

    // Borro el registro sin importar su visibilidad: así sale del mapa y del historial a la vez.
    public void deleteDetection(Long id) {
        if (!detectionRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ApiErrorMessages.DETECTION_NOT_FOUND);
        }
        detectionRepository.deleteById(id);
    }

    // Los paths vienen del cliente y los firmo con la service_role key, que ignora RLS: sin esta revisión
    // alguien podría pedir una URL firmada de la carpeta de otro.
    static boolean isOwnedStoragePath(UUID ownerId, String storagePath) {
        return storagePath.startsWith(ownerId + "/") && !storagePath.contains("..");
    }

    private static void requireOwnedStoragePath(UUID ownerId, String storagePath) {
        if (storagePath != null && !isOwnedStoragePath(ownerId, storagePath)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ApiErrorMessages.STORAGE_PATH_OUTSIDE_OWN_FOLDER);
        }
    }

    /** Una sola consulta de perfiles por lista: el nombre visible es el usuario actual de cada dueño. */
    private List<DetectionDto> toDtos(List<Detection> detections) {
        Map<UUID, String> usernames = observerNames.usernamesOf(detections);
        return detections.stream().map(detection -> toDto(detection, usernames)).toList();
    }

    private DetectionDto toDto(Detection detection) {
        return toDto(detection, observerNames.usernamesOf(List.of(detection)));
    }

    private DetectionDto toDto(Detection detection, Map<UUID, String> usernames) {
        String signedAudioUrl = detection.getAudioStoragePath() == null
            ? null
            : signedUrlResolver.resolveSignedUrl(
                storageProperties.recordingsBucket(),
                detection.getAudioStoragePath(),
                storageProperties.signedUrlTtlSeconds()
            );
        String signedObserverPhotoUrl = resolveObserverPhotoUrl(detection);
        // Sin usuario (cuenta aún sin migrar) uso el nombre que se guardó con la detección.
        String observerName = ObserverNames.nameOf(detection, usernames);
        return DetectionDto.fromEntity(detection, signedAudioUrl, signedObserverPhotoUrl, observerName);
    }

    // Si no puedo firmar la foto del observador la dejo en null: un campo secundario no debe tumbar la detección.
    private String resolveObserverPhotoUrl(Detection detection) {
        if (detection.getObserverPhotoStoragePath() == null) return null;
        try {
            return signedUrlResolver.resolveSignedUrl(
                storageProperties.observerPhotosBucket(),
                detection.getObserverPhotoStoragePath(),
                storageProperties.signedUrlTtlSeconds()
            );
        } catch (SignedUrlException ex) {
            return null;
        }
    }
}
