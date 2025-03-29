package org.ene.minijrag.component.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OllamaChatClient {

    @Value("${ollama.api-url}")
    private String ollamaUri;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(ollamaUri)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .exchangeStrategies(strategies)
                .build();

        log.info("Initialized Ollama client with base URL: {}", ollamaUri);
    }

    /**
     * Chat with the model using message history and advanced parameters
     *
     * @param model    The model name to use
     * @param messages List of message objects with role and content
     * @param options  Advanced model parameters (optional)
     * @param stream   Whether to stream the response (default: false)
     * @param format   Response format (json or JSON schema)
     * @param tools    List of tools for the model to use
     * @param keepAlive Controls how long the model stays loaded in memory
     * @return Mono containing the complete response as JsonNode
     */
    public Mono<JsonNode> chat(String model,
                               List<Map<String, Object>> messages,
                               Map<String, Object> options,
                               Boolean stream,
                               String format,
                               List<Map<String, Object>> tools,
                               String keepAlive) {
        ObjectNode requestBody = objectMapper.createObjectNode();

        // Required parameters
        requestBody.put("model", model);
        requestBody.set("messages", objectMapper.valueToTree(messages));

        // Optional parameters
        if (options != null && !options.isEmpty()) {
            ObjectNode optionsNode = objectMapper.createObjectNode();
            options.forEach((key, value) -> {
                if (value instanceof Integer) {
                    optionsNode.put(key, (Integer) value);
                } else if (value instanceof Double) {
                    optionsNode.put(key, (Double) value);
                } else if (value instanceof Boolean) {
                    optionsNode.put(key, (Boolean) value);
                } else if (value instanceof String) {
                    optionsNode.put(key, (String) value);
                }
            });
            requestBody.set("options", optionsNode);
        }

        if (stream != null) {
            requestBody.put("stream", stream);
        }

        if (format != null) {
            requestBody.put("format", format);
        }

        if (tools != null && !tools.isEmpty()) {
            requestBody.set("tools", objectMapper.valueToTree(tools));
        }

        if (keepAlive != null) {
            requestBody.put("keep_alive", keepAlive);
        }

        log.debug("Sending chat request to model: {} with options: {}", model, options);

        return webClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnSuccess(response -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Received response from model {}: {}", model, response.toPrettyString());
                    }
                    log.info("Successfully received chat response from model: {}", model);
                })
                .onErrorResume(e -> {
                    log.error("Failed to chat with model {}: {}", model, e.getMessage());
                    if (e instanceof WebClientResponseException wcre) {
                        log.error("Response status: {}", wcre.getStatusCode());
                        log.error("Response body: {}", wcre.getResponseBodyAsString());

                        // Create error response object
                        ObjectNode errorResponse = objectMapper.createObjectNode();
                        errorResponse.put("error", true);
                        errorResponse.put("status", wcre.getStatusCode().value());
                        errorResponse.put("message", wcre.getResponseBodyAsString());

                        return Mono.just(errorResponse);
                    }

                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", true);
                    errorResponse.put("message", e.getMessage());
                    return Mono.just(errorResponse);
                });
    }

    /**
     * Simplified chat method with just model and messages
     */
    public Mono<JsonNode> chat(String model, List<Map<String, Object>> messages) {
        return chat(model, messages, null, null, null, null, null);
    }

    /**
     * Chat method with model, messages and streaming option
     */
    public Mono<JsonNode> chat(String model, List<Map<String, Object>> messages, boolean stream) {
        return chat(model, messages, null, stream, null, null, null);
    }

    /**
     * Chat method with model, messages and options
     */
    public Mono<JsonNode> chat(String model, List<Map<String, Object>> messages, Map<String, Object> options) {
        return chat(model, messages, options, null, null, null, null);
    }
}