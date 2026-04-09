package com.yourorg.agent;

import com.yourorg.agent.agent.AgentDecision;
import com.yourorg.agent.agent.OrderPipelineAgent;
import com.yourorg.agent.client.AnthropicClient;
import com.yourorg.agent.memory.AgentMemoryService;
import com.yourorg.agent.model.OrderMetrics;
import com.yourorg.agent.tool.AgentTool;
import com.yourorg.agent.tool.SlackAlertTool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
class OrderPipelineAgentTest {

    @Autowired
    private OrderPipelineAgent agent;

    @MockBean
    private AnthropicClient anthropicClient;

    @MockBean
    private AgentMemoryService memoryService;

    @MockBean(name = "fetch_order_metrics")
    private AgentTool metricsTool;

    @MockBean(name = "send_slack_alert")
    private SlackAlertTool slackAlertTool;

    private OrderMetrics sampleMetrics;

    @BeforeEach
    void setUp() {
        sampleMetrics = OrderMetrics.builder()
                .region("SRI_LANKA")
                .totalOrders(80)
                .failedOrders(12)
                .failureRatePct(15.0)
                .orderDropPct(40.0)
                .avgProcessingTimeMs(3200.0)
                .windowStart(LocalDateTime.now().minusMinutes(15))
                .windowEnd(LocalDateTime.now())
                .build();

        when(metricsTool.execute(any())).thenReturn(sampleMetrics);
        when(memoryService.getPreviousMetrics(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void shouldDetectAnomalyAndSendSlackAlert() {
        AgentDecision anomalyDecision = AgentDecision.builder()
                .anomalyDetected(true)
                .anomalyType("DROP_SPIKE")
                .severity("HIGH")
                .reasoning("Order volume dropped 40% vs prior window.")
                .recommendedAction("Check order ingestion service and DB connections.")
                .toolsToCall(List.of("send_slack_alert"))
                .build();

        when(anthropicClient.reason(anyString())).thenReturn(anomalyDecision);

        agent.analyzeRegion("SRI_LANKA");

        verify(slackAlertTool, times(1)).execute(any());
        verify(memoryService, times(1)).saveDecision(eq("SRI_LANKA"), any());
    }

    @Test
    void shouldNotAlertWhenNoAnomalyDetected() {
        AgentDecision healthyDecision = AgentDecision.builder()
                .anomalyDetected(false)
                .reasoning("All metrics within normal thresholds.")
                .toolsToCall(List.of())
                .build();

        when(anthropicClient.reason(anyString())).thenReturn(healthyDecision);

        agent.analyzeRegion("SRI_LANKA");

        verify(slackAlertTool, never()).execute(any());
        verify(memoryService, times(1)).saveMetrics(eq("SRI_LANKA"), any());
    }

    @Test
    void shouldRunFallbackWhenLlmFails() {
        when(anthropicClient.reason(anyString()))
                .thenThrow(new RuntimeException("Anthropic API unavailable"));

        // Should not throw — fallback handles it gracefully
        assertThat(catchThrowable(() -> agent.analyzeRegion("BANGLADESH"))).isNull();
    }

    private Throwable catchThrowable(Runnable runnable) {
        try {
            runnable.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
