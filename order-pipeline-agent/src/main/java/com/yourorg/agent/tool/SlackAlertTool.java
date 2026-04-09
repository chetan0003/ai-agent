package com.yourorg.agent.tool;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component("send_slack_alert")
@Slf4j
@RequiredArgsConstructor
public class SlackAlertTool implements AgentTool {

    private final WebClient slackWebClient;

    @Value("${slack.webhook-url}")
    private String webhookUrl;

    @Override
    public String getName() {
        return "send_slack_alert";
    }

    @Override
    public String getDescription() {
        return "Sends an anomaly alert to the Slack ops channel with full defect context.";
    }

    @Override
    public String execute(Map<String, Object> params) {
        String message = buildSlackMessage(params);
        log.info("Sending Slack alert for region={}, type={}, severity={}",
                params.get("region"), params.get("anomalyType"), params.get("severity"));

        slackWebClient.post()
                .uri(webhookUrl)
                .bodyValue(Map.of("text", message))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(e -> log.error("Failed to send Slack alert: {}", e.getMessage()))
                .subscribe();

        return "Alert dispatched";
    }

    private String buildSlackMessage(Map<String, Object> params) {
        return """
                :rotating_light: *Order Pipeline Anomaly Detected*
                *Region:* %s
                *Type:* %s
                *Severity:* %s
                *Reasoning:* %s
                *Recommended Action:* %s
                """.formatted(
                params.get("region"),
                params.get("anomalyType"),
                params.get("severity"),
                params.get("reasoning"),
                params.get("recommendedAction")
        );
    }
}
