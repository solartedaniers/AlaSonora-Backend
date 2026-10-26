package com.alasonora.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.alasonora.backend.config.NotificationProperties;
import com.alasonora.backend.config.SupabaseProperties;
import com.alasonora.backend.entity.Detection;
import com.alasonora.backend.entity.DetectionCandidate;
import com.alasonora.backend.entity.GeoLocation;
import com.alasonora.backend.entity.IucnStatus;
import com.alasonora.backend.entity.Species;
import com.alasonora.backend.entity.Visibility;
import com.alasonora.backend.exception.ApiErrorCode;
import com.alasonora.backend.exception.CodedResponseStatusException;
import com.alasonora.backend.geo.ApproximateAreaResolver;
import com.alasonora.backend.repository.DetectionRepository;
import com.alasonora.backend.repository.ProfileRepository;
import com.alasonora.backend.storage.DetectionFileRemover;
import com.alasonora.backend.storage.SupabaseStorageObjectRemover;

import jakarta.persistence.EntityManager;

// Borro contra una base real (H2) para comprobar también la cascada de alternativas.
@DataJpaTest
class DetectionDeletionTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final String RECORDINGS = "recordings";
    private static final String PHOTOS = "observer-photos";

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private DetectionRepository detectionRepository;
    @Autowired
    private ProfileRepository profileRepository;

    private final SupabaseStorageObjectRemover storage = mock(SupabaseStorageObjectRemover.class);
    private DetectionService detectionService;
    private NotificationService notificationService;
    private Long detectionId;

    @BeforeEach
    void setUp() {
        SupabaseProperties supabase = new SupabaseProperties("url", "key", new SupabaseProperties.Storage(RECORDINGS, PHOTOS, 120));
        ObserverNames observerNames = new ObserverNames(profileRepository);
        detectionService = new DetectionService(detectionRepository, null, (bucket, path, ttl) -> bucket + ":" + path,
            supabase, observerNames, new DetectionFileRemover(storage, supabase));
        notificationService = new NotificationService(detectionRepository, profileRepository, observerNames,
            mock(ApproximateAreaResolver.class), new NotificationProperties(30, 60),
            Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC));
        when(storage.remove(anyString(), anyList())).thenReturn(1);
        detectionId = publicDetectionOf(OWNER);
    }

    @Test
    void theOwnerDeletesTheDetectionWithItsAlternativesAndFiles() {
        detectionService.deleteOwnDetection(detectionId, OWNER);

        assertEquals(0, detectionRepository.count());
        assertEquals(0L, candidateCount());
        verify(storage).remove(RECORDINGS, List.of(OWNER + "/1.wav"));
        verify(storage).remove(PHOTOS, List.of(OWNER + "/1.jpg"));
    }

    @Test
    void aDeletedPublicDetectionLeavesThePublicMapAndTheOthersNotifications() {
        detectionService.deleteOwnDetection(detectionId, OWNER);

        assertTrue(detectionService.getPublicDetections().isEmpty());
        assertTrue(detectionService.getFeedFor(OTHER).isEmpty());
        assertTrue(notificationService.feedFor(OTHER).items().isEmpty());
    }

    @Test
    void anotherUserGetsForbiddenAndNothingIsDeleted() {
        CodedResponseStatusException error = assertThrows(CodedResponseStatusException.class,
            () -> detectionService.deleteOwnDetection(detectionId, OTHER));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
        assertEquals(ApiErrorCode.DETECTION_NOT_OWNED, error.getErrorCode());
        assertEquals(1, detectionRepository.count());
        verifyNoInteractions(storage);
    }

    @Test
    void aMissingDetectionGivesNotFound() {
        CodedResponseStatusException error = assertThrows(CodedResponseStatusException.class,
            () -> detectionService.deleteOwnDetection(detectionId + 100, OWNER));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        assertEquals(ApiErrorCode.DETECTION_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void aStorageFailureDoesNotStopTheDeletion() {
        when(storage.remove(anyString(), anyList())).thenThrow(new IllegalStateException("storage down"));

        detectionService.deleteOwnDetection(detectionId, OWNER);

        assertEquals(0, detectionRepository.count());
        // Si el audio falla, igual intento la foto.
        verify(storage).remove(PHOTOS, List.of(OWNER + "/1.jpg"));
    }

    @Test
    void theAdminDeletionWorksAsBefore() {
        detectionService.deleteDetection(detectionId);

        assertEquals(0, detectionRepository.count());
        assertEquals(0L, candidateCount());
        verifyNoInteractions(storage);
        ResponseStatusException missing = assertThrows(ResponseStatusException.class, () -> detectionService.deleteDetection(detectionId));
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
    }

    private long candidateCount() {
        return entityManager.createQuery("select count(c) from DetectionCandidate c", Long.class).getSingleResult();
    }

    private Long publicDetectionOf(UUID ownerId) {
        Species species = new Species();
        species.setScientificName("Turdus fuscater");
        species.setCommonName("Mirla patinaranja");
        species.setCommonNameEn("Great Thrush");
        species.setIucnStatus(IucnStatus.LC);
        entityManager.persist(species);

        Detection detection = new Detection();
        detection.setOwnerId(ownerId);
        detection.setRecordedAt(Instant.parse("2026-10-20T10:00:00Z"));
        detection.setAudioStoragePath(ownerId + "/1.wav");
        detection.setObserverPhotoStoragePath(ownerId + "/1.jpg");
        detection.setDurationSeconds(5);
        detection.setSpecies(species);
        detection.setConfidence(90);
        detection.setPeakFrequencyHz(4000);
        GeoLocation location = new GeoLocation();
        location.setLatitude(4.6);
        location.setLongitude(-74.1);
        location.setPlaceName("Bogotá");
        detection.setLocation(location);
        detection.setVisibility(Visibility.PUBLIC);
        DetectionCandidate candidate = new DetectionCandidate();
        candidate.setDetection(detection);
        candidate.setSpecies(species);
        candidate.setConfidence(40);
        detection.getAlternatives().add(candidate);
        entityManager.persist(detection);
        entityManager.flush();
        entityManager.clear();
        return detection.getId();
    }
}
