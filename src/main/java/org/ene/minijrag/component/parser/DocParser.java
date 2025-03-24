package org.ene.minijrag.component.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.microsoft.OfficeParser;
import org.apache.tika.sax.BodyContentHandler;
import org.ene.minijrag.component.parser.inc.FileParser;
import org.ene.minijrag.util.DownloadUtil;
import org.ene.minijrag.util.FileTypeUtil;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
public class DocParser implements FileParser {

    private final Tika tika = new Tika();

    @Override
    public Mono<String> parse(byte[] fileBytes) {
        return parseWordDocument(fileBytes);
    }

    @Override
    public boolean supports(FileTypeUtil.FileType fileType) {
        return fileType == FileTypeUtil.FileType.DOC ||
                fileType == FileTypeUtil.FileType.DOCX;
    }


    /**
     * Parse Word documents (DOC or DOCX) using Tika
     *
     * @param docBytes document content as byte array
     * @return Mono<String> containing the document content
     */
    public Mono<String> parseWordDocument(byte[] docBytes) {
        return Mono.fromCallable(() -> {
            try (InputStream stream = new ByteArrayInputStream(docBytes)) {
                // Parse using Tika
                String content = tika.parseToString(stream);

                log.info("Word document parsing completed, extracted {} characters", content.length());
                return content;
            } catch (Exception e) {
                log.error("Error occurred while parsing Word document", e);
                throw new RuntimeException("Failed to parse Word document: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Process documents using a specialized Office parser
     * This method provides more control options and can retrieve metadata
     */
    public Mono<String> parseWordDocumentWithMetadata(byte[] docBytes) {
        return Mono.fromCallable(() -> {
            try (InputStream stream = new ByteArrayInputStream(docBytes)) {
                // Create content handler, -1 means no limit on text length
                BodyContentHandler handler = new BodyContentHandler(-1);
                Metadata metadata = new Metadata();
                ParseContext context = new ParseContext();

                // Use Office parser
                new OfficeParser().parse(stream, handler, metadata, context);

                // Log some metadata
                log.info("Document title: {}", metadata.get("title"));
                log.info("Document author: {}", metadata.get("Author"));
                log.info("Document type: {}", metadata.get("Content-Type"));

                String content = handler.toString();
                log.info("Word document parsing completed, extracted {} characters", content.length());
                return content;
            } catch (IOException | SAXException | TikaException e) {
                log.error("Error occurred while parsing Word document", e);
                throw new RuntimeException("Failed to parse Word document: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}