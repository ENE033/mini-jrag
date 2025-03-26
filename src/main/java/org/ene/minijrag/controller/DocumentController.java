package org.ene.minijrag.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ene.minijrag.req.FileReq;
import org.ene.minijrag.req.SearchReq;
import org.ene.minijrag.resp.VectorDocumentResp;
import org.ene.minijrag.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Interface 1: Process file
     *
     * @param request Request containing file URL and knowledge base name
     * @return Operation result
     */
    @PostMapping("/process")
    public Mono<ResponseEntity<String>> processFile(@RequestBody FileReq request) {
        String fileUrl = request.getFileUrl();
        String knowledgeBaseName = request.getKnowledgeBaseName();

        log.info("Received request to process file, URL: {}, knowledge base: {}", fileUrl, knowledgeBaseName);

        return documentService.processFile(fileUrl, knowledgeBaseName)
                .map(success -> {
                    if (success) {
                        return ResponseEntity.ok("File processed successfully");
                    } else {
                        return ResponseEntity.status(500).body("File processing failed");
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error occurred while processing File", error);
                    return Mono.just(ResponseEntity.status(500).body("Error occurred while processing File: " + error.getMessage()));
                });
    }

    /**
     * Interface 2: Vector query based on search text
     *
     * @param request Request containing search text, return count, knowledge base name, and file name list
     * @return List of similar chunks
     */
    @PostMapping("/search")
    public Mono<ResponseEntity<List<VectorDocumentResp>>> searchSimilarChunks(@RequestBody SearchReq request) {
        String searchText = request.getSearchText();
        int topK = request.getTopK() != null ? request.getTopK() : 5;
        String knowledgeBaseName = request.getKnowledgeBaseName();
        List<String> fileNames = request.getFileNames();
        Float similarity = request.getSimilarity();

        log.info("Received search request, text: {}, topK: {}, knowledge base: {}, file names: {}",
                searchText, topK, knowledgeBaseName, fileNames);

        return documentService.searchSimilarChunks(searchText, topK, knowledgeBaseName, fileNames, similarity)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error occurred during search", error);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    /**
     * Interface 3: Search across multiple knowledge bases
     *
     * @param request Request containing search text, return count, and knowledge base name list
     * @return List of similar chunks
     */
    @PostMapping("/search-across-kb")
    public Mono<ResponseEntity<List<VectorDocumentResp>>> searchAcrossKnowledgeBases(@RequestBody SearchReq request) {
        String searchText = request.getSearchText();
        int topK = request.getTopK() != null ? request.getTopK() : 5;
        List<String> knowledgeBaseNames = request.getKnowledgeBaseNames();

        log.info("Received cross-knowledge base search request, text: {}, topK: {}, knowledge base list: {}",
                searchText, topK, knowledgeBaseNames);

        return documentService.searchAcrossKnowledgeBases(searchText, topK, knowledgeBaseNames)
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error occurred during cross-knowledge base search", error);
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

}
