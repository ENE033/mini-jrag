package org.ene.minijrag.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.io.FilenameUtils;
import org.ene.minijrag.client.ElasticsearchClient;
import org.ene.minijrag.component.embedd.inc.TextVectorizer;
import org.ene.minijrag.component.parser.FileParserDecorator;
import org.ene.minijrag.component.splitter.TextSplitterFactory;
import org.ene.minijrag.req.VectorDocumentReq;
import org.ene.minijrag.resp.VectorDocumentResp;
import org.ene.minijrag.util.DownloadUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class DocumentService {

    @Autowired
    private FileParserDecorator fileParserDecorator;

    @Autowired
    private ElasticsearchClient elasticsearchClient;

    @Autowired
    @Qualifier("jinaVectorizer")
    private TextVectorizer jinaClient;

    @Autowired
    @Qualifier("ollamaVectorizer")
    private TextVectorizer ollamaClient;

    @Value("${embedding.type:Jina}")
    private String embeddingType;

    private TextVectorizer textVectorizer;

    private static final int CHUNK_SIZE = 500; // Maximum number of characters per text chunk
    private static final int OVERLAP_SIZE = 50; // Number of overlapping characters between text chunks

    @PostConstruct
    public void init() {
        // Select TextVectorizer implementation based on configuration
        if ("Ollama".equalsIgnoreCase(embeddingType)) {
            this.textVectorizer = ollamaClient;
            log.info("Using Ollama for text vectorization");
        } else if ("Jina".equalsIgnoreCase(embeddingType)) {
            this.textVectorizer = jinaClient;
            log.info("Using Jina for text vectorization");
        } else {
            log.warn("Invalid embedding.type: '{}'. Defaulting to Jina.", embeddingType);
            this.textVectorizer = jinaClient;
        }
    }

    /**
     * Interface 1: Process file, parse, chunk, vectorize and store in vector database
     *
     * @param fileUrl           URL of the file
     * @param knowledgeBaseName Knowledge base name
     * @return Mono<Boolean> indicating whether the operation was successful
     */
    public Mono<Boolean> processFile(String fileUrl, String knowledgeBaseName) {
        // Dynamically generate index name
        String indexName = knowledgeBaseName + "_index";

        return DownloadUtil.download(fileUrl)
                .flatMap(fileParserDecorator::parseFile)
                .flatMap(parsedText -> {
                    // Chunk text
                    List<String> chunks = TextSplitterFactory.createTokenSplitter().split(parsedText, CHUNK_SIZE, OVERLAP_SIZE);
                    log.info("File text chunking completed, generated {} chunks", chunks.size());

                    // Call TextVectorizer for batch vectorization
                    return textVectorizer.getEmbedding(chunks)
                            .flatMap(embeddings -> {
                                if (embeddings.size() != chunks.size()) {
                                    log.error("Number of vectorization results does not match number of chunks");
                                    return Mono.error(new RuntimeException("Number of vectorization results does not match number of chunks"));
                                }

                                // Build vector document list
                                List<VectorDocumentReq> documents = new ArrayList<>(chunks.size());
                                String fileName = FilenameUtils.getName(fileUrl);

                                for (int i = 0; i < chunks.size(); i++) {
                                    String chunk = chunks.get(i);
                                    List<Float> vector = embeddings.get(i);

                                    // Build vector document
                                    VectorDocumentReq document = new VectorDocumentReq();
                                    document.setContent(chunk);
                                    document.setVector(vector);
                                    document.setFileName(fileName);
                                    document.setChunkOrder(i); // Set chunk order

                                    // Add to bulk list
                                    documents.add(document);
                                }

                                // Bulk store to vector database
                                return elasticsearchClient.storeBulkVectorDocuments(indexName, documents)
                                        .doOnSuccess(success -> log.info("File processing completed, all chunks stored in vector database"))
                                        .doOnError(error -> log.error("File processing failed", error));
                            });
                });
    }


    /**
     * Interface 2: Perform vector query based on search text, return the most similar chunks
     *
     * @param searchText        Search text
     * @param topK              Number of similar chunks to return
     * @param knowledgeBaseName Knowledge base name (optional)
     * @param fileNames         List of file names (optional)
     * @return Mono<List < VectorDocumentResp>> List of similar chunks
     */
    public Mono<List<VectorDocumentResp>> searchSimilarChunks(String searchText, int topK, String knowledgeBaseName, List<String> fileNames, Float similarity) {
        // Call TextVectorizer for vectorization
        return textVectorizer.getEmbedding(searchText)
                .flatMap(queryVector -> {
                    // Dynamically generate index name
                    String indexName = knowledgeBaseName != null ? knowledgeBaseName + "_index" : "_all";

                    // Define numCandidates (number of candidates to return from each shard)
                    int numCandidates = Math.min(10_000, (int) (1.5 * topK)); // Default value is 1.5 * topK, maximum value is 10,000

                    // Define filter condition
                    ObjectNode filter = null;
                    if (fileNames != null && !fileNames.isEmpty()) {
                        filter = elasticsearchClient.buildFileNameFilter(fileNames);
                    }

                    // Query most similar chunks in vector database
                    return elasticsearchClient.searchSimilarVectors(indexName, queryVector, topK, numCandidates, filter, similarity)
                            .doOnSuccess(results -> log.info("Search completed, found {} similar chunks", results.size()))
                            .doOnError(error -> log.error("Search failed", error));
                });
    }

    /**
     * Interface 3: Search across multiple knowledge bases
     *
     * @param searchText         Search text
     * @param topK               Number of similar chunks to return
     * @param knowledgeBaseNames List of knowledge base names
     * @return Mono<List < VectorDocumentResp>> List of similar chunks
     */
    public Mono<List<VectorDocumentResp>> searchAcrossKnowledgeBases(String searchText, int topK, List<String> knowledgeBaseNames) {
        // Call TextVectorizer for vectorization
        return textVectorizer.getEmbedding(searchText)
                .flatMap(queryVector -> {
                    // Dynamically generate list of index names
                    List<String> indexNames = new ArrayList<>();
                    for (String knowledgeBaseName : knowledgeBaseNames) {
                        indexNames.add(knowledgeBaseName + "_index");
                    }

                    // Query most similar chunks across multiple knowledge bases
                    return elasticsearchClient.searchAcrossKnowledgeBases(indexNames, queryVector, topK)
                            .doOnSuccess(results -> log.info("Cross-knowledge base search completed, found {} similar chunks", results.size()))
                            .doOnError(error -> log.error("Cross-knowledge base search failed", error));
                });
    }
}
