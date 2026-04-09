package com.ossom.monitoring.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ossom.monitoring.config.AgentProperties;
import com.ossom.monitoring.model.AlertPayload;
import com.ossom.monitoring.model.AnomalyResult;
import com.ossom.monitoring.model.MetricSnapshot;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Enriches an AnomalyResult with LLM-generated root causes and recommended actions.
 *
 * Supports:
 *   - Claude (Anthropic API)
 *   - Ollama (local, e.g. llama3)
 *
 * Falls back to a rule-based summary if LLM is disabled or the call fails.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LlmExplainerService {

    private final AgentProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            You are a senior SRE / DevOps engineer assistant.
            Given a system metric anomaly, respond ONLY with a JSON object in this exact shape:
            {
              "summary": "<one concise sentence>",
              "rootCauses": ["<cause 1>", "<cause 2>", "<cause 3>"],
              "recommendedActions": ["<action 1>", "<action 2>", "<action 3>"]
            }
            Use technical language suitable for a backend engineer.
            Do NOT include markdown formatting or any text outside the JSON object.
            """;

    @CircuitBreaker(name = "llm", fallbackMethod = "explainFallback")
    public AlertPayload explain(AnomalyResult anomaly) {
        if (!props.getLlm().isEnabled()) {
            return buildFallback(anomaly, "LLM disabled in config");
        }

        try {
            String userPrompt = buildPrompt(anomaly);
            String rawResponse = "claude".equalsIgnoreCase(props.getLlm().getProvider())
                    ? callClaude(userPrompt)
                    : callOllama(userPrompt);

            return parseAndBuild(rawResponse, anomaly);
        } catch (Exception ex) {
            log.warn("LLM call failed ({}), using fallback: {}", ex.getClass().getSimpleName(), ex.getMessage());
            return buildFallback(anomaly, ex.getMessage());
        }
    }

    // ── Claude API ────────────────────────────────────────────────────────────

    private String callClaude(String userPrompt) {
        Map<String, Object> body = Map.of(
                "model", props.getLlm().getModel(),
                "max_tokens", props.getLlm().getMaxTokens(),
                "system", SYSTEM_PROMPT,
                "messages", List.of(Map.of("role", "user", "content", userPrompt))
        );

        String response = restClient.post()
                .uri("https://api.anthropic.com/v1/messages")
                .header("x-api-key", props.getLlm().getApiKey())
                .header("anthropic-version", "2023-06-01")
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = parseJson(response);
        return root.path("content").get(0).path("text").asText();
    }

    // ── Ollama API ────────────────────────────────────────────────────────────

    private String callOllama(String userPrompt) {
        String combinedPrompt = SYSTEM_PROMPT + "\n\nUser: " + userPrompt;
        Map<String, Object> body = Map.of(
                "model", props.getLlm().getModel().isEmpty() ? "llama3" : props.getLlm().getModel(),
                "prompt", combinedPrompt,
                "stream", false
        );

        String response = restClient.post()
                .uri(props.getLlm().getOllamaBaseUrl() + "/api/generate")
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        JsonNode root = parseJson(response);
        return root.path("response").asText();
    }

    // ── Prompt builder ────────────────────────────────────────────────────────

    private String buildPrompt(AnomalyResult a) {
        MetricSnapshot snap = a.getSnapshot();
        return """
                Anomaly Report:
                  Host ID:      %s
                  Host Name:    %s
                  Metric:       %s
                  Current:      %.1f%%
                  Baseline avg: %.1f%%
                  Deviation:    %.1f%%
                  Type:         %s
                  Severity:     %s
                
                Other metrics at the time of the anomaly:
                  CPU:    %.1f%%
                  Memory: %.1f%%
                  Disk:   %.1f%%
                
                What are the 2-3 most likely root causes and the top recommended actions?
                """.formatted(
                snap.getHostId(), snap.getHostName(),
                a.getMetric(), a.getCurrentValue(), a.getBaselineValue(),
                a.getDeviationPercent(), a.getType(), a.getSeverity(),
                snap.getCpuPercent(), snap.getMemoryPercent(), snap.getDiskPercent()
        );
    }

    // ── Response parsing ──────────────────────────────────────────────────────

    private AlertPayload parseAndBuild(String raw, AnomalyResult anomaly) {
        // Strip optional markdown code fences
        String json = raw.replaceAll("(?s)```json\\s*|```", "").trim();
        try {
            JsonNode node = objectMapper.readTree(json);
            return AlertPayload.builder()
                    .anomaly(anomaly)
                    .summary(node.path("summary").asText("No summary available"))
                    .rootCauses(node.path("rootCauses").toString())
                    .recommendedActions(node.path("recommendedActions").toString())
                    .firedAt(Instant.now())
                    .llmEnriched(true)
                    .build();
        } catch (Exception ex) {
            log.warn("Failed to parse LLM JSON response: {}", ex.getMessage());
            return buildFallback(anomaly, "JSON parse error: " + ex.getMessage());
        }
    }

    // ── Fallback ──────────────────────────────────────────────────────────────

    private AlertPayload buildFallback(AnomalyResult a, String reason) {
        String summary = "[%s] %s anomaly on %s — %s at %.1f%% (baseline %.1f%%)"
                .formatted(a.getSeverity(), a.getType(),
                        a.getSnapshot().getHostName(), a.getMetric(),
                        a.getCurrentValue(), a.getBaselineValue());

        String rootCauses = switch (a.getMetric()) {
            case "CPU"    -> "[\"Runaway process or thread\", \"Recent deployment regression\", \"Unexpected traffic spike\"]";
            case "MEMORY" -> "[\"Memory leak in application\", \"Insufficient heap configuration\", \"GC pressure\"]";
            case "DISK"   -> "[\"Log file growth\", \"Temporary file accumulation\", \"Database data growth\"]";
            default       -> "[\"Unknown — manual investigation required\"]";
        };

        return AlertPayload.builder()
                .anomaly(a)
                .summary(summary)
                .rootCauses(rootCauses)
                .recommendedActions("[\"Review application logs\", \"Check recent deployments\", \"Inspect top processes with htop or kubectl top\"]")
                .firedAt(Instant.now())
                .llmEnriched(false)
                .build();
    }

    @SuppressWarnings("unused")
    private AlertPayload explainFallback(AnomalyResult anomaly, Exception ex) {
        log.warn("LLM circuit breaker OPEN — using fallback explanation");
        return buildFallback(anomaly, "Circuit breaker open: " + ex.getMessage());
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to parse JSON: " + ex.getMessage(), ex);
        }
    }
}
