package org.ene.minijrag.req;

import lombok.Data;
import java.util.List;

@Data
public class SearchReq {
    private String searchText;
    private Integer topK;
    private String knowledgeBaseName;
    private List<String> fileNames;
    private List<String> knowledgeBaseNames;
    private Float similarity;
}
