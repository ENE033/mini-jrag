package org.ene.minijrag.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.io.FilenameUtils;
import org.ene.minijrag.client.ElasticsearchClient;
import org.ene.minijrag.client.JinaClient;
import org.ene.minijrag.component.parser.FileParserDecorator;
import org.ene.minijrag.component.splitter.TextSplitterFactory;
import org.ene.minijrag.req.VectorDocumentReq;
import org.ene.minijrag.resp.JinaEmbeddingResp;
import org.ene.minijrag.resp.VectorDocumentResp;
import org.ene.minijrag.util.DownloadUtil;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final FileParserDecorator fileParserDecorator;
    private final ElasticsearchClient elasticsearchVectorService;
    private final JinaClient jinaClient;

    private static final int CHUNK_SIZE = 500; // Maximum number of characters per text chunk
    private static final int OVERLAP_SIZE = 50; // Number of overlapping characters between text chunks

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

                    // Call JinaClient for batch vectorization
                    return jinaClient.getEmbeddings(chunks)
                            .flatMap(response -> {
                                // Parse JinaEmbeddingResp
                                List<JinaEmbeddingResp.EmbeddingData> embeddingDataList = response.getData();

                                if (embeddingDataList.size() != chunks.size()) {
                                    log.error("Number of vectorization results does not match number of chunks");
                                    return Mono.error(new RuntimeException("Number of vectorization results does not match number of chunks"));
                                }

                                // Build vector documents and store in vector database
                                List<Mono<Boolean>> storeOperations = new ArrayList<>();
                                for (int i = 0; i < chunks.size(); i++) {
                                    String chunk = chunks.get(i);
                                    List<Float> vector = embeddingDataList.get(i).getEmbedding();

                                    // Build vector document
                                    VectorDocumentReq document = new VectorDocumentReq();
                                    document.setContent(chunk);
                                    document.setVector(vector);
                                    document.setFileName(FilenameUtils.getName(fileUrl)); // Set file name

                                    // Store in vector database
                                    Mono<Boolean> storeOperation = elasticsearchVectorService.storeVectorDocument(indexName, document);
                                    storeOperations.add(storeOperation);
                                }

                                // Wait for all storage operations to complete
                                return Mono.when(storeOperations)
                                        .then(Mono.just(true))
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
    public Mono<List<VectorDocumentResp>> searchSimilarChunks(String searchText, int topK, String knowledgeBaseName, List<String> fileNames) {
        // Call JinaClient for vectorization
        return jinaClient.getEmbedding(searchText)
                .flatMap(response -> {
                    // Parse JinaEmbeddingResp, get query vector
                    List<Float> queryVector = response.getData().getFirst().getEmbedding();

                    // Dynamically generate index name
                    String indexName = knowledgeBaseName != null ? knowledgeBaseName + "_index" : "_all";

                    // Define numCandidates (number of candidates to return from each shard)
                    int numCandidates = Math.min(10_000, (int) (1.5 * topK)); // Default value is 1.5 * topK, maximum value is 10,000

                    // Define filter condition
                    ObjectNode filter = null;
                    if (fileNames != null && !fileNames.isEmpty()) {
                        filter = elasticsearchVectorService.buildFileNameFilter(fileNames);
                    }

                    // Query most similar chunks in vector database
                    return elasticsearchVectorService.searchSimilarVectors(indexName, queryVector, topK, numCandidates, filter, null)
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
        // Call JinaClient for vectorization
        return jinaClient.getEmbedding(searchText)
                .flatMap(response -> {
                    // Parse JinaEmbeddingResp, get query vector
                    List<Float> queryVector = response.getData().getFirst().getEmbedding();

                    // Dynamically generate list of index names
                    List<String> indexNames = new ArrayList<>();
                    for (String knowledgeBaseName : knowledgeBaseNames) {
                        indexNames.add(knowledgeBaseName + "_index");
                    }

                    // Query most similar chunks across multiple knowledge bases
                    return elasticsearchVectorService.searchAcrossKnowledgeBases(indexNames, queryVector, topK)
                            .doOnSuccess(results -> log.info("Cross-knowledge base search completed, found {} similar chunks", results.size()))
                            .doOnError(error -> log.error("Cross-knowledge base search failed", error));
                });
    }
}
