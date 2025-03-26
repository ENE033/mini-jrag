package org.ene.minijrag.resp;

import lombok.Data;

import java.util.List;

/**
 * Ollama Embedding Response class
 */
@Data
public class OllamaEmbeddingResp {
    private List<Float> embedding;
    private String model;
}