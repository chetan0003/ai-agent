package com.yourorg.agent;

import com.yourorg.agent.model.OrderMetrics;
import com.yourorg.agent.prompt.PromptBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PromptBuilderTest {

    @Autowired
    private PromptBuilder promptBuilder;

    @Test
    void shouldContainRegionAndMetricsInPrompt() {
        OrderMetrics current = OrderMetrics.builder()
                .region("SRI_LANKA")
                .totalOrders(50)
                .failedOrders(8)
                .failureRatePct(16.0)
                .orderDropPct(45.0)
                .avgProcessingTimeMs(4200.0)
                .windowStart(LocalDateTime.now().minusMinutes(15))
                .windowEnd(LocalDateTime.now())
                .build();

        OrderMetrics previous = OrderMetrics.builder()
                .totalOrders(91)
                .failedOrders(2)
                .build();

        String thresholds = """
                { "orderDropThresholdPct": 30, "failureRateThresholdPct": 10 }
                """;

        String prompt = promptBuilder.buildAnomalyPrompt(current, previous, thresholds);

        assertThat(prompt).contains("SRI_LANKA");
        assertThat(prompt).contains("45.0");
        assertThat(prompt).contains("16.0");
        assertThat(prompt).contains("anomalyDetected");
        assertThat(prompt).contains("toolsToCall");
    }
}
