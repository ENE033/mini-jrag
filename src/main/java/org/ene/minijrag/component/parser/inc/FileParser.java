package org.ene.minijrag.component.parser.inc;

import org.ene.minijrag.util.FileTypeUtil.FileType;
import reactor.core.publisher.Mono;

public interface FileParser {
    /**
     * Parse file content
     *
     * @param fileBytes file content as byte array
     * @return parsed text content
     */
    Mono<String> parse(byte[] fileBytes);

    /**
     * Check if this parser supports the specified file type
     *
     * @param fileType file type
     * @return true if supported, false otherwise
     */
    boolean supports(FileType fileType);
}
