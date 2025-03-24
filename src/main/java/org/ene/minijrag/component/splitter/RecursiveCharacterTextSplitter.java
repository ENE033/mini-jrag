package org.ene.minijrag.component.splitter;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.ene.minijrag.component.splitter.inc.TextSplitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Recursive Character Text Splitter
 * Splits text by recursively trying different separators
 */
@Slf4j
@Builder
@Data
public class RecursiveCharacterTextSplitter implements TextSplitter {

    /**
     * List of separators, sorted by priority from high to low
     */
    private List<String> separators;

    /**
     * Whether to use regular expressions as separators
     */
    private boolean isSeparatorRegex;

    /**
     * How to keep separators
     */
    private SeparatorPosition keepSeparator;

    /**
     * Function to measure text length
     */
    private Function<String, Integer> lengthFunction;

    /**
     * Whether to remove whitespace from the beginning and end of text
     */
    private boolean stripWhitespace;

    /**
     * Language enumeration
     */
    public enum Language {
        PYTHON, JAVA, JS, TS, GO, RUST, CPP, C, RUBY,
        PHP, MARKDOWN, HTML, LATEX, CSHARP, COBOL,
        KOTLIN, SCALA, SWIFT, PROTO, RST, SOL, LUA,
        HASKELL, ELIXIR, POWERSHELL
    }

    /**
     * Separator position enumeration
     */
    public enum SeparatorPosition {
        START,  // Place separator at the beginning of each chunk
        END,    // Place separator at the end of each chunk
        NONE;   // Do not keep separators
    }

    /**
     * Implements the split method from TextSplitter interface
     */
    @Override
    public List<String> split(String text, int chunkSize, int overlapSize) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }
        return splitTextRecursively(text, this.separators, chunkSize, overlapSize);
    }

    /**
     * Recursively splits text
     *
     * @param text        Text to be split
     * @param separators  List of separators
     * @param chunkSize   Chunk size
     * @param overlapSize Overlap size
     * @return List of text chunks after splitting
     */
    private List<String> splitTextRecursively(String text, List<String> separators, int chunkSize, int overlapSize) {
        // Keep original implementation unchanged
        List<String> finalChunks = new ArrayList<>();

        // Get the appropriate separator
        String separator = separators.getLast();
        List<String> newSeparators = Collections.emptyList();

        // Find the first separator that exists in the text
        for (int i = 0; i < separators.size(); i++) {
            String s = separators.get(i);
            String regex = isSeparatorRegex ? s : Pattern.quote(s);

            if (s.isEmpty()) {
                separator = s;
                break;
            }

            if (Pattern.compile(regex).matcher(text).find()) {
                separator = s;
                newSeparators = i + 1 < separators.size()
                        ? separators.subList(i + 1, separators.size())
                        : Collections.emptyList();
                break;
            }
        }

        // Split the text using the found separator
        String regex = isSeparatorRegex ? separator : Pattern.quote(separator);
        List<String> splits = splitTextWithRegex(text, regex, keepSeparator);

        // Process the text fragments after splitting
        List<String> goodSplits = new ArrayList<>();
        String joinSeparator = keepSeparator != SeparatorPosition.NONE ? "" : separator;

        for (String s : splits) {
            // If the fragment is smaller than the chunk size, add it to goodSplits
            if (lengthFunction.apply(s) < chunkSize) {
                goodSplits.add(s);
            } else {
                // If there are accumulated goodSplits, merge them
                if (!goodSplits.isEmpty()) {
                    List<String> mergedText = mergeSplits(goodSplits, joinSeparator, chunkSize, overlapSize);
                    finalChunks.addAll(mergedText);
                    goodSplits.clear();
                }

                // Process large fragments
                if (newSeparators.isEmpty()) {
                    finalChunks.add(s);
                } else {
                    // Process recursively with next level separators
                    List<String> recursiveChunks = splitTextRecursively(s, newSeparators, chunkSize, overlapSize);
                    finalChunks.addAll(recursiveChunks);
                }
            }
        }

        // Process remaining goodSplits
        if (!goodSplits.isEmpty()) {
            List<String> mergedText = mergeSplits(goodSplits, joinSeparator, chunkSize, overlapSize);
            finalChunks.addAll(mergedText);
        }

        return finalChunks;
    }

    /**
     * Split text using regular expressions
     *
     * @param text           Text to be split
     * @param separatorRegex Separator regular expression
     * @param keepSeparator  How to keep separators
     * @return List of text fragments after splitting
     */
    private List<String> splitTextWithRegex(
            String text,
            String separatorRegex,
            SeparatorPosition keepSeparator
    ) {
        // Keep original implementation unchanged
        if (separatorRegex.isEmpty()) {
            // If the separator is empty, split by characters
            return text.chars()
                    .mapToObj(c -> String.valueOf((char) c))
                    .collect(Collectors.toList());
        }

        if (keepSeparator == SeparatorPosition.NONE) {
            // Do not keep separators
            return Arrays.stream(text.split(separatorRegex, -1))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } else {
            // Need to keep separators
            Pattern pattern = Pattern.compile("(" + separatorRegex + ")");
            String[] splits = pattern.split(text, -1);
            List<String> result = new ArrayList<>();

            // Find all matching separators
            List<String> separators = new ArrayList<>();
            java.util.regex.Matcher matcher = pattern.matcher(text);
            while (matcher.find()) {
                separators.add(matcher.group(1));
            }

            // Combine text fragments and separators based on keepSeparator position
            if (keepSeparator == SeparatorPosition.END) {
                // Separator at the end: [text][sep], [text][sep], ...
                for (int i = 0; i < splits.length; i++) {
                    if (!splits[i].isEmpty()) {
                        StringBuilder chunk = new StringBuilder(splits[i]);
                        if (i < separators.size()) {
                            chunk.append(separators.get(i));
                        }
                        result.add(chunk.toString());
                    }
                }
            } else if (keepSeparator == SeparatorPosition.START) {
                // Separator at the beginning: [sep][text], [sep][text], ...
                if (!splits[0].isEmpty()) {
                    result.add(splits[0]);
                }

                for (int i = 0; i < separators.size(); i++) {
                    if (i + 1 < splits.length && !splits[i + 1].isEmpty()) {
                        result.add(separators.get(i) + splits[i + 1]);
                    } else if (i + 1 >= splits.length) {
                        result.add(separators.get(i));
                    }
                }
            }

            return result.stream()
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
    }

    /**
     * Merge text fragments after splitting
     *
     * @param splits      List of text fragments
     * @param separator   Joining separator
     * @param chunkSize   Chunk size
     * @param overlapSize Overlap size
     * @return List of merged text chunks
     */
    private List<String> mergeSplits(List<String> splits, String separator, int chunkSize, int overlapSize) {
        // Keep original implementation unchanged
        if (splits.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> docs = new ArrayList<>();
        List<String> currentDoc = new ArrayList<>();
        int total = 0;
        int separatorLength = lengthFunction.apply(separator);

        for (String split : splits) {
            int len = lengthFunction.apply(split);

            // Check if adding this fragment would exceed the size limit of the current chunk
            if (total + len + (!currentDoc.isEmpty() ? separatorLength : 0) > chunkSize) {
                if (total > chunkSize) {
                    log.warn("Created a chunk of size {}, which is longer than the specified {}",
                            total, chunkSize);
                }

                // If there are accumulated document fragments, merge them
                if (!currentDoc.isEmpty()) {
                    String doc = joinDocs(currentDoc, separator);
                    if (doc != null) {
                        docs.add(doc);
                    }

                    // Handle overlap: keep enough content to meet the overlap requirement
                    while (total > overlapSize ||
                            (total + len + (!currentDoc.isEmpty() ? separatorLength : 0) > chunkSize && total > 0)) {

                        total -= lengthFunction.apply(currentDoc.getFirst());
                        total -= currentDoc.size() > 1 ? separatorLength : 0;
                        currentDoc.removeFirst();
                    }
                }
            }

            // Add the current fragment
            currentDoc.add(split);
            total += len + (currentDoc.size() > 1 ? separatorLength : 0);
        }

        // Process the last chunk
        String doc = joinDocs(currentDoc, separator);
        if (doc != null) {
            docs.add(doc);
        }

        return docs;
    }

    /**
     * Join document fragments
     *
     * @param docs      List of document fragments
     * @param separator Separator
     * @return Joined text
     */
    private String joinDocs(List<String> docs, String separator) {
        String text = String.join(separator, docs);

        if (stripWhitespace) {
            text = text.trim();
        }

        return text.isEmpty() ? null : text;
    }

    /**
     * Get separator list for a specific language
     *
     * @param language Programming language
     * @return List of separators
     */
    public static List<String> getSeparatorsForLanguage(Language language) {
        // Keep original implementation unchanged
        return switch (language) {
            case PYTHON -> Arrays.asList(
                    "\nclass ", "\ndef ", "\n\tdef ",
                    "\n\n", "\n", " ", ""
            );
            case JAVA -> Arrays.asList(
                    "\nclass ", "\npublic ", "\nprotected ", "\nprivate ", "\nstatic ",
                    "\nif ", "\nfor ", "\nwhile ", "\nswitch ", "\ncase ",
                    "\n\n", "\n", " ", ""
            );
            case JS, TS -> Arrays.asList(
                    "\nfunction ", "\nconst ", "\nlet ", "\nvar ", "\nclass ",
                    "\nif ", "\nfor ", "\nwhile ", "\nswitch ", "\ncase ", "\ndefault ",
                    "\n\n", "\n", " ", ""
            );
            // Other languages retain original implementation...
            default ->
                // Default separators
                    Arrays.asList("\n\n", "\n", " ", "");
        };
    }

}
