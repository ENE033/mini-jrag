package org.ene.minijrag.util;

import lombok.extern.slf4j.Slf4j;
import java.util.Arrays;

@Slf4j
public class FileTypeUtil {

    // File signatures (magic numbers)
    private static final byte[] PDF_SIGNATURE = {(byte) 0x25, (byte) 0x50, (byte) 0x44, (byte) 0x46}; // %PDF
    private static final byte[] DOCX_SIGNATURE = {(byte) 0x50, (byte) 0x4B, (byte) 0x03, (byte) 0x04}; // PK..  (ZIP format)
    private static final byte[] DOC_SIGNATURE = {(byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0}; // D0CF11E0 (OLE2)

    public enum FileType {
        PDF,
        DOC,
        DOCX,
        UNKNOWN
    }

    /**
     * Detect file type by checking file signatures
     *
     * @param bytes file content as byte array
     * @return detected file type
     */
    public static FileType detectFileType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return FileType.UNKNOWN;
        }

        byte[] fileSignature = Arrays.copyOfRange(bytes, 0, 4);

        if (Arrays.equals(fileSignature, PDF_SIGNATURE)) {
            log.debug("Detected PDF file");
            return FileType.PDF;
        } else if (Arrays.equals(fileSignature, DOCX_SIGNATURE)) {
            log.debug("Detected DOCX file");
            return FileType.DOCX;
        } else if (Arrays.equals(fileSignature, DOC_SIGNATURE)) {
            log.debug("Detected DOC file");
            return FileType.DOC;
        }

        log.debug("Unknown file type");
        return FileType.UNKNOWN;
    }

    /**
     * Check if byte array contains a PDF file
     */
    public static boolean isPdf(byte[] bytes) {
        return detectFileType(bytes) == FileType.PDF;
    }

    /**
     * Check if byte array contains a DOC file
     */
    public static boolean isDoc(byte[] bytes) {
        return detectFileType(bytes) == FileType.DOC;
    }

    /**
     * Check if byte array contains a DOCX file
     */
    public static boolean isDocx(byte[] bytes) {
        return detectFileType(bytes) == FileType.DOCX;
    }

    /**
     * Check if byte array contains a Word document (DOC or DOCX)
     */
    public static boolean isWordDocument(byte[] bytes) {
        FileType fileType = detectFileType(bytes);
        return fileType == FileType.DOC || fileType == FileType.DOCX;
    }
}
