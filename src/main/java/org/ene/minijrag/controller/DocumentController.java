package org.ene.minijrag.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ene.minijrag.client.ElasticsearchClient;
import org.ene.minijrag.req.PdfReq;
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
     * Interface 1: Process PDF file
     *
     * @param request Request containing PDF URL and knowledge base name
     * @return Operation result
     */
    @PostMapping("/process")
    public Mono<ResponseEntity<String>> processPdf(@RequestBody PdfReq request) {
        String pdfUrl = request.getPdfUrl();
        String knowledgeBaseName = request.getKnowledgeBaseName();

        log.info("Received request to process PDF file, URL: {}, knowledge base: {}", pdfUrl, knowledgeBaseName);

        return documentService.processPdf(pdfUrl, knowledgeBaseName)
                .map(success -> {
                    if (success) {
                        return ResponseEntity.ok("PDF file processed successfully");
                    } else {
                        return ResponseEntity.status(500).body("PDF file processing failed");
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error occurred while processing PDF file", error);
                    return Mono.just(ResponseEntity.status(500).body("Error occurred while processing PDF file: " + error.getMessage()));
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

        log.info("Received search request, text: {}, topK: {}, knowledge base: {}, file names: {}",
                searchText, topK, knowledgeBaseName, fileNames);

        return documentService.searchSimilarChunks(searchText, topK, knowledgeBaseName, fileNames)
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
