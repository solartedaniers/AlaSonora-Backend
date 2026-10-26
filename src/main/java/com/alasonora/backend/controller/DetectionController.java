package com.alasonora.backend.controller;

import com.alasonora.backend.exception.ApiErrorMessages;

import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.alasonora.backend.dto.ClassificationResultDto;
import com.alasonora.backend.dto.ClassifyDetectionRequest;
import com.alasonora.backend.dto.CreateDetectionRequest;
import com.alasonora.backend.dto.DetectionDto;
import com.alasonora.backend.dto.SetObserverPhotoRequest;
import com.alasonora.backend.dto.UserStatsDto;
import com.alasonora.backend.service.DetectionClassificationService;
import com.alasonora.backend.service.DetectionService;
import com.alasonora.backend.service.UserStatsService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/detections")
public class DetectionController {

    private static final String PUBLIC_VISIBILITY_PARAM = "public";

    private final DetectionService detectionService;
    private final DetectionClassificationService classificationService;
    private final UserStatsService userStatsService;

    public DetectionController(
        DetectionService detectionService,
        DetectionClassificationService classificationService,
        UserStatsService userStatsService
    ) {
        this.detectionService = detectionService;
        this.classificationService = classificationService;
        this.userStatsService = userStatsService;
    }

    @GetMapping
    public List<DetectionDto> getPublicDetections(@RequestParam String visibility) {
        if (!PUBLIC_VISIBILITY_PARAM.equalsIgnoreCase(visibility)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ApiErrorMessages.ONLY_PUBLIC_VISIBILITY);
        }
        return detectionService.getPublicDetections();
    }

    /** Mapa con sesión: públicas de todos más mis privadas. Requiere token (no está en PublicEndpoints). */
    @GetMapping("/feed")
    public List<DetectionDto> getFeed(@AuthenticationPrincipal Jwt jwt) {
        return detectionService.getFeedFor(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/mine")
    public List<DetectionDto> getMine(@AuthenticationPrincipal Jwt jwt) {
        return detectionService.getMyDetections(UUID.fromString(jwt.getSubject()));
    }

    @GetMapping("/mine/stats")
    public UserStatsDto getMyStats(@AuthenticationPrincipal Jwt jwt, @RequestParam(defaultValue = "UTC") ZoneId zone) {
        return userStatsService.getStats(UUID.fromString(jwt.getSubject()), zone);
    }

    // Firmo la URL del audio en cada lectura y nunca la guardo: así una detección privada sigue reproducible.
    @GetMapping("/{id}")
    public DetectionDto getById(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return detectionService.getByIdForRequester(id, UUID.fromString(jwt.getSubject()));
    }

    @PostMapping
    public ResponseEntity<DetectionDto> create(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody CreateDetectionRequest request
    ) {
        DetectionDto created = detectionService.createDetection(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // Solo el dueño borra: 403 si es de otro y 404 si no existe.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        detectionService.deleteOwnDetection(id, UUID.fromString(jwt.getSubject()));
        return ResponseEntity.noContent().build();
    }

    // El frontend ya subió la foto a Storage; aquí solo guardo el path si quien la pide es el dueño.
    @PutMapping("/{id}/observer-photo")
    public DetectionDto setObserverPhoto(
        @AuthenticationPrincipal Jwt jwt,
        @PathVariable Long id,
        @Valid @RequestBody SetObserverPhotoRequest request
    ) {
        return detectionService.setObserverPhoto(id, UUID.fromString(jwt.getSubject()), request.observerPhotoStoragePath());
    }

    // Devuelvo un CompletableFuture para liberar el hilo de Tomcat mientras responde el motor.
    @PostMapping("/classify")
    public CompletableFuture<ResponseEntity<ClassificationResultDto>> classify(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody ClassifyDetectionRequest request
    ) {
        return classificationService.classify(request).thenApply(ResponseEntity::ok);
    }
}
