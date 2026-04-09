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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sends rich Slack alert messages via Incoming Webhooks.
 * Activated when agent.slack.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "agent.slack.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SlackAlertChannel implements AlertChannel {

    private final AgentProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(Severity severity) {
        // Send Slack messages for MEDIUM and above (skip LOW to reduce noise)
        return severity != Severity.LOW;
    }

    @Override
    public void send(AlertPayload payload) {
        AnomalyResult a = payload.getAnomaly();

        String color = switch (a.getSeverity()) {
            case CRITICAL -> "#FF0000";
            case HIGH     -> "#FF8C00";
            case MEDIUM   -> "#FFD700";
            default       -> "#36A64F";
        };
        String emoji = switch (a.getSeverity()) {
            case CRITICAL -> ":rotating_light:";
            case HIGH     -> ":warning:";
            case MEDIUM   -> ":large_yellow_circle:";
            default       -> ":information_source:";
        };

        List<Map<String, Object>> fields = new ArrayList<>();
        fields.add(field("Host",      a.getSnapshot().getHostName()));
        fields.add(field("Metric",    a.getMetric()));
        fields.add(field("Current",   String.format("%.1f%%", a.getCurrentValue())));
        fields.add(field("Baseline",  String.format("%.1f%%", a.getBaselineValue())));
        fields.add(field("Deviation", String.format("%.1f%%", a.getDeviationPercent())));
        fields.add(field("Type",      a.getType().name()));

        // Pretty-print JSON arrays if possible
        fields.add(field("Root Causes",  prettyArray(payload.getRootCauses())));
        fields.add(field("Actions",      prettyArray(payload.getRecommendedActions())));

        if (payload.isLlmEnriched()) {
            fields.add(field("Analysis", "AI-enriched :robot_face:"));
        }

        Map<String, Object> attachment = Map.of(
                "color",   color,
                "pretext", emoji + " *" + a.getSeverity() + " ALERT* — " + a.getSnapshot().getHostName(),
                "text",    payload.getSummary(),
                "fields",  fields,
                "footer",  "Monitoring Agent | " + payload.getFiredAt().toString(),
                "mrkdwn_in", List.of("pretext", "text")
        );

        Map<String, Object> body = Map.of("attachments", List.of(attachment));

        try {
            restClient.post()
                    .uri(props.getSlack().getWebhookUrl())
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Slack alert sent — host={} severity={}", a.getHostId(), a.getSeverity());
        } catch (Exception ex) {
            log.error("Failed to send Slack alert: {}", ex.getMessage());
            throw ex;
        }
    }

    private Map<String, Object> field(String title, String value) {
        return Map.of("title", title, "value", value, "short", true);
    }

    /** Convert JSON array string ["a","b"] → "• a\n• b" */
    private String prettyArray(String jsonArray) {
        try {
            String[] items = objectMapper.readValue(jsonArray, String[].class);
            StringBuilder sb = new StringBuilder();
            for (String item : items) {
                sb.append("• ").append(item).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception ex) {
            return jsonArray;
        }
    }
}
