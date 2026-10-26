package com.alasonora.backend.translation;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.alasonora.backend.config.SpeciesTranslationProperties;
import tools.jackson.databind.JsonNode;

/** Traduzco al español consultando Wikidata en vivo por el nombre científico (P225), sin diccionario local. */
@Component
public class WikidataSpeciesCommonNameTranslator implements SpeciesCommonNameTranslator {

    private static final Logger log = LoggerFactory.getLogger(WikidataSpeciesCommonNameTranslator.class);

    private final RestClient restClient;

    public WikidataSpeciesCommonNameTranslator(SpeciesTranslationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(properties.timeoutMs()));
        requestFactory.setReadTimeout(Duration.ofMillis(properties.timeoutMs()));

        // La política de Wikimedia pide un User-Agent con forma de contacto: sin ella puede limitar o
        // rechazar las consultas que salen de servidores en la nube.
        this.restClient = RestClient.builder()
            .baseUrl(properties.endpoint())
            .requestFactory(requestFactory)
            .defaultHeader("User-Agent", properties.userAgent())
            .build();
    }

    @Override
    public Optional<String> translateToSpanish(String scientificName) {
        try {
            // Paso la query SPARQL como variable {query}: si la inserto en la plantilla, sus llaves rompen el UriBuilder.
            JsonNode response = restClient.get()
                .uri("?query={query}&format=json", sparqlQuery(scientificName))
                .retrieve()
                .body(JsonNode.class);
            return extractName(response, scientificName);
        } catch (RestClientException ex) {
            // Falla de red, timeout o límite de Wikidata: el llamador deja el nombre vacío y lo
            // reintenta más tarde. Lo registro para poder ver la causa en los logs de producción.
            log.warn("Wikidata Spanish name lookup failed for {}: {}", scientificName, ex.getMessage());
            return Optional.empty();
        }
    }

    private String sparqlQuery(String scientificName) {
        String escaped = scientificName.replace("\"", "\\\"");
        // Uso P1843 (prefiero "es" sobre es-*) y, de respaldo, la etiqueta en español si no es el propio nombre científico.
        return """
            SELECT ?common ?commonLang ?label WHERE {
              ?taxon wdt:P225 "%s".
              OPTIONAL {
                ?taxon wdt:P1843 ?common.
                BIND(LANG(?common) AS ?commonLang)
                FILTER(STRSTARTS(?commonLang, "es"))
              }
              OPTIONAL { ?taxon rdfs:label ?label. FILTER(LANG(?label) = "es") }
            }
            ORDER BY DESC(?commonLang = "es")
            LIMIT 1
            """.formatted(escaped);
    }

    private Optional<String> extractName(JsonNode response, String scientificName) {
        if (response == null) return Optional.empty();
        JsonNode bindings = response.path("results").path("bindings");
        if (!bindings.isArray() || bindings.isEmpty()) return Optional.empty();

        JsonNode row = bindings.get(0);
        Optional<String> common = nonBlank(row.path("common").path("value").asString(null));
        if (common.isPresent()) return common;
        return nonBlank(row.path("label").path("value").asString(null))
            .filter(label -> !label.equalsIgnoreCase(scientificName));
    }

    private static Optional<String> nonBlank(String value) {
        if (value == null) return Optional.empty();
        String text = value.strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }
}
