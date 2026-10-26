package com.alasonora.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.alasonora.backend.ai.AiEngineReadiness;
import com.alasonora.backend.ai.AudioQualityException;
import com.alasonora.backend.ai.BirdPhotoClassifier;
import com.alasonora.backend.ai.BirdSoundClassifier;
import com.alasonora.backend.ai.NoBirdInPhotoException;
import com.alasonora.backend.ai.PhotoClassificationDisabledException;
import com.alasonora.backend.async.AiTaskRunner;
import com.alasonora.backend.config.AiProperties;
import com.alasonora.backend.dto.ClassifyDetectionRequest;
import com.alasonora.backend.exception.ApiErrorCode;
import com.alasonora.backend.exception.CodedResponseStatusException;

/** Cada rechazo del motor sale con su ApiErrorCode: el frontend ya no depende del mensaje. */
class ClassificationErrorCodeTest {

    private final AiTaskRunner runner = new AiTaskRunner(new SameThreadExecutor());
    private final AiProperties properties = new AiProperties(null, null, new AiProperties.Catalog("disclaimer"), null,
        new AiProperties.Wake(1000, 1000, 10, 60_000));

    @Test
    void mapsEachAudioQualityReasonToItsCode() {
        assertEquals(ApiErrorCode.AUDIO_TOO_NOISY, audioErrorFor(AudioQualityException.Reason.TOO_NOISY).getErrorCode());
        assertEquals(ApiErrorCode.NO_BIRD_SIGNAL, audioErrorFor(AudioQualityException.Reason.NO_BIRD_SIGNAL).getErrorCode());

        CodedResponseStatusException unknown = audioErrorFor(AudioQualityException.Reason.UNKNOWN);
        assertEquals(ApiErrorCode.AUDIO_REJECTED, unknown.getErrorCode());
        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, unknown.getStatusCode());
        assertEquals("engine detail", unknown.getReason());
    }

    @Test
    void mapsThePhotoRejections() {
        CodedResponseStatusException noBird = photoErrorFor(bytes -> {
            throw new NoBirdInPhotoException("No bird could be identified in the photo");
        });
        assertEquals(ApiErrorCode.NO_BIRD_IN_PHOTO, noBird.getErrorCode());

        CodedResponseStatusException disabled = photoErrorFor(bytes -> {
            throw new PhotoClassificationDisabledException();
        });
        assertEquals(ApiErrorCode.PHOTO_UNAVAILABLE, disabled.getErrorCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, disabled.getStatusCode());
    }

    private CodedResponseStatusException audioErrorFor(AudioQualityException.Reason reason) {
        BirdSoundClassifier classifier = request -> {
            throw new AudioQualityException(reason, "engine detail");
        };
        DetectionClassificationService service = new DetectionClassificationService(
            classifier, null, runner, properties, new AiEngineReadiness(Clock.systemUTC(), properties));
        ClassifyDetectionRequest request = new ClassifyDetectionRequest("http://audio", Instant.now(), null);
        return unwrap(() -> service.classify(request).join());
    }

    private CodedResponseStatusException photoErrorFor(BirdPhotoClassifier classifier) {
        PhotoClassificationService service = new PhotoClassificationService(classifier, runner);
        return unwrap(() -> service.classify(new MockMultipartFile("photo", new byte[] {1})).join());
    }

    private static CodedResponseStatusException unwrap(Runnable call) {
        try {
            call.run();
        } catch (CompletionException wrapped) {
            return assertInstanceOf(CodedResponseStatusException.class, wrapped.getCause());
        }
        throw new AssertionError("expected a coded error");
    }

    // Corro la tarea en el mismo hilo: aquí solo pruebo la traducción del error.
    private static final class SameThreadExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }
}
