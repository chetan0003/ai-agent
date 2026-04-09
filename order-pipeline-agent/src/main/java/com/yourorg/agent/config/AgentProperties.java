package com.yourorg.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private AnomalyConfig anomaly = new AnomalyConfig();

    @Data
    public static class AnomalyConfig {
        private double orderDropThresholdPct = 30.0;
        private double failureRateThresholdPct = 10.0;
        private long processingDelayMs = 5000L;
        private int stalledOrderMinutes = 20;
        private String cron = "0 */5 * * * *";
        private List<String> regions = List.of("SRI_LANKA", "BANGLADESH", "POPEYES");
    }

    public String getThresholdsJson() {
        return """
                {
                  "orderDropThresholdPct": %s,
                  "failureRateThresholdPct": %s,
                  "processingDelayMs": %s,
                  "stalledOrderMinutes": %s
                }
                """.formatted(
                anomaly.getOrderDropThresholdPct(),
                anomaly.getFailureRateThresholdPct(),
                anomaly.getProcessingDelayMs(),
                anomaly.getStalledOrderMinutes()
        );
    }
}
