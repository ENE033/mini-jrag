package org.ene.minijrag.component.parser.ocr;

import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

@Component
@Slf4j
public class TesseractManager {

    private static final Path TEMP_DIR;

    // Static initialization block - executed immediately when the class is loaded
    static {
        Path tempDirPath = null;
        try {
            // Create temporary directory
            tempDirPath = Files.createTempDirectory("tessdata_");
            log.info("Created tessdata directory at: {}", tempDirPath);

            // Extract tessdata files
            extractTessdataFromResources(tempDirPath);

        } catch (Exception e) {
            log.error("Failed to initialize tessdata", e);
        }

        TEMP_DIR = tempDirPath;

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(TesseractManager::cleanupTempDir));
    }

    private static void cleanupTempDir() {
        if (TEMP_DIR != null) {
            try (Stream<Path> pathStream = Files.walk(TEMP_DIR)) {
                pathStream
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(file -> {
                            if (!file.delete()) {
                                log.warn("Failed to delete: {}", file);
                            }
                        });
                log.info("Cleaned up temporary tessdata directory: {}", TEMP_DIR);
            } catch (IOException e) {
                log.error("Error cleaning up tessdata directory", e);
            }
        }
    }


    private static void extractTessdataFromResources(Path targetDir) throws IOException {
        ClassLoader classLoader = TesseractManager.class.getClassLoader();
        String[] trainedDataFiles = {"chi_sim.traineddata", "eng.traineddata"};

        for (String file : trainedDataFiles) {
            try (InputStream is = classLoader.getResourceAsStream("tessdata/" + file)) {
                if (is == null) {
                    log.warn("Resource not found: tessdata/{}", file);
                    continue;
                }

                Files.copy(is, targetDir.resolve(file), StandardCopyOption.REPLACE_EXISTING);
                log.debug("Extracted: {} to {}", file, targetDir);
            }
        }
        log.info("Tessdata files extracted successfully");
    }

    // Create and configure Tesseract instance
    public static ITesseract createTesseractInstance() {
        if (TEMP_DIR == null) {
            throw new IllegalStateException("Tessdata initialization failed");
        }

        ITesseract tesseract = new Tesseract();
        tesseract.setDatapath(TEMP_DIR.toString());
        tesseract.setLanguage("chi_sim+eng");
        tesseract.setOcrEngineMode(3);
        tesseract.setPageSegMode(6);

        return tesseract;
    }
}
