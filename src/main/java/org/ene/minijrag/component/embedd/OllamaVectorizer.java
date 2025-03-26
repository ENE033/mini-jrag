package org.ene.minijrag.component.embedd;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.ene.minijrag.component.embedd.inc.TextVectorizer;
import org.ene.minijrag.req.OllamaEmbeddingReq;
import org.ene.minijrag.resp.OllamaEmbeddingResp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class OllamaVectorizer implements TextVectorizer {

    /**
     * Ollama API URL
     */
    @Value("${ollama.api-url}")
    private String apiUrl;

    /**
     * Default model name
     */
    private static final String DEFAULT_MODEL = "nomic-embed-text";

    private WebClient webClient;

    /**
     * Initialize WebClient
     */
    @PostConstruct
    public void init() {
        // Increase memory limit to avoid errors with large response bodies
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * Implementation of TextVectorizer interface method
     * Get embedding vector for a single text
     *
     * @param text Text to be vectorized
     * @return List of float values representing the embedding vector
     */
    @Override
    public Mono<List<Float>> getEmbedding(String text) {
        return getEmbeddingResponse(text)
                .map(OllamaEmbeddingResp::getEmbedding);
    }

    /**
     * Implementation of TextVectorizer interface method
     * Get embedding vectors for multiple texts
     *
     * @param texts List of texts to be vectorized
     * @return List of embedding vectors
     */
    @Override
    public Mono<List<List<Float>>> getEmbedding(List<String> texts) {
        return getEmbeddingsResponse(texts)
                .map(responses -> responses.stream()
                        .map(OllamaEmbeddingResp::getEmbedding)
                        .collect(Collectors.toList()));
    }

    /**
     * Get raw embedding response from Ollama API
     *
     * @param text Text to be vectorized
     * @return Full API response
     */
    public Mono<OllamaEmbeddingResp> getEmbeddingResponse(String text) {
        OllamaEmbeddingReq request = new OllamaEmbeddingReq(DEFAULT_MODEL, text);

        return webClient.post()
                .uri("/api/embeddings")
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> {
                                    log.error("Error from Ollama API: {}, Status: {}", error, response.statusCode());
                                    return Mono.error(new RuntimeException("Error from Ollama API: " + error));
                                })
                )
                .bodyToMono(OllamaEmbeddingResp.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(throwable -> {
                            log.warn("Retrying due to error: {}", throwable.getMessage());
                            return true; // Always allow retry
                        })
                )
                .doOnError(e -> log.error("Failed to get embeddings from Ollama API", e))
                .doOnSuccess(response -> log.debug("Successfully got embeddings from Ollama API"));
    }

    /**
     * Get batch embeddings response from Ollama API
     *
     * @param texts List of texts to be vectorized
     * @return List of API responses
     */
    public Mono<List<OllamaEmbeddingResp>> getEmbeddingsResponse(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(Collections.emptyList());
        }

        List<Mono<OllamaEmbeddingResp>> requests = new ArrayList<>();
        for (String text : texts) {
            requests.add(getEmbeddingResponse(text));
        }

        return Mono.zip(requests, responses -> {
            List<OllamaEmbeddingResp> results = new ArrayList<>();
            for (Object response : responses) {
                results.add((OllamaEmbeddingResp) response);
            }
            return results;
        });
    }

}
