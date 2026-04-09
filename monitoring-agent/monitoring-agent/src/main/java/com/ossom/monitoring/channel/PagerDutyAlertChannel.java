package com.ossom.monitoring.channel;

import com.ossom.monitoring.config.AgentProperties;
import com.ossom.monitoring.model.AlertPayload;
import com.ossom.monitoring.model.AnomalyResult;
import com.ossom.monitoring.model.Severity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Triggers PagerDuty incidents via the Events API v2.
 * Only fires for HIGH and CRITICAL severities.
 * Activated when agent.pagerduty.enabled=true.
 */
@Component
@ConditionalOnProperty(name = "agent.pagerduty.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class PagerDutyAlertChannel implements AlertChannel {

    private static final String PAGERDUTY_EVENTS_URL = "https://events.pagerduty.com/v2/enqueue";

    private final AgentProperties props;
    private final RestClient restClient;

    @Override
    public boolean supports(Severity severity) {
        return severity == Severity.HIGH || severity == Severity.CRITICAL;
    }

    @Override
    public void send(AlertPayload payload) {
        AnomalyResult a = payload.getAnomaly();

        String pdSeverity = switch (a.getSeverity()) {
            case CRITICAL -> "critical";
            case HIGH     -> "error";
            default       -> "warning";
        };

        Map<String, Object> body = Map.of(
                "routing_key",  props.getPagerduty().getIntegrationKey(),
                "event_action", "trigger",
                "payload", Map.of(
                        "summary",   payload.getSummary(),
                        "severity",  pdSeverity,
                        "source",    a.getSnapshot().getHostName(),
                        "component", a.getMetric(),
                        "group",     "monitoring-agent",
                        "class",     a.getType().name(),
                        "custom_details", Map.of(
                                "currentValue",     a.getCurrentValue(),
                                "baselineValue",    a.getBaselineValue(),
                                "deviationPercent", a.getDeviationPercent(),
                                "rootCauses",       payload.getRootCauses(),
                                "actions",          payload.getRecommendedActions()
                        )
                )
        );

        try {
            restClient.post()
                    .uri(PAGERDUTY_EVENTS_URL)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("PagerDuty incident triggered — host={} severity={}",
                    a.getHostId(), a.getSeverity());
        } catch (Exception ex) {
            log.error("Failed to trigger PagerDuty incident: {}", ex.getMessage());
            throw ex;
        }
    }
}
