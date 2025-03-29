package org.ene.minijrag.resp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VectorDocumentResp {
    private String id;
    private String content;
    private String fileName;
//    private List<Float> vector;
    private Map<String, Object> metadata;
}
