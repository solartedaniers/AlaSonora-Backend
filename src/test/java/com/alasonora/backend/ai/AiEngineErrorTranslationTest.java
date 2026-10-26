package com.alasonora.backend.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.alasonora.backend.config.AiProperties;
import com.alasonora.backend.config.AudioUploadProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/** Traduzco los códigos en inglés del motor; sin código (motor anterior) sigo funcionando como antes. */
class AiEngineErrorTranslationTest {

    private HttpServer server;
    private volatile int engineStatus;
    private volatile String engineBody;

    @BeforeEach
    void startStubEngine() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/audio.wav", exchange -> respond(exchange, 200, "RIFF\0\0\0\0WAVEfmt "));
        server.createContext("/analyze", exchange -> respond(exchange, engineStatus, engineBody));
        server.createContext("/classify-photo", exchange -> respond(exchange, engineStatus, engineBody));
        server.start();
    }

    @AfterEach
    void stopStubEngine() {
        server.stop(0);
    }

    @Test
    void translatesTheAudioQualityCodes() {
        assertEquals(AudioQualityException.Reason.TOO_NOISY, qualityReasonFor("{\"code\":\"TOO_NOISY\",\"detail\":\"Too much background noise\"}"));
        assertEquals(AudioQualityException.Reason.NO_BIRD_SIGNAL, qualityReasonFor("{\"code\":\"NO_BIRD_SIGNAL\",\"detail\":\"No bird sound\"}"));
    }

    @Test
    void keepsTheOldDetailWhenTheEngineSendsNoCode() {
        engineStatus = 422;
        engineBody = "{\"detail\":\"Demasiada interferencia de ruido\"}";

        AudioQualityException error = assertThrows(AudioQualityException.class, () -> audioClassifier().classify(request()));

        assertEquals(AudioQualityException.Reason.UNKNOWN, error.reason());
        assertEquals("Demasiada interferencia de ruido", error.getMessage());
    }

    @Test
    void translatesTheEngineUploadLimits() {
        engineStatus = 413;
        engineBody = "{\"code\":\"AUDIO_TOO_LARGE\",\"detail\":\"too large\"}";
        assertEquals(AudioFileRejectedException.Reason.TOO_LARGE,
            assertThrows(AudioFileRejectedException.class, () -> audioClassifier().classify(request())).reason());

        engineStatus = 415;
        engineBody = "{\"code\":\"AUDIO_UNSUPPORTED\",\"detail\":\"unsupported\"}";
        assertEquals(AudioFileRejectedException.Reason.UNSUPPORTED_FORMAT,
            assertThrows(AudioFileRejectedException.class, () -> audioClassifier().classify(request())).reason());
    }

    @Test
    void translatesThePhotoCodes() {
        engineStatus = 503;
        engineBody = "{\"code\":\"PHOTO_DISABLED\",\"detail\":\"disabled\"}";
        assertThrows(PhotoClassificationDisabledException.class, () -> photoClassifier().classify(new byte[] {1}));

        engineStatus = 422;
        engineBody = "{\"code\":\"NO_BIRD_IN_PHOTO\",\"detail\":\"No bird could be identified in the photo\"}";
        assertThrows(NoBirdInPhotoException.class, () -> photoClassifier().classify(new byte[] {1}));
    }

    private AudioQualityException.Reason qualityReasonFor(String body) {
        engineStatus = 422;
        engineBody = body;
        return assertThrows(AudioQualityException.class, () -> audioClassifier().classify(request())).reason();
    }

    private AiProperties properties() {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        AiProperties.Engine engine = new AiProperties.Engine(base, "key", 2000, 0.1, 3, 1000, 0, 10, 1000);
        return new AiProperties(engine, null, null, new AiProperties.Photo(0.3, 3), null);
    }

    private BirdNetHttpClassifier audioClassifier() {
        AudioFileValidator validator = new AudioFileValidator(new AudioUploadProperties(1_048_576));
        return new BirdNetHttpClassifier(properties(), JsonMapper.builder().build(), validator, Duration.ofSeconds(30));
    }

    private BirdPhotoHttpClassifier photoClassifier() {
        return new BirdPhotoHttpClassifier(properties(), JsonMapper.builder().build());
    }

    private ClassificationRequest request() {
        return new ClassificationRequest("http://127.0.0.1:" + server.getAddress().getPort() + "/audio.wav", null, null, null);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
