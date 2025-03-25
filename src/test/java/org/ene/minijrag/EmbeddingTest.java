package org.ene.minijrag;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ene.minijrag.client.JinaClient;
import org.ene.minijrag.resp.JinaEmbeddingResp;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;

public class EmbeddingTest {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(App.class, args);
        JinaClient jinaClient = context.getBean(JinaClient.class);
        List<String> list = Arrays.asList("I love you ", "你好啊");
        Mono<JinaEmbeddingResp> embeddings = jinaClient.getEmbeddings(list);
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
