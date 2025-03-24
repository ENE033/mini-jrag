package org.ene.minijrag.component.parser;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.ene.minijrag.component.parser.ocr.TesseractManager;
import org.ene.minijrag.util.DownloadUtil;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class PdfParser {

    // Thread pool configuration
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors() / 2;
    private static final int MAX_POOL_SIZE = CORE_POOL_SIZE;
    private static final long KEEP_ALIVE_TIME = 0;
    private static final int QUEUE_CAPACITY = 10000000;

    // Create thread pool
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE_TIME,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "ocr-worker-" + counter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    // Create Reactor scheduler from existing ExecutorService
    private static final Scheduler customScheduler = Schedulers.fromExecutorService(EXECUTOR);

    /**
     * Download and parse PDF file
     *
     * @param pdfUrl URL of the PDF file
     * @return Mono<String> containing PDF content
     */
    public Mono<String> downloadAndParsePdf(String pdfUrl) {
        return DownloadUtil.download(pdfUrl)
                .flatMap(this::parsePdfContent); // Parse PDF content
    }

    /**
     * Parse content of PDF file
     *
     * @param pdfBytes Byte array of PDF file
     * @return Mono<String> containing PDF content
     */
    private Mono<String> parsePdfContent(byte[] pdfBytes) {
        try {
            InputStream inputStream = new ByteArrayInputStream(pdfBytes);
            PDDocument document = PDDocument.load(inputStream);
            inputStream.close(); // Close input stream but keep document open

            // Extract text using PDFTextStripper
            PDFTextStripper pdfStripper = new PDFTextStripper();
            String extractedText = pdfStripper.getText(document);

            // Check if extracted text contains actual content
            if (containsActualText(extractedText)) {
                // If text content is extracted, close document and return directly
                document.close();
                log.info("PDF file is text type, extracting text using PDFBox");
                return Mono.just(extractedText);
            } else {
                // If no text content is extracted, use multi-threaded OCR recognition
                log.info("PDF file is image type, extracting text using multi-threaded OCR");
                return extractTextWithReactiveOCR(document, pdfBytes)
                        .doFinally((signalType) -> {
                            // Ensure document is closed after OCR processing
                            try {
                                document.close();
                            } catch (IOException e) {
                                throw new RuntimeException("Error occurred while parsing PDF file", e);
                            }
                        });
            }
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Error occurred while parsing PDF file", e));
        }
    }

    /**
     * Determine if extracted text contains actual content
     *
     * @param text Extracted text
     * @return true if it contains actual content, false otherwise
     */
    private boolean containsActualText(String text) {
        if (text == null || text.isEmpty()) {
            return false; // Text is empty
        }

        // Remove invisible characters (such as newlines, carriage returns, spaces, etc.)
        String visibleText = text.replaceAll("[\\s\\r\\n]+", "");

        // If text still has content after removing invisible characters, it contains actual text
        return !visibleText.isEmpty();
    }

    /**
     * Extract text from PDF file using multi-threaded OCR
     *
     * @param document Main document object
     * @param pdfBytes Original PDF byte array, used to create independent document instances in each thread
     * @return Extracted text content
     */
    private Mono<String> extractTextWithReactiveOCR(PDDocument document, byte[] pdfBytes) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            int totalPages = document.getNumberOfPages();
            log.info("Total PDF pages: {}", totalPages);

            // Create a Mono for each page
            List<Mono<String>> pageMonos = new ArrayList<>(totalPages);

            for (int i = 0; i < totalPages; i++) {
                final int pageIndex = i;

                // Create a Mono for each page and specify execution on the customScheduler
                Mono<String> pageMono = Mono.fromCallable(() -> {
                    // Create an independent PDDocument instance in each thread
                    try (InputStream is = new ByteArrayInputStream(pdfBytes);
                         PDDocument threadLocalDocument = PDDocument.load(is)) {

                        log.debug("Starting to process page {}...", pageIndex + 1);

                        PDFRenderer pdfRenderer = new PDFRenderer(threadLocalDocument);
                        BufferedImage image = pdfRenderer.renderImageWithDPI(pageIndex, 300);

                        ITesseract tesseract = TesseractManager.createTesseractInstance();
                        String ocrText = tesseract.doOCR(image);

                        log.debug("Page {} processing completed", pageIndex + 1);
                        return "Page " + (pageIndex + 1) + " OCR Content:\n" + ocrText;
                    } catch (Exception e) {
                        log.error("Error processing page {}: {}", pageIndex + 1, e.getMessage());
                        return "Page " + (pageIndex + 1) + " Error: " + e.getMessage();
                    }
                }).subscribeOn(customScheduler);

                pageMonos.add(pageMono);
            }

            // Combine all page results
            return Flux.mergeSequential(pageMonos)
                    .collectList()
                    .map(results -> {
                        StringBuilder sb = new StringBuilder();
                        for (String result : results) {
                            sb.append(result).append("\n\n");
                        }
                        return sb.toString();
                    })
                    .doFinally(signalType -> {
                        stopWatch.stop();
                        log.info("Reactive OCR parsing completed, total time: {} seconds",
                                stopWatch.getTotalTimeSeconds());
                    });
        } catch (Exception e) {
            stopWatch.stop();
            log.error("Reactive OCR processing failed, time elapsed: {} seconds",
                    stopWatch.getTotalTimeSeconds(), e);
            return Mono.error(new RuntimeException("Error during reactive OCR parsing", e));
        }
    }
}
