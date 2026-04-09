package com.yourorg.agent.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yourorg.agent.agent.AgentDecision;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnthropicClient {

    private final WebClient anthropicWebClient;
    private final ObjectMapper objectMapper;

    @Value("${anthropic.model}")
    private String model;

    @Value("${anthropic.max-tokens}")
    private int maxTokens;

    // ── Public API ────────────────────────────────────────────────────────────

    public AgentDecision reason(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            log.warn("Prompt is null or blank — skipping LLM call");
            return fallbackDecision("Empty prompt");
        }

        try {
            String rawResponse = callApi(prompt);
            if (rawResponse == null || rawResponse.isBlank()) {
                log.error("Anthropic returned null/empty body — check API key and model name");
                return fallbackDecision("Empty API response");
            }
            return parseDecision(rawResponse);
        } catch (AnthropicApiException ex) {
            log.error("Anthropic API error [{}]: {}", ex.getStatus(), ex.getMessage());
            return fallbackDecision("API error " + ex.getStatus() + ": " + ex.getMessage());
        } catch (Exception ex) {
            log.error("Unexpected error calling Anthropic API", ex);
            return fallbackDecision("Unexpected error: " + ex.getMessage());
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private String callApi(String userPrompt) throws AnthropicApiException {
        // Build request as plain Map — avoids @JsonProperty issues on Java records
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,           // int — serialises as JSON number
                "messages", List.of(
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        // Log the exact JSON being sent — this tells you immediately if the body is wrong
        try {
            log.info("Anthropic request body: {}",
                    objectMapper.writeValueAsString(requestBody));
        } catch (Exception ignored) {}

        final String[] errorBodyHolder = {null};
        final int[] statusHolder = {0};

        String response = anthropicWebClient.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, httpResponse ->
                        httpResponse.bodyToMono(String.class).flatMap(body -> {
                            errorBodyHolder[0] = body;
                            statusHolder[0] = httpResponse.statusCode().value();
                            log.error("Anthropic 4xx | status={} body={}",
                                    httpResponse.statusCode(), body);
                            return Mono.error(new AnthropicApiException(
                                    httpResponse.statusCode().value(), body));
                        })
                )
                .onStatus(HttpStatusCode::is5xxServerError, httpResponse ->
                        httpResponse.bodyToMono(String.class).flatMap(body -> {
                            errorBodyHolder[0] = body;
                            statusHolder[0] = httpResponse.statusCode().value();
                            log.error("Anthropic 5xx | status={} body={}",
                                    httpResponse.statusCode(), body);
                            return Mono.error(new AnthropicApiException(
                                    httpResponse.statusCode().value(), body));
                        })
                )
                .bodyToMono(String.class)
                .doOnSuccess(resp ->
                        log.info("Anthropic response: {}", resp)
                )
                .block();

        return response;
    }

    private AgentDecision parseDecision(String raw) {
        try {
            JsonNode root = objectMapper.readTree(raw);
            String content = root.at("/content/0/text").asText();

            if (content == null || content.isBlank()) {
                log.warn("LLM returned empty content. Full response: {}", raw);
                return fallbackDecision("Empty content from LLM");
            }

            // Strip accidental markdown fences
            String cleaned = content.replaceAll("(?s)```json\s*|```", "").trim();
            log.debug("Cleaned LLM content for parsing: {}", cleaned);

            return objectMapper.readValue(cleaned, AgentDecision.class);

        } catch (Exception e) {
            log.error("Failed to parse AgentDecision. Raw response was: {}", raw, e);
            return fallbackDecision("Parse error: " + e.getMessage());
        }
    }

    private AgentDecision fallbackDecision(String reason) {
        return AgentDecision.builder()
                .anomalyDetected(false)
                .reasoning("LLM unavailable — " + reason)
                .toolsToCall(List.of())
                .build();
    }

    // ── Exception ─────────────────────────────────────────────────────────────

    public static class AnthropicApiException extends RuntimeException {
        private final int status;

        public AnthropicApiException(int status, String body) {
            super(body);
            this.status = status;
        }

        public int getStatus() { return status; }
    }
}
