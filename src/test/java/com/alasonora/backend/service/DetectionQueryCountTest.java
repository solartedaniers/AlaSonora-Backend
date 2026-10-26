package com.alasonora.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import com.alasonora.backend.config.NotificationProperties;
import com.alasonora.backend.config.SupabaseProperties;
import com.alasonora.backend.entity.Detection;
import com.alasonora.backend.entity.DetectionCandidate;
import com.alasonora.backend.entity.GeoLocation;
import com.alasonora.backend.entity.IucnStatus;
import com.alasonora.backend.entity.Species;
import com.alasonora.backend.entity.Visibility;
import com.alasonora.backend.geo.ApproximateAreaResolver;
import com.alasonora.backend.repository.DetectionRepository;
import com.alasonora.backend.repository.ProfileRepository;
import com.alasonora.backend.storage.PrivateObjectSignedUrlResolver;

import jakarta.persistence.EntityManager;

// Cuento las consultas SQL reales de cada lista y compruebo que la respuesta no cambia al cargar desde la base.
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class DetectionQueryCountTest {

    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final int DETECTIONS_PER_OWNER = 6;
    private static final int MAX_QUERIES_PER_LIST = 5;

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private DetectionRepository detectionRepository;
    @Autowired
    private ProfileRepository profileRepository;

    private DetectionService detectionService;
    private NotificationService notificationService;
    private Statistics statistics;

    @BeforeEach
    void setUp() {
        PrivateObjectSignedUrlResolver signer = (bucket, path, ttl) -> bucket + ":" + path;
        SupabaseProperties supabase = new SupabaseProperties("url", "key", new SupabaseProperties.Storage("recordings", "observer-photos", 120));
        ObserverNames observerNames = new ObserverNames(profileRepository);
        detectionService = new DetectionService(detectionRepository, null, signer, supabase, observerNames);
        notificationService = new NotificationService(detectionRepository, profileRepository, observerNames,
            Mockito.mock(ApproximateAreaResolver.class), new NotificationProperties(30, 60),
            Clock.fixed(Instant.parse("2026-10-26T12:00:00Z"), ZoneOffset.UTC));
        statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();

        List<Species> species = List.of(species("Turdus fuscater"), species("Zonotrichia capensis"),
            species("Colibri coruscans"), species("Pyrocephalus rubinus"));
        for (int i = 0; i < DETECTIONS_PER_OWNER; i++) {
            detection(OWNER, i, species.get(i % 4), species.get((i + 1) % 4), species.get((i + 2) % 4));
            detection(OTHER, i, species.get((i + 3) % 4), species.get(i % 4), species.get((i + 1) % 4));
        }
        entityManager.flush();
    }

    @Test
    void myDetectionsKeepTheSameResponseWithFewQueries() {
        assertSameResponseWithFewQueries("GET /api/detections/mine", () -> detectionService.getMyDetections(OWNER));
    }

    @Test
    void feedKeepsTheSameResponseWithFewQueries() {
        assertSameResponseWithFewQueries("GET /api/detections/feed", () -> detectionService.getFeedFor(OWNER));
    }

    @Test
    void publicDetectionsKeepTheSameResponseWithFewQueries() {
        assertSameResponseWithFewQueries("GET /api/detections", () -> detectionService.getPublicDetections());
    }

    @Test
    void notificationsKeepTheSameResponseWithFewQueries() {
        assertSameResponseWithFewQueries("GET /api/notifications", () -> notificationService.feedFor(OWNER));
    }

    // Primero respondo con todo ya en memoria (la respuesta de referencia) y luego desde cero, contando el SQL.
    private void assertSameResponseWithFewQueries(String endpoint, Supplier<Object> request) {
        Object expected = request.get();
        entityManager.clear();
        statistics.clear();

        Object actual = request.get();

        long queries = statistics.getPrepareStatementCount();
        System.out.println(endpoint + " -> " + queries + " consultas SQL");
        assertEquals(expected, actual);
        assertTrue(queries <= MAX_QUERIES_PER_LIST, endpoint + " hizo " + queries + " consultas");
    }

    private Species species(String scientificName) {
        Species species = new Species();
        species.setScientificName(scientificName);
        species.setCommonName(scientificName + " es");
        species.setCommonNameEn(scientificName + " en");
        species.setIucnStatus(IucnStatus.LC);
        species.setTypicalVocalizations("song, call");
        entityManager.persist(species);
        return species;
    }

    private void detection(UUID ownerId, int index, Species main, Species... alternatives) {
        Detection detection = new Detection();
        detection.setOwnerId(ownerId);
        detection.setRecordedAt(Instant.parse("2026-10-01T10:00:00Z").plusSeconds(3600L * index + (ownerId.equals(OWNER) ? 0 : 60)));
        detection.setAudioStoragePath(ownerId + "/" + index + ".wav");
        detection.setDurationSeconds(5);
        detection.setSpecies(main);
        detection.setConfidence(80 + index);
        detection.setPeakFrequencyHz(4000);
        GeoLocation location = new GeoLocation();
        location.setLatitude(4.6);
        location.setLongitude(-74.1);
        location.setPlaceName("Bogotá");
        detection.setLocation(location);
        detection.setObserverName("Observador " + index);
        detection.setVisibility(index % 2 == 0 ? Visibility.PUBLIC : Visibility.PRIVATE);
        for (int i = 0; i < alternatives.length; i++) {
            DetectionCandidate candidate = new DetectionCandidate();
            candidate.setDetection(detection);
            candidate.setSpecies(alternatives[i]);
            candidate.setConfidence(50 - i * 10);
            detection.getAlternatives().add(candidate);
        }
        entityManager.persist(detection);
    }
}
