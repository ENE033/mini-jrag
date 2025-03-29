package org.ene.minijrag.component.embedd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.ene.minijrag.component.embedd.inc.TextVectorizer;
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
    private final ObjectMapper objectMapper = new ObjectMapper();

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

        log.info("Initialized Ollama vectorizer with API URL: {}", apiUrl);
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
        return getEmbedResponse(text)
                .map(response -> {
                    JsonNode embeddings = response.path("embeddings");
                    if (embeddings.isArray() && !embeddings.isEmpty()) {
                        JsonNode firstEmbedding = embeddings.get(0);
                        List<Float> result = new ArrayList<>();
                        for (JsonNode value : firstEmbedding) {
                            result.add(value.floatValue());
                        }
                        return result;
                    }
                    return Collections.emptyList();
                });
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
        if (texts == null || texts.isEmpty()) {
            log.warn("getEmbedding called with null or empty text list. Returning an empty result.");
            return Mono.just(Collections.emptyList());
        }

        log.info("getEmbedding called with {} texts. Starting batch embedding process.", texts.size());

        return getBatchEmbedResponse(texts)
                .doOnSubscribe(subscription -> log.info("Sending batch embedding request for {} texts.", texts.size()))
                .doOnSuccess(response -> log.info("Received embedding response for {} texts.", texts.size()))
                .doOnError(error -> log.error("Failed to get embeddings for {} texts. Error: {}", texts.size(), error.getMessage(), error))
                .map(response -> {
                    JsonNode embeddings = response.path("embeddings");
                    List<List<Float>> result = new ArrayList<>();

                    if (embeddings.isArray()) {
                        log.info("Processing embedding response. Found {} embeddings.", embeddings.size());
                        for (JsonNode embedding : embeddings) {
                            List<Float> vector = new ArrayList<>();
                            for (JsonNode value : embedding) {
                                vector.add(value.floatValue());
                            }
                            result.add(vector);
                        }
                        log.info("Successfully processed {} embeddings.", result.size());
                    } else {
                        log.warn("Embedding response does not contain an array of embeddings. Returning an empty result.");
                    }

                    return result;
                });
    }

    /**
     * Get embedding response for a single text using the /api/embed endpoint
     * <p>
     * API Request Format:
     * <pre>
     * {
     *   "model": "model-name",  // Name of model to generate embeddings from
     *   "input": "text",        // Text to generate embeddings for
     *   "truncate": true,       // Optional: truncate input to fit context length (default: true)
     *   "options": {},          // Optional: additional model parameters
     *   "keep_alive": "5m"      // Optional: how long to keep model loaded (default: 5m)
     * }
     * </pre>
     * <p>
     * API Response Format:
     * <pre>
     * {
     *   "model": "model-name",
     *   "embeddings": [[0.123, 0.456, ...]], // Array of embedding vectors
     *   "total_duration": 12345,             // Processing time in nanoseconds
     *   "load_duration": 1234,               // Model load time in nanoseconds
     *   "prompt_eval_count": 8               // Number of tokens processed
     * }
     * </pre>
     *
     * @param text Text to be vectorized
     * @return JsonNode containing the API response
     */
    public Mono<JsonNode> getEmbedResponse(String text) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", DEFAULT_MODEL);
        requestBody.put("input", text);
        requestBody.put("truncate", true);

        return webClient.post()
                .uri("/api/embed")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> {
                                    log.error("Error from Ollama API: {}, Status: {}", error, response.statusCode());
                                    return Mono.error(new RuntimeException("Error from Ollama API: " + error));
                                })
                )
                .bodyToMono(JsonNode.class)
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
     * Get batch embeddings response using the /api/embed endpoint with array input
     * <p>
     * API Request Format for Batch Processing:
     * <pre>
     * {
     *   "model": "model-name",           // Name of model to generate embeddings from
     *   "input": ["text1", "text2", ...], // Array of texts to generate embeddings for
     *   "truncate": true,                // Optional: truncate input to fit context length (default: true)
     *   "options": {},                   // Optional: additional model parameters
     *   "keep_alive": "5m"               // Optional: how long to keep model loaded (default: 5m)
     * }
     * </pre>
     * <p>
     * API Response Format for Batch Processing:
     * <pre>
     * {
     *   "model": "model-name",
     *   "embeddings": [                  // Array of embedding vectors, one per input text
     *     [0.123, 0.456, ...],           // First text embedding
     *     [0.789, 0.012, ...]            // Second text embedding
     *   ],
     *   "total_duration": 12345,         // Processing time in nanoseconds
     *   "load_duration": 1234,           // Model load time in nanoseconds
     *   "prompt_eval_count": 16          // Number of tokens processed
     * }
     * </pre>
     *
     * @param texts List of texts to be vectorized
     * @return JsonNode containing the API response
     */
    public Mono<JsonNode> getBatchEmbedResponse(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Mono.just(objectMapper.createObjectNode());
        }

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", DEFAULT_MODEL);

        // Create array of input texts
        ArrayNode inputArray = requestBody.putArray("input");
        for (String text : texts) {
            inputArray.add(text);
        }

        requestBody.put("truncate", true);

        return webClient.post()
                .uri("/api/embed")
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .flatMap(error -> {
                                    log.error("Error from Ollama API: {}, Status: {}", error, response.statusCode());
                                    return Mono.error(new RuntimeException("Error from Ollama API: " + error));
                                })
                )
                .bodyToMono(JsonNode.class)
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .filter(throwable -> {
                            log.warn("Retrying due to error: {}", throwable.getMessage());
                            return true; // Always allow retry
                        })
                )
                .doOnError(e -> log.error("Failed to get batch embeddings from Ollama API", e))
                .doOnSuccess(response -> log.debug("Successfully got batch embeddings from Ollama API for {} texts", texts.size()));
    }
}
