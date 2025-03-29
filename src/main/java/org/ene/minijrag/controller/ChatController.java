package org.ene.minijrag.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ene.minijrag.component.chat.OllamaChatClient;
import org.ene.minijrag.resp.VectorDocumentResp;
import org.ene.minijrag.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            return documentService.searchSimilarChunks(searchText, 20, knowledgeBaseName, null, 0.5f)
                    .flatMap(similarChunks -> {
                        if (!similarChunks.isEmpty()) {
                            // Combine retrieved chunks into a single string
                            String retrievedKnowledge = similarChunks.stream()
                                    .map(VectorDocumentResp::getContent)
                                    .collect(Collectors.joining("\n"));

                            log.info("Retrieved knowledge from knowledge base: {}", retrievedKnowledge);

                            // Add retrieved knowledge to system message
                            Map<String, Object> systemMessage = messages.stream()
                                    .filter(message -> "system".equals(message.get("role")))
                                    .findFirst()
                                    .orElse(null);

                            if (systemMessage != null) {
                                String originalContent = (String) systemMessage.get("content");
                                systemMessage.put("content", originalContent + "\n\n### Retrieved Knowledge:\n" + retrievedKnowledge);
                            } else {
                                // If no system message exists, create one
                                messages.add(0, Map.of(
                                        "role", "system",
                                        "content", "You are a helpful AI assistant that provides concise and accurate information.\n\n### Retrieved Knowledge:\n" + retrievedKnowledge
                                ));
                            }
                        } else {
                            log.info("No relevant chunks found in knowledge base: {}", knowledgeBaseName);
                        }

                        // Call the chat API with the updated messages
                        return ollamaChatClient.chat(model, messages, options, stream, format, tools, keepAlive)
                                .map(ResponseEntity::ok)
                                .onErrorResume(error -> {
                                    log.error("Error occurred during advanced chat", error);
                                    ObjectNode errorResponse = objectMapper.createObjectNode();
                                    errorResponse.put("error", true);
                                    errorResponse.put("message", error.getMessage());
                                    return Mono.just(ResponseEntity.internalServerError().body(errorResponse));
                                });
                    });
        } else {
            // If no knowledgeBaseName is provided, directly call the chat API
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
}
