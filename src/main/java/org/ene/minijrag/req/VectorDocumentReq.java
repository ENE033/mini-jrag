package org.ene.minijrag.req;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class VectorDocumentReq {
    private String id;
    private String fileName;
    private String content;
    private List<Float> vector;
    private int chunkOrder;
    private Map<String, Object> metadata;
}
