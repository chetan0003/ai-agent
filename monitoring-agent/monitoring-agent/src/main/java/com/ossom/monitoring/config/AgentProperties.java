package com.ossom.monitoring.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "agent")
@Data
public class AgentProperties {

    private long pollIntervalMs = 60000;
    private Site247Props site247 = new Site247Props();
    private ThresholdProps thresholds = new ThresholdProps();
    private HistoryProps history = new HistoryProps();
    private LlmProps llm = new LlmProps();
    private SlackProps slack = new SlackProps();
    private WebhookProps webhook = new WebhookProps();
    private PagerDutyProps pagerduty = new PagerDutyProps();

    @Data
    public static class Site247Props {
        private String apiKey;
        private String baseUrl = "https://api.site24x7.com/api";
        private List<String> deviceIds = List.of();
    }

    @Data
    public static class ThresholdProps {
        private double cpuSpikeDelta = 30.0;
        private double cpuHigh = 80.0;
        private double cpuCritical = 95.0;
        private double memoryHigh = 85.0;
        private double memoryCritical = 95.0;
        private double diskHigh = 90.0;
        private double diskCritical = 98.0;
    }

    @Data
    public static class HistoryProps {
        private int windowSize = 10;
        private int cooldownMinutes = 15;
    }

    @Data
    public static class LlmProps {
        private boolean enabled = true;
        private String provider = "claude";    // claude | ollama
        private String model = "claude-sonnet-4-20250514";
        private String apiKey;
        private int maxTokens = 512;
        private int timeoutSeconds = 10;
        private String ollamaBaseUrl = "http://localhost:11434";
    }

    @Data
    public static class SlackProps {
        private boolean enabled = false;
        private String webhookUrl;
    }

    @Data
    public static class WebhookProps {
        private boolean enabled = false;
        private String url;
        private String secret;
    }

    @Data
    public static class PagerDutyProps {
        private boolean enabled = false;
        private String integrationKey;
    }
}
