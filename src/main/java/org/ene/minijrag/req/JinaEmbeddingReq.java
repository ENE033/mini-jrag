package org.ene.minijrag.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class JinaEmbeddingReq {
    private String model;
    private String task;
    
    @JsonProperty("late_chunking")
    private boolean lateChunking;
    
    private Integer dimensions;
    
    @JsonProperty("embedding_type")
    private String embeddingType;
    
    private List<String> input;
}
