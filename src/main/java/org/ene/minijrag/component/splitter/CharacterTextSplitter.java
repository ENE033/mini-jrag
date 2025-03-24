package org.ene.minijrag.component.splitter;

import lombok.extern.slf4j.Slf4j;
import org.ene.minijrag.component.splitter.inc.TextSplitter;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class CharacterTextSplitter implements TextSplitter {

    // Default sentence terminators
    private static final char[] SENTENCE_TERMINATORS = {'.', '!', '?', '\n'};

    /**
     * Split text into overlapping chunks based on character count
     *
     * @param text        The text to be split
     * @param chunkSize   The maximum number of characters per chunk
     * @param overlapSize The number of overlapping characters
     * @return A list of text chunks after splitting
     * @throws IllegalArgumentException If the input parameters are invalid
     */
    @Override
    public List<String> split(String text, int chunkSize, int overlapSize) {
        List<String> chunks = new ArrayList<>();
        int textLength = text.length();

        // If text length is less than chunk size, return directly
        if (textLength <= chunkSize) {
            chunks.add(text);
            return chunks;
        }

        // Calculate minimum chunk size to prevent creating chunks that are too small
        int minChunkSize = (int) (chunkSize * MIN_CHUNK_SIZE_RATIO);
        int startPos = 0;

        while (startPos < textLength) {
            // Calculate the end position of the current chunk
            int endPos = Math.min(startPos + chunkSize, textLength);

            // Try to extend to the end of a sentence to make the chunking more natural
            if (endPos < textLength) {
                int sentenceEndPos = findSentenceEnd(text, endPos, Math.min(endPos + 100, textLength));
                if (sentenceEndPos > endPos) {
                    endPos = sentenceEndPos;
                }
            }

            // Add the current text chunk
            chunks.add(text.substring(startPos, endPos));
            log.debug("Added chunk from char position {} to {}", startPos, endPos);

            // Update the starting position for the next chunk
            int nextStartPos = startPos + Math.max(1, chunkSize - overlapSize);

            // If the next chunk is too small (less than the minimum chunk size), go directly to the end of the text
            if (textLength - nextStartPos < minChunkSize) {
                // If the current chunk already covers the end of the text or only a very small part remains, exit the loop
                if (endPos == textLength || textLength - endPos < minChunkSize) {
                    break;
                }
                // Otherwise create the last chunk (from the appropriate position to the end of the text)
                chunks.add(text.substring(Math.max(endPos - overlapSize, 0), textLength));
                break;
            }

            startPos = nextStartPos;
        }

        return chunks;
    }

    /**
     * Find the end position of a sentence within a specified range
     *
     * @param text  The text
     * @param start The starting position
     * @param end   The ending position
     * @return The found sentence end position, or the starting position if not found
     */
    private int findSentenceEnd(String text, int start, int end) {
        for (int pos = start; pos < end; pos++) {
            char c = text.charAt(pos);
            for (char terminator : SENTENCE_TERMINATORS) {
                if (c == terminator) {
                    return pos + 1;
                }
            }
        }
        return start;  // If no sentence terminator is found, return the starting position
    }
}
