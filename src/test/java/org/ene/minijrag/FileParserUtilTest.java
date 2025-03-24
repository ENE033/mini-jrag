package org.ene.minijrag;

import org.ene.minijrag.component.parser.DocParser;
import org.ene.minijrag.component.parser.FileParserDecorator;
import org.ene.minijrag.component.parser.PdfParser;
import org.ene.minijrag.component.parser.inc.FileParser;
import org.ene.minijrag.util.DownloadUtil;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.LockSupport;

public class FileParserUtilTest {
    public static void main(String[] args) {
        List<FileParser> list = Arrays.asList(new DocParser(), new PdfParser());
        FileParserDecorator fileParserDecorator = new FileParserDecorator(list);
        Mono<String> stringMono = DownloadUtil.download("xxxx")
                .flatMap(fileParserDecorator::parseFile);
        String block = stringMono.block();
        System.out.println(block);
        LockSupport.park();
    }
}
