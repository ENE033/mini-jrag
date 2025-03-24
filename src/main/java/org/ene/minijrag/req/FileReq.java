package org.ene.minijrag.req;

import lombok.Data;

@Data
public class FileReq {
    private String fileUrl;
    private String knowledgeBaseName = "default";
}
