package com.alasonora.backend.storage;

import java.time.Duration;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.alasonora.backend.config.SupabaseProperties;

/** Firmo URLs de objetos privados con la Storage Admin API, con la misma service_role key que SupabaseAdminClient. */
@Component
public class SupabasePrivateObjectSignedUrlResolver implements PrivateObjectSignedUrlResolver {

    private final RestClient restClient;
    private final String storageBaseUrl;

    public SupabasePrivateObjectSignedUrlResolver(SupabaseProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.storageBaseUrl = properties.url() + "/storage/v1";
        this.restClient = RestClient.builder()
            .baseUrl(storageBaseUrl)
            .requestFactory(requestFactory)
            .defaultHeader("apikey", properties.serviceRoleKey())
            .defaultHeader("Authorization", "Bearer " + properties.serviceRoleKey())
            .build();
    }

    @Override
    public String resolveSignedUrl(String bucket, String storagePath, int ttlSeconds) {
        try {
            // Concateno storagePath porque sale de mi propio patrón de subida; la plantilla de RestClient codificaría la barra como %2F.
            SignedUrlResponse response = restClient.post()
                .uri("/object/sign/" + bucket + "/" + storagePath)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expiresIn", ttlSeconds))
                .retrieve()
                .body(SignedUrlResponse.class);
            if (response == null || response.signedURL() == null) {
                throw new SignedUrlException("Supabase returned no signed URL for " + bucket + "/" + storagePath, null);
            }
            return storageBaseUrl + response.signedURL();
        } catch (RestClientException | IllegalArgumentException ex) {
            // También atrapo IllegalArgumentException: la lanza el cliente si supabase.url no es una URI absoluta.
            throw new SignedUrlException("Could not sign the URL for " + bucket + "/" + storagePath, ex);
        }
    }

    private record SignedUrlResponse(String signedURL) {
    }
}
