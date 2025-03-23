package org.ene.minijrag.client;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.ene.minijrag.req.JinaEmbeddingReq;
import org.ene.minijrag.resp.JinaEmbeddingResp;
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
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class JinaClient {

    /**
     * Jina API key
     */
    @Value("${jina.api-key}")
    private String apiKey;

    /**
     * Jina API URL
     */
    @Value("${jina.api-url}")
    private String apiUrl;

    /**
     * Default model name
     */
    private static final String DEFAULT_MODEL = "jina-embeddings-v3";

    /**
     * Default task
     */
    private static final String DEFAULT_TASK = "text-matching";

    /**
     * Default vector dimension
     */
    private static final int DEFAULT_DIMENSIONS = 1024;

    /**
     * Default embedding vector type
     */
    private static final String DEFAULT_EMBEDDING_TYPE = "float";

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
                .baseUrl(apiUrl) // apiUrl has been injected by now
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey) // Set API key
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * Get text embedding vector
     *
     * @param text Text to be vectorized
     * @return Response containing embedding vector
     */
    public Mono<JinaEmbeddingResp> getEmbedding(String text) {
        return getEmbeddings(Collections.singletonList(text));
    }

    /**
     * Batch get text embedding vectors
     *
     * @param texts List of texts to be vectorized
     * @return Response containing embedding vectors
     */
    public Mono<JinaEmbeddingResp> getEmbeddings(List<String> texts) {
        JinaEmbeddingReq request = JinaEmbeddingReq.builder()
                .model(DEFAULT_MODEL)
                .task(DEFAULT_TASK)
                .lateChunking(false)
                .dimensions(DEFAULT_DIMENSIONS)
                .embeddingType(DEFAULT_EMBEDDING_TYPE)
                .input(texts)
                .build();

        return getEmbeddings(request);
    }

    /**
     * Get embedding vectors using custom request parameters
     *
     * @param request Custom request parameters
     * @return Response containing embedding vectors
     */
    public Mono<JinaEmbeddingResp> getEmbeddings(JinaEmbeddingReq request) {
        return webClient.post()
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> {
                                    log.error("Error from Jina API: {}, Status: {}", error, response.statusCode());
                                    return Mono.empty(); // Return empty response to avoid throwing exception
                                })
                )
                .bodyToMono(JinaEmbeddingResp.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(throwable -> {
                            log.warn("Retrying due to error: {}", throwable.getMessage());
                            return true; // Always allow retry
                        })
                )
                .doOnError(e -> log.error("Failed to get embeddings from Jina API", e))
                .doOnSuccess(response -> log.debug("Successfully got embeddings from Jina API"));
    }
}
