package org.ene.minijrag.req;

import lombok.Data;

@Data
public class PdfReq {
    private String pdfUrl;
    private String knowledgeBaseName = "default";
}
