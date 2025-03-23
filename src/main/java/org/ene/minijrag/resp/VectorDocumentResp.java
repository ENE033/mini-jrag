package org.ene.minijrag.resp;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class VectorDocumentResp {
    private String id;
    private String content;
    private List<Float> vector;
    private Map<String, Object> metadata;
}
