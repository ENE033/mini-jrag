package org.ene.minijrag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ene.minijrag.component.embedd.OllamaVectorizer;
import org.ene.minijrag.resp.OllamaEmbeddingResp;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

public class EmbeddingTest {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(App.class, args);
        OllamaVectorizer ollamaVectorizer = context.getBean(OllamaVectorizer.class);
        List<String> list = Arrays.asList("I love you ", "你好啊");
        Mono<List<OllamaEmbeddingResp>> embeddings = ollamaVectorizer.getEmbeddingsResponse(list);
        embeddings.subscribe(resp -> {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                String s = objectMapper.writeValueAsString(resp);
                System.out.println(s);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
