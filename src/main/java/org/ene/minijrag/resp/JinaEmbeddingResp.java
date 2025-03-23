package org.ene.minijrag.resp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class JinaEmbeddingResp {
    private String model;
    private String object;
    private Usage usage;
    private List<EmbeddingData> data;

    @Data
    public static class Usage {
        @JsonProperty("total_tokens")
        private int totalTokens;

        @JsonProperty("prompt_tokens")
        private int promptTokens;
    }

    @Data
    public static class EmbeddingData {
        private String object;
        private int index;
        private List<Float> embedding;
    }
}
