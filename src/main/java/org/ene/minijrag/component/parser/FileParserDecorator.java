package org.ene.minijrag.component.parser;

import lombok.extern.slf4j.Slf4j;
import org.ene.minijrag.component.parser.inc.FileParser;
import org.ene.minijrag.util.FileTypeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
public class FileParserDecorator {

    private final List<FileParser> parsers;

    @Autowired
    public FileParserDecorator(List<FileParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * Parse file content
     *
     * @param fileBytes file content as byte array
     * @return parsed text content
     */
    public Mono<String> parseFile(byte[] fileBytes) {
        FileTypeUtil.FileType fileType = FileTypeUtil.detectFileType(fileBytes);

        log.info("Parsing file type: {}", fileType);

        return parsers.stream()
                .filter(parser -> parser.supports(fileType))
                .findFirst()
                .map(parser -> parser.parse(fileBytes))
                .orElseGet(() -> Mono.error(
                        new UnsupportedOperationException("Unsupported file type: " + fileType)
                ));
    }
}
