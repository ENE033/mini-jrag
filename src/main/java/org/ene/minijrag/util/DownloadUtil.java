package org.ene.minijrag.util;

import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URISyntaxException;

public class DownloadUtil {

    private final static WebClient webClient;

    static {
        // Initialize WebClient
        webClient = WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(configurer ->
                                configurer.defaultCodecs().maxInMemorySize(100 * 1024 * 1024))
                        .build())
                .build();
    }

    /**
     * Download file
     *
     * @param url URL of the file
     */
    public static Mono<byte[]> download(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        return webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(byte[].class); // Download file as byte array
    }
}
