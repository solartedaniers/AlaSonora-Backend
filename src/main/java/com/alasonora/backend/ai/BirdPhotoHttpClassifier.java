package com.alasonora.backend.ai;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.alasonora.backend.config.AiProperties;
import tools.jackson.databind.ObjectMapper;

/**
 * Adaptador que habla con el mismo motor Python/FastAPI que {@link BirdNetHttpClassifier},
 * pero con su endpoint de fotos: otro modelo, sin tocar el flujo de audio.
 */
@Component
public class BirdPhotoHttpClassifier implements BirdPhotoClassifier {

    private static final Logger log = LoggerFactory.getLogger(BirdPhotoHttpClassifier.class);
    private static final String PHOTO_DISABLED = "PHOTO_DISABLED";
    private final RestClient engineClient;
    private final AiProperties.Engine engineProperties;
    private final AiProperties.Photo photoProperties;
    private final ObjectMapper objectMapper;

    public BirdPhotoHttpClassifier(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.engineProperties = aiProperties.engine();
        this.photoProperties = aiProperties.photo();
        this.objectMapper = objectMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(engineProperties.timeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(engineProperties.timeoutMs()));

        this.engineClient = RestClient.builder()
            .baseUrl(engineProperties.baseUrl())
            .requestFactory(requestFactory)
            .build();
    }

    @Override
    public List<RawPhotoClassificationCandidate> classify(byte[] photoBytes) {
        MultipartBodyBuilder body = new MultipartBodyBuilder();
        body.part("photo", photoBytes).filename("photo.jpg").contentType(MediaType.valueOf("image/jpeg"));
        body.part("min_confidence", photoProperties.minConfidence());
        body.part("max_results", photoProperties.maxResults());

        long start = System.nanoTime();
        try {
            EngineResponse response = engineClient.post()
                .uri("/classify-photo")
                .header("X-Internal-Api-Key", engineProperties.apiKey())
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body.build())
                .retrieve()
                .body(EngineResponse.class);

            if (response == null || response.candidates() == null) return List.of();

            return response.candidates().stream()
                .map(c -> new RawPhotoClassificationCandidate(c.label(), c.confidence() * 100))
                .toList();
        } catch (RestClientResponseException ex) {
            AiEngineCallLog.warnFailure(log, "photo classification", engineProperties.host(), ex, AiEngineCallLog.elapsedMs(start));
            AiEngineError error = AiEngineError.from(ex, objectMapper);
            if (error.is(PHOTO_DISABLED)) throw new PhotoClassificationDisabledException();
            if (ex.getStatusCode() == HttpStatusCode.valueOf(422)) throw new NoBirdInPhotoException(error.detail());
            throw new PhotoClassificationException("Photo classification engine call failed", ex);
        } catch (RestClientException ex) {
            AiEngineCallLog.warnFailure(log, "photo classification", engineProperties.host(), ex, AiEngineCallLog.elapsedMs(start));
            throw new PhotoClassificationException("Photo classification engine call failed", ex);
        }
    }

    private record EngineResponse(List<EngineCandidate> candidates) {
    }

    private record EngineCandidate(String label, double confidence) {
    }
}
