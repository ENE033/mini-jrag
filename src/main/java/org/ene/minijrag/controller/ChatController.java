package org.ene.minijrag.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ene.minijrag.component.chat.OllamaChatClient;
import org.ene.minijrag.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final OllamaChatClient ollamaChatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DocumentService documentService;

    @PostMapping
    public Mono<ResponseEntity<JsonNode>> chat(@RequestBody JsonNode request) {
        String model = request.path("model").asText();
        JsonNode messagesNode = request.path("messages");

        List<Map<String, Object>> messages = objectMapper.convertValue(
                messagesNode,
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
        );

        // Extract optional parameters
        Map<String, Object> options;
        if (!request.path("options").isMissingNode()) {
            options = objectMapper.convertValue(
                    request.path("options"),
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class)
            );
        } else {
            options = null;
        }

        Boolean stream = request.has("stream") ? request.path("stream").asBoolean() : null;
        String format = request.has("format") ? request.path("format").asText() : null;

        List<Map<String, Object>> tools;
        if (!request.path("tools").isMissingNode()) {
            tools = objectMapper.convertValue(
                    request.path("tools"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );
        } else {
            tools = null;
        }

        String keepAlive = request.has("keep_alive") ? request.path("keep_alive").asText() : null;

        // Extract knowledgeBaseName
        String knowledgeBaseName = request.has("knowledgeBase") ? request.path("knowledgeBase").asText() : null;

        log.info("Received advanced chat request for model: {}, with {} messages", model, messages.size());

        // Extract searchText from the last user message
        String searchText = messages.stream()
                .filter(message -> "user".equals(message.get("role")))
                .map(message -> (String) message.get("content"))
                .reduce((first, second) -> second) // Get the last user message
                .orElse(null);

        if (knowledgeBaseName != null && searchText != null) {
            log.info("Searching knowledge base: {} for relevant chunks with searchText: {}", knowledgeBaseName, searchText);

            // Call searchSimilarChunks to retrieve relevant knowledge
            return documentService.searchSimilarChunks(searchText, 5, knowledgeBaseName, null, 0.5f)
                    .flatMap(similarChunks -> {
                        if (!similarChunks.isEmpty()) {
                            log.info("Retrieved {} chunks from knowledge base: {}", similarChunks.size(), knowledgeBaseName);

                            // Log each chunk's details
                            similarChunks.forEach(chunk -> {
                                log.info("Chunk ID: {}, FileName: {}, ChunkOrder: {},Content: {}", chunk.getId(), chunk.getFileName(), chunk.getChunkOrder(), chunk.getContent());
                            });

                            // Add each chunk as a separate system message
                            similarChunks.forEach(chunk -> {
                                messages.add(Map.of(
                                        "role", "system",
                                        "content", "### Retrieved Knowledge:\n" + chunk.getContent()
                                ));
                            });
                        } else {
                            log.info("No relevant chunks found in knowledge base: {}", knowledgeBaseName);
                        }

                        // Call the chat API with the updated messages
                        return callChatApi(model, messages, options, stream, format, tools, keepAlive);
                    });
        } else {
            // If no knowledgeBaseName is provided, directly call the chat API
            return callChatApi(model, messages, options, stream, format, tools, keepAlive);
        }
    }

    /**
     * Helper method to call the chat API
     */
    private Mono<ResponseEntity<JsonNode>> callChatApi(String model,
                                                       List<Map<String, Object>> messages,
                                                       Map<String, Object> options,
                                                       Boolean stream,
                                                       String format,
                                                       List<Map<String, Object>> tools,
                                                       String keepAlive) {
        return ollamaChatClient.chat(model, messages, options, stream, format, tools, keepAlive)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error occurred during advanced chat", error);
                    ObjectNode errorResponse = objectMapper.createObjectNode();
                    errorResponse.put("error", true);
                    errorResponse.put("message", error.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(errorResponse));
                });
    }

}
