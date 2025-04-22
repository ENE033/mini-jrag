package org.ene.minijrag.component.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class OllamaChatClient {

    @Value("${ollama.api-url}")
    private String ollamaUri;

    private WebClient webClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${proxy.host:127.0.0.1}")
    private String proxyHost;

    @Value("${proxy.port:7890}")
    private int proxyPort;

    @PostConstruct
    public void init() {
        HttpClient httpClient = HttpClient.create()
                .proxy(proxy -> proxy.type(ProxyProvider.Proxy.HTTP)
                        .host(proxyHost)
                        .port(proxyPort));

        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
                .build();

        this.webClient = WebClient.builder()
                .baseUrl(ollamaUri)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .exchangeStrategies(strategies)
                .build();

        log.info("Initialized Ollama client with base URL: {} and proxy: {}:{}", ollamaUri, proxyHost, proxyPort);
    }


    /**
     * Chat with the model using message history and advanced parameters
     * <pre>
     * Parameter         Description                                                                 Value Type   Example Usage
     * ----------------------------------------------------------------------------------------------------------
     * mirostat          Enable Mirostat sampling for controlling perplexity.                       int          mirostat 0
     *                   (default: 0, 0 = disabled, 1 = Mirostat, 2 = Mirostat 2.0)
     *
     * mirostat_eta      Influences how quickly the algorithm responds to feedback                  float        mirostat_eta 0.1
     *                   from the generated text. A lower learning rate will result in slower
     *                   adjustments, while a higher learning rate will make the algorithm
     *                   more responsive. (Default: 0.1)
     *
     * mirostat_tau      Controls the balance between coherence and diversity of the output.        float        mirostat_tau 5.0
     *                   A lower value will result in more focused and coherent text. (Default: 5.0)
     *
     * num_ctx           Sets the size of the context window used to generate the next token.       int          num_ctx 4096
     *                   (Default: 2048)
     *
     * repeat_last_n     Sets how far back for the model to look back to prevent repetition.         int          repeat_last_n 64
     *                   (Default: 64, 0 = disabled, -1 = num_ctx)
     *
     * repeat_penalty    Sets how strongly to penalize repetitions.                                 float        repeat_penalty 1.1
     *                   A higher value (e.g., 1.5) will penalize repetitions more strongly,
     *                   while a lower value (e.g., 0.9) will be more lenient. (Default: 1.1)
     *
     * temperature       The temperature of the model. Increasing the temperature                  float        temperature 0.7
     *                   will make the model answer more creatively. (Default: 0.8)
     *
     * seed              Sets the random number seed to use for generation.                        int          seed 42
     *                   Setting this to a specific number will make the model generate the
     *                   same text for the same prompt. (Default: 0)
     *
     * stop              Sets the stop sequences to use.                                           string       stop "AI assistant:"
     *                   When this pattern is encountered, the LLM will stop generating text
     *                   and return. Multiple stop patterns may be set by specifying multiple
     *                   separate stop parameters in a modelfile.
     *
     * num_predict       Maximum number of tokens to predict when generating text.                 int          num_predict 42
     *                   (Default: -1, infinite generation)
     *
     * top_k             Reduces the probability of generating nonsense.                           int          top_k 40
     *                   A higher value (e.g., 100) will give more diverse answers, while a
     *                   lower value (e.g., 10) will be more conservative. (Default: 40)
     *
     * top_p             Works together with top-k. A higher value (e.g., 0.95) will lead          float        top_p 0.9
     *                   to more diverse text, while a lower value (e.g., 0.5) will generate
     *                   more focused and conservative text. (Default: 0.9)
     *
     * min_p             Alternative to the top_p, and aims to ensure a balance of quality         float        min_p 0.05
     *                   and variety. The parameter p represents the minimum probability for
     *                   a token to be considered, relative to the probability of the most
     *                   likely token. For example, with p=0.05 and the most likely token
     *                   having a probability of 0.9, logits with a value less than 0.045
     *                   are filtered out. (Default: 0.0)
     * </pre>
     *
     * @param model     The model name to use
     * @param messages  List of message objects with role and content
     * @param options   Advanced model parameters (optional). Below are the supported parameters:
     * @param stream    Whether to stream the response (default: false)
     * @param format    Response format (json or JSON schema)
     * @param tools     List of tools for the model to use
     * @param keepAlive Controls how long the model stays loaded in memory
     * @return Mono containing the complete response as JsonNode
     */
    public Mono<JsonNode> chat(String model,
                               List<Map<String, Object>> messages,
                               Map<String, Object> options,
                               Boolean stream,
                               String format,
                               List<Map<String, Object>> tools,
                               String keepAlive,
                               int depth) {
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

        log.info("Sending chat request to model: {} with options: {}", model, options);

        return webClient.post()
                .uri("/api/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(response -> handleFunctionCall(model, messages, response, options, stream, format, keepAlive, depth)) // 处理 function call
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
     * Handle function call logic
     *
     * @param model     The model name
     * @param messages  The current message history
     * @param response  The response from the chat API
     * @param options   Advanced model parameters (optional)
     * @param stream    Whether to stream the response (default: false)
     * @param format    Response format (json or JSON schema)
     * @param keepAlive Controls how long the model stays loaded in memory
     * @param depth     The current recursion depth
     * @return Mono containing the updated response
     */
    private Mono<JsonNode> handleFunctionCall(String model, List<Map<String, Object>> messages, JsonNode response,
                                              Map<String, Object> options, Boolean stream, String format, String keepAlive, int depth) {
        // Check recursion depth
        if (depth >= 5) {
            log.warn("Maximum recursion depth reached. Stopping function call handling.");
            return Mono.just(response);
        }

        // Check for tool calls
        JsonNode toolCalls = response.path("message").path("tool_calls");
        if (!toolCalls.isArray() || toolCalls.isEmpty()) {
            return Mono.just(response);
        }

        List<Mono<Map<String, Object>>> functionResults = new ArrayList<>();

        // Process ALL tool calls, not just the first one
        for (JsonNode toolCall : toolCalls) {
            String functionName = toolCall.path("function").path("name").asText();
            JsonNode arguments = toolCall.path("function").path("arguments");

            // Add to results based on function name
            switch (functionName) {
                case "get_random_user":
                    functionResults.add(callRandomUserApi(arguments)
                            .map(result -> Map.of(
                                    "role", "tool", // Use proper role for tool responses
                                    "content", result.toString()
                            )));
                    break;
                default:
                    log.warn("Unknown function call: {}", functionName);
                    functionResults.add(Mono.just(Map.of(
                            "role", "tool",
                            "content", "Function " + functionName + " not implemented"
                    )));
            }
        }

        // Combine all function results and continue the conversation
        return Flux.fromIterable(functionResults)
                .flatMap(mono -> mono)
                .collectList()
                .flatMap(results -> {
                    messages.addAll(results);
                    return chat(model, messages, options, stream, format, null, keepAlive, depth + 1);
                });
    }


    /**
     * Simulate an API call to get random user data
     *
     * @param arguments The arguments for the API call
     * @return Mono containing the API response
     */
    private Mono<JsonNode> callRandomUserApi(JsonNode arguments) {
        String gender = arguments.path("gender").asText();
        String nationality = arguments.path("nationality").asText();

        String apiUrl = String.format("https://randomuser.me/api/?gender=%s&nat=%s", gender, nationality);
        log.info("Calling Random User API: {}", apiUrl);

        return webClient.get()
                .uri(apiUrl)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .doOnSuccess(response -> log.info("Received response from Random User API: {}", response))
                .doOnError(e -> log.error("Failed to call Random User API: {}", e.getMessage()));
    }
}
