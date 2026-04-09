package com.ossom.monitoring.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ossom.monitoring.config.AgentProperties;
import com.ossom.monitoring.model.AlertPayload;
import com.ossom.monitoring.model.AnomalyResult;
import com.ossom.monitoring.model.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

/**
 * Delivers alert payloads to a generic HTTP webhook endpoint.
 * Activated when agent.webhook.enabled=true.
 *
 * Optionally signs payloads with HMAC-SHA256 if agent.webhook.secret is set.
 * Receiving side can verify the X-Monitoring-Signature header.
 */
@Component
@ConditionalOnProperty(name = "agent.webhook.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class WebhookAlertChannel implements AlertChannel {

    private final AgentProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(Severity severity) {
        return true; // Webhook receives all severities
    }

    @Override
    public void send(AlertPayload payload) {
        AnomalyResult a = payload.getAnomaly();

        Map<String, Object> body = Map.of(
                "event",      "anomaly_detected",
                "timestamp",  payload.getFiredAt().toString(),
                "host",       Map.of(
                        "id",   a.getHostId(),
                        "name", a.getSnapshot().getHostName()
                ),
                "anomaly",    Map.of(
                        "metric",           a.getMetric(),
                        "type",             a.getType().name(),
                        "severity",         a.getSeverity().name(),
                        "currentValue",     a.getCurrentValue(),
                        "baselineValue",    a.getBaselineValue(),
                        "deviationPercent", a.getDeviationPercent()
                ),
                "explanation", Map.of(
                        "summary",              payload.getSummary(),
                        "rootCauses",           payload.getRootCauses(),
                        "recommendedActions",   payload.getRecommendedActions(),
                        "llmEnriched",          payload.isLlmEnriched()
                )
        );

        try {
            String jsonBody = objectMapper.writeValueAsString(body);
            var req = restClient.post()
                    .uri(props.getWebhook().getUrl())
                    .header("Content-Type", "application/json")
                    .header("X-Monitoring-Agent", "ossom-monitoring/1.0");

            // Add HMAC signature if secret is configured
            String secret = props.getWebhook().getSecret();
            if (secret != null && !secret.isBlank()) {
                String sig = hmacSha256(secret, jsonBody);
                req = req.header("X-Monitoring-Signature", "sha256=" + sig);
            }

            req.body(jsonBody).retrieve().toBodilessEntity();
            log.info("Webhook alert sent — host={} severity={}", a.getHostId(), a.getSeverity());
        } catch (Exception ex) {
            log.error("Failed to send webhook alert: {}", ex.getMessage());
            throw new RuntimeException(ex);
        }
    }

    private String hmacSha256(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec key = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(key);
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(hash);
    }
}
