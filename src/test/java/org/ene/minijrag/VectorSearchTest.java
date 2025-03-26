package org.ene.minijrag;

import org.ene.minijrag.service.DocumentService;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

public class VectorSearchTest {
    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(App.class, args);
        context.getBean(DocumentService.class)
                .searchSimilarChunks("love", 5, "test_embeddings", null, null)
                .subscribe(System.out::println);
    }
}
