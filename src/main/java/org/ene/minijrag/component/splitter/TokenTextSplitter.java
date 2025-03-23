package org.ene.minijrag.component.splitter;

import org.ene.minijrag.util.TikTokenUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TokenTextSplitter implements TextSplitter {

    @Override
    public List<String> split(String text, int maxToken, int overlapTokens) {
        // Implement token-based text splitting

        if (text.isEmpty()) {
            return Collections.emptyList();
        }

        // Encode text into token IDs
        List<Integer> inputIds = TikTokenUtil.encode(text);

        // If the text token count is less than the maximum token count, return directly
        if (inputIds.size() <= maxToken) {
            return List.of(text);
        }

        List<String> chunks = new ArrayList<>();
        int startIdx = 0;

        while (startIdx < inputIds.size()) {
            // Calculate the end index of the current chunk
            int endIdx = Math.min(startIdx + maxToken, inputIds.size());

            // Extract the current token chunk
            List<Integer> chunkIds = inputIds.subList(startIdx, endIdx);

            // Decode tokens to text, remove garbled characters, and add to results
            // Chinese decoding may produce garbled characters that need special handling
            // Remove replacement character "�" (Unicode value \uFFFD)
            String decodedText = TikTokenUtil.decode(chunkIds).replace("\uFFFD", "");
            chunks.add(decodedText);

            // If we've reached the end of the text, exit the loop
            if (endIdx == inputIds.size()) {
                break;
            }

            // Update the starting index, considering overlap
            startIdx += maxToken - overlapTokens;
        }

        return chunks;
    }
}
