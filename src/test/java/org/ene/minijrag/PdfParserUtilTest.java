package org.ene.minijrag;

import org.ene.minijrag.component.parser.PdfParser;
import reactor.core.publisher.Mono;

public class PdfParserUtilTest {
    public static void main(String[] args) {
        PdfParser pdfParser = new PdfParser();
        Mono<String> stringMono = pdfParser.downloadAndParsePdf("xxx");
        String block = stringMono.block();
        System.out.println(block);
    }
}
