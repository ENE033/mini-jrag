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
import reactor.core.publisher.Mono;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class PdfParser {

    // Thread pool configuration
    private static final int CORE_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int MAX_POOL_SIZE = CORE_POOL_SIZE * 2;
    private static final long KEEP_ALIVE_TIME = 60L;
    private static final int QUEUE_CAPACITY = 100;

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
                System.out.println("PDF file is text type, extracting text using PDFBox");
                return Mono.just(extractedText);
            } else {
                // If no text content is extracted, use multi-threaded OCR recognition
                System.out.println("PDF file is image type, extracting text using multi-threaded OCR");
                return Mono.fromCallable(() -> {
                    try {
                        return extractTextWithMultiThreadedOCR(document, pdfBytes);
                    } finally {
                        // Ensure document is closed after OCR processing
                        document.close();
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
    private String extractTextWithMultiThreadedOCR(PDDocument document, byte[] pdfBytes) {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        try {
            int totalPages = document.getNumberOfPages();
            System.out.println("Total PDF pages: " + totalPages);

            // Create a thread-safe list to store OCR results for each page
            List<CompletableFuture<String>> futures = new ArrayList<>(totalPages);

            // Create an OCR task for each page
            for (int i = 0; i < totalPages; i++) {
                final int pageIndex = i;

                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    // Create independent PDDocument instance in each thread
                    try (InputStream is = new ByteArrayInputStream(pdfBytes);
                         PDDocument threadLocalDocument = PDDocument.load(is)) {

                        System.out.println("Starting to process page " + (pageIndex + 1) + "...");

                        // Create independent PDFRenderer for current thread
                        PDFRenderer pdfRenderer = new PDFRenderer(threadLocalDocument);

                        // Render PDF page as image
                        BufferedImage image = pdfRenderer.renderImageWithDPI(pageIndex, 300); // 300 DPI resolution

                        // Create Tesseract instance
                        ITesseract tesseract = TesseractManager.createTesseractInstance();

                        // Extract text using Tesseract OCR
                        String ocrText = tesseract.doOCR(image);
                        System.out.println("Page " + (pageIndex + 1) + " processing completed");

                        return "Page " + (pageIndex + 1) + " OCR Content:\n" + ocrText;
                    } catch (Exception e) {
                        System.err.println("Error processing page " + (pageIndex + 1) + ": " + e.getMessage());
                        return "Page " + (pageIndex + 1) + " Error: " + e.getMessage();
                    }
                }, EXECUTOR);

                futures.add(future);
            }

            // Collect OCR results from all pages
            StringBuilder ocrContent = new StringBuilder();

            // Wait for all tasks to complete and collect results
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Collect results in page order
            for (int i = 0; i < futures.size(); i++) {
                try {
                    String pageContent = futures.get(i).get();
                    ocrContent.append(pageContent).append("\n\n");
                } catch (Exception e) {
                    System.err.println("Error getting OCR result for page " + (i + 1) + ": " + e.getMessage());
                    ocrContent.append("Page ").append(i + 1).append(" Error: ").append(e.getMessage()).append("\n\n");
                }
            }

            stopWatch.stop();
            System.out.println("Multi-threaded OCR parsing completed, total time: " + stopWatch.getTotalTimeSeconds() + " seconds");

            return ocrContent.toString();

        } catch (Exception e) {
            stopWatch.stop();
            System.err.println("Multi-threaded OCR processing failed, time elapsed: " + stopWatch.getTotalTimeSeconds() + " seconds");
            throw new RuntimeException("Error occurred during multi-threaded OCR parsing of PDF file", e);
        }
    }
}
