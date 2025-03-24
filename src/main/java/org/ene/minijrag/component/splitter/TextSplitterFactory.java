package org.ene.minijrag.component.splitter;

import org.ene.minijrag.component.splitter.inc.TextSplitter;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

public final class TextSplitterFactory {
    private TextSplitterFactory() { /* Prevent instantiation */ }

    // Factory methods for simple text splitters
    public static TextSplitter createCharacterSplitter() {
        return new CharacterTextSplitter();
    }

    public static TextSplitter createTokenSplitter() {
        return new TokenTextSplitter();
    }

    public static TextSplitter createDefaultRecursiveSplitter() {
        return createRecursiveSplitter(null, false, null, null, true);
    }

    /**
     * Create a recursive character text splitter with custom configuration
     *
     * @param separators       List of separators
     * @param isSeparatorRegex Whether to use regular expressions as separators
     * @param keepSeparator    How to retain separators
     * @param lengthFunction   Function to measure text length
     * @param stripWhitespace  Whether to remove whitespace from the beginning and end of text
     * @return Configured recursive character text splitter
     */
    public static TextSplitter createRecursiveSplitter(List<String> separators,
                                                       boolean isSeparatorRegex,
                                                       RecursiveCharacterTextSplitter.SeparatorPosition keepSeparator,
                                                       Function<String, Integer> lengthFunction,
                                                       boolean stripWhitespace) {
        return new RecursiveCharacterTextSplitter.RecursiveCharacterTextSplitterBuilder()
                .separators(separators == null ? Arrays.asList("\n\n", "\n", " ", "") : separators)
                .isSeparatorRegex(isSeparatorRegex)
                .keepSeparator(keepSeparator == null ? RecursiveCharacterTextSplitter.SeparatorPosition.NONE : keepSeparator)
                .lengthFunction(lengthFunction == null ? String::length : lengthFunction)
                .stripWhitespace(stripWhitespace)
                .build();
    }
}
