package com.yourorg.agent.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
public class WebClientConfig {

    @Value("${anthropic.api-key}")
    private String anthropicApiKey;

    @Bean
    public WebClient anthropicWebClient(ObjectMapper objectMapper) {
        // Use the same ObjectMapper Spring Boot configured (honours @JsonProperty on records)
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(config -> config.defaultCodecs()
                        .jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper,
                                MediaType.APPLICATION_JSON)))
                .build();

        return WebClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT,       MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("x-api-key",         anthropicApiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .exchangeStrategies(strategies)
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    @Bean
    public WebClient slackWebClient() {
        return WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("Outbound {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("Response status: {}", response.statusCode());
            return Mono.just(response);
        });
    }
}
