package org.ene.minijrag.component.embedd.inc;

import reactor.core.publisher.Mono;

import java.util.List;

public interface TextVectorizer {
    Mono<List<Float>> getEmbedding(String text);

    Mono<List<List<Float>>> getEmbedding(List<String> texts);
}
