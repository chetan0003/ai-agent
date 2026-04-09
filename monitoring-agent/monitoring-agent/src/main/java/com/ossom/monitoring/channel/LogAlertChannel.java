package com.ossom.monitoring.channel;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ossom.monitoring.model.AlertPayload;
import com.ossom.monitoring.model.AnomalyResult;
import com.ossom.monitoring.model.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Always-active fallback channel that logs alerts to the application log.
 * Useful in dev/test environments and as an audit trail in production.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogAlertChannel implements AlertChannel {

    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(Severity severity) {
        return true; // Log every severity
    }

    @Override
    public void send(AlertPayload payload) {
        AnomalyResult a = payload.getAnomaly();
        String border = "=".repeat(70);

        log.warn("\n{}\n  MONITORING ALERT — {} | {}\n{}\n" +
                 "  Host       : {} ({})\n" +
                 "  Metric     : {}\n" +
                 "  Type       : {}\n" +
                 "  Current    : {:.1f}%\n" +
                 "  Baseline   : {:.1f}%\n" +
                 "  Deviation  : {:.1f}%\n" +
                 "  Summary    : {}\n" +
                 "  Root Causes: {}\n" +
                 "  Actions    : {}\n" +
                 "  LLM        : {}\n" +
                 "  Fired At   : {}\n{}",
                border,
                a.getSeverity(), a.getType(),
                border,
                a.getSnapshot().getHostName(), a.getHostId(),
                a.getMetric(),
                a.getType(),
                a.getCurrentValue(),
                a.getBaselineValue(),
                a.getDeviationPercent(),
                payload.getSummary(),
                prettyJson(payload.getRootCauses()),
                prettyJson(payload.getRecommendedActions()),
                payload.isLlmEnriched() ? "AI-enriched" : "rule-based fallback",
                payload.getFiredAt(),
                border);
    }

    private String prettyJson(String json) {
        try {
            Object parsed = objectMapper.readValue(json, Object.class);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(parsed);
        } catch (Exception e) {
            return json;
        }
    }
}
