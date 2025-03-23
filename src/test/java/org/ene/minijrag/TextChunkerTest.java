package org.ene.minijrag;

import org.ene.minijrag.component.parser.PdfParser;
import org.ene.minijrag.component.splitter.RecursiveCharacterTextSplitter;
import org.ene.minijrag.component.splitter.TextSplitter;
import org.ene.minijrag.component.splitter.TextSplitterFactory;
import org.ene.minijrag.util.*;
import reactor.core.publisher.Mono;

import java.util.List;

public class TextChunkerTest {
    public static void main(String[] args) {
        Character character = '�';

        PdfParser pdfParser = new PdfParser();
        Mono<String> stringMono = pdfParser.downloadAndParsePdf("xxx");
        String block = stringMono.block();

        // 1. Using Character Splitter
        System.out.println("\n===== CHARACTER SPLITTER DEMO =====");
        TextSplitter characterSplitter = TextSplitterFactory.createCharacterSplitter();
        List<String> characterChunks = characterSplitter.split(block, 500, 50);
        characterChunks.forEach((x) -> {
            int countTokens = TikTokenUtil.countTokens(x);
            System.out.println("=======================, token=" + countTokens);
            System.out.println(x);
        });

        // 2. Using Token Splitter
        System.out.println("\n===== TOKEN SPLITTER DEMO =====");
        TextSplitter tokenSplitter = TextSplitterFactory.createTokenSplitter();
        List<String> tokenChunks = tokenSplitter.split(block, 500, 50);
        tokenChunks.forEach((x) -> {
            int countTokens = TikTokenUtil.countTokens(x);
            System.out.println("=======================, token=" + countTokens);
            System.out.println(x);
        });

        // 3. Using Default Recursive Splitter
        System.out.println("\n===== DEFAULT RECURSIVE SPLITTER DEMO =====");
        TextSplitter defaultRecursiveSplitter = TextSplitterFactory.createDefaultRecursiveSplitter();
        List<String> defaultRecursiveChunks = defaultRecursiveSplitter.split(block, 500, 50);
        defaultRecursiveChunks.forEach((x) -> {
            int countTokens = TikTokenUtil.countTokens(x);
            System.out.println("=======================, token=" + countTokens);
            System.out.println(x);
        });

        // 4. Using Custom Recursive Splitter with TikToken (similar to fromTikTokenEncoder())
        System.out.println("\n===== TIKTOKEN RECURSIVE SPLITTER DEMO =====");
        TextSplitter tikTokenRecursiveSplitter = TextSplitterFactory.createRecursiveSplitter(
                null,  // default separators
                false, // not using regex
                null,  // default separator position
                TikTokenUtil::countTokens, // use TikToken for length calculation
                true   // strip whitespace
        );
        List<String> tikTokenRecursiveChunks = tikTokenRecursiveSplitter.split(block, 500, 50);
        tikTokenRecursiveChunks.forEach((x) -> {
            int countTokens = TikTokenUtil.countTokens(x);
            System.out.println("=======================, token=" + countTokens);
            System.out.println(x);
        });
    }
}
