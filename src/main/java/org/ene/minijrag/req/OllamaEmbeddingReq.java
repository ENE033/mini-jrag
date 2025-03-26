package org.ene.minijrag.req;

import lombok.Data;

/**
 * Ollama Embedding Request class
 */
@Data
public class OllamaEmbeddingReq {
    private String model;
    private String prompt;

    public OllamaEmbeddingReq(String model, String prompt) {
        this.model = model;
        this.prompt = prompt;
    }
}
