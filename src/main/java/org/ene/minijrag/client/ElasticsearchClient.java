package org.ene.minijrag.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.ene.minijrag.req.VectorDocumentReq;
import org.ene.minijrag.resp.VectorDocumentResp;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class ElasticsearchClient {

    /**
     * Elasticsearch connection address
     */
    @Value("${elasticsearch.uris}")
    private String esUri;

    /**
     * Vector dimension
     */
    private static final int VECTOR_DIMENSION = 1024;

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
                .baseUrl(esUri) // esUri has been injected by now
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .exchangeStrategies(strategies)
                .build();
    }

    /**
     * Create vector index
     *
     * @param indexName Index name
     * @return Whether creation was successful
     */
    public Mono<Boolean> createVectorIndex(String indexName) {
        Map<String, Object> indexSettings = new HashMap<>();

        // Set mappings
        Map<String, Object> properties = new HashMap<>();

        // Content field
        Map<String, Object> contentField = new HashMap<>();
        contentField.put("type", "text");
        contentField.put("analyzer", "standard");
        properties.put("content", contentField);

        // Vector field
        Map<String, Object> vectorField = new HashMap<>();
        vectorField.put("type", "dense_vector");
        vectorField.put("dims", VECTOR_DIMENSION);
        vectorField.put("index", true);
        vectorField.put("similarity", "cosine");
        properties.put("vector", vectorField);

        // Metadata field
        Map<String, Object> metadataField = new HashMap<>();
        metadataField.put("type", "object");
        properties.put("metadata", metadataField);

        // Assemble mappings
        Map<String, Object> mappings = new HashMap<>();
        mappings.put("properties", properties);

        // Assemble request body
        indexSettings.put("mappings", mappings);

        return webClient.put()
                .uri("/" + indexName)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(indexSettings)
                .retrieve()
                .toBodilessEntity()
                .map(response -> {
                    log.info("Successfully created vector index: {}", indexName);
                    return true;
                })
                .onErrorResume(e -> {
                    log.error("Failed to create vector index: {}, error: {}", indexName, e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Store vector document to specified index
     *
     * @param indexName Index name
     * @param document  Document containing vector
     * @return Whether storage was successful
     */
    public Mono<Boolean> storeVectorDocument(String indexName, VectorDocumentReq document) {
        // Process document ID
        String documentId = document.getId();
        String endpoint = "/" + indexName + "/_doc";
        if (documentId != null && !documentId.isEmpty()) {
            endpoint += "/" + documentId;
        }

        try {
            String jsonBody = objectMapper.writeValueAsString(document);
            log.debug("Document being sent: {}", jsonBody);
        } catch (Exception ex) {
            log.error("Error serializing document: {}", ex.getMessage());
        }

        return webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(document)
                .retrieve()
                .bodyToMono(String.class)
                .map(body -> {
                    log.info("Successfully stored document to index {}: {}", indexName, body);
                    return true;
                })
                .onErrorResume(e -> {
                    log.error("Failed to store document to index {}: {}", indexName, e.getMessage());
                    if (e instanceof WebClientResponseException wcre) {
                        log.error("Response Body: {}", wcre.getResponseBodyAsString());
                    }
                    return Mono.just(false);
                });
    }

    /**
     * Search documents in specified index based on vector similarity
     *
     * @param indexName     Index name
     * @param queryVector   Query vector
     * @param limit         Number of results to return
     * @param numCandidates Number of candidates to return from each shard
     * @param filter        Filter conditions (optional)
     * @param similarity    Minimum similarity threshold (optional)
     * @return List of similar documents
     */
    public Mono<List<VectorDocumentResp>> searchSimilarVectors(String indexName, List<Float> queryVector, int limit,
                                                               Integer numCandidates, ObjectNode filter, Float similarity) {
        try {
            // Build request body using KNN query
            ObjectNode requestBody = objectMapper.createObjectNode();

            // Create query node and knn node
            ObjectNode queryNode = objectMapper.createObjectNode();
            ObjectNode knnNode = objectMapper.createObjectNode();

            // Set knn query parameters
            knnNode.put("field", "vector"); // Vector field
            knnNode.set("query_vector", objectMapper.valueToTree(queryVector)); // Query vector
            knnNode.put("k", limit); // Number of nearest neighbors to return

            // Set num_candidates
            if (numCandidates != null) {
                knnNode.put("num_candidates", numCandidates);
            }

            // Set filter
            if (filter != null) {
                knnNode.set("filter", filter);
            }

            // Set similarity
            if (similarity != null) {
                knnNode.put("similarity", similarity);
            }

            // Assemble query
            queryNode.set("knn", knnNode);
            requestBody.set("query", queryNode);
            requestBody.put("size", limit);

            return webClient.post()
                    .uri("/" + indexName + "/_search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .map(json -> {
                        List<VectorDocumentResp> results = new ArrayList<>();
                        JsonNode hits = json.path("hits").path("hits");

                        for (JsonNode hit : hits) {
                            try {
                                JsonNode source = hit.path("_source");
                                VectorDocumentResp doc = objectMapper.treeToValue(source, VectorDocumentResp.class);
                                doc.setId(hit.path("_id").asText());
                                results.add(doc);
                            } catch (Exception e) {
                                log.error("Failed to parse search result: {}", e.getMessage());
                            }
                        }

                        return results;
                    })
                    .onErrorResume(e -> {
                        log.error("Vector search failed in index {}: {}", indexName, e.getMessage());
                        return Mono.just(new ArrayList<>());
                    });
        } catch (Exception e) {
            log.error("Failed to build search request: {}", e.getMessage());
            return Mono.just(new ArrayList<>());
        }
    }

    public Mono<List<VectorDocumentResp>> searchAcrossKnowledgeBases(List<String> indexNames, List<Float> queryVector, int topK) {
        String indices = String.join(",", indexNames);

        ObjectNode requestBody = objectMapper.createObjectNode();
        ObjectNode queryNode = objectMapper.createObjectNode();
        ObjectNode knnNode = objectMapper.createObjectNode();

        knnNode.put("field", "vector");
        knnNode.set("query_vector", objectMapper.valueToTree(queryVector));
        knnNode.put("k", topK);

        queryNode.set("knn", knnNode);
        requestBody.set("query", queryNode);
        requestBody.put("size", topK);

        return webClient.post()
                .uri("/" + indices + "/_search")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    List<VectorDocumentResp> results = new ArrayList<>();
                    JsonNode hits = json.path("hits").path("hits");

                    for (JsonNode hit : hits) {
                        try {
                            JsonNode source = hit.path("_source");
                            VectorDocumentResp doc = objectMapper.treeToValue(source, VectorDocumentResp.class);
                            doc.setId(hit.path("_id").asText());
                            results.add(doc);
                        } catch (Exception e) {
                            log.error("Failed to parse search result: {}", e.getMessage());
                        }
                    }

                    return results;
                })
                .onErrorResume(e -> {
                    log.error("Cross-knowledge base search failed: {}", e.getMessage());
                    return Mono.just(new ArrayList<>());
                });
    }


    public ObjectNode buildFileNameFilter(List<String> fileNames) {
        ObjectNode termsNode = objectMapper.createObjectNode();
        ArrayNode fileNameArray = termsNode.putArray("fileName");

        fileNames.forEach(fileNameArray::add);

        ObjectNode filterNode = objectMapper.createObjectNode();
        filterNode.set("terms", termsNode);

        return filterNode;
    }


}
