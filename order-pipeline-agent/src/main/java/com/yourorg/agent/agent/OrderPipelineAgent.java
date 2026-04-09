package com.yourorg.agent.agent;

import com.yourorg.agent.client.AnthropicClient;
import com.yourorg.agent.config.AgentProperties;
import com.yourorg.agent.memory.AgentMemoryService;
import com.yourorg.agent.model.OrderMetrics;
import com.yourorg.agent.prompt.PromptBuilder;
import com.yourorg.agent.tool.AgentTool;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderPipelineAgent {

    private final AnthropicClient anthropicClient;
    private final PromptBuilder promptBuilder;
    private final Map<String, AgentTool> tools;
    private final AgentMemoryService memory;
    private final AgentProperties agentProperties;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "${agent.anomaly.cron}")
    public void runAgentCycle() {
        log.info("Agent cycle starting for all regions...");
        agentProperties.getAnomaly().getRegions()
                .forEach(this::analyzeRegion);
    }

    public void analyzeRegion(String region) {
        log.info("Analyzing region: {}", region);

        try {
            // 1. Fetch current metrics via tool
            AgentTool metricsTool = tools.get("fetch_order_metrics");
            OrderMetrics current = (OrderMetrics) metricsTool.execute(
                    Map.of("region", region, "window_minutes", 15));

            // 2. Get previous window from memory for comparison
            OrderMetrics previous = memory.getPreviousMetrics(region)
                    .orElse(current);

            // 3. Build LLM prompt with metrics + thresholds
            String prompt = promptBuilder.buildAnomalyPrompt(
                    current, previous, agentProperties.getThresholdsJson());

            // 4. Call LLM for reasoning
            AgentDecision decision = anthropicClient.reason(prompt);
            log.info("Decision for region [{}]: anomaly={}, severity={}",
                    region, decision.isAnomalyDetected(), decision.getSeverity());

            // 5. Execute tools the LLM requested
            if (decision.isAnomalyDetected() && decision.getToolsToCall() != null) {
                decision.getToolsToCall().forEach(toolName -> {
                    AgentTool tool = tools.get(toolName);
                    if (tool != null) {
                        tool.execute(Map.of(
                                "region", region,
                                "anomalyType", decision.getAnomalyType(),
                                "severity", decision.getSeverity(),
                                "reasoning", decision.getReasoning(),
                                "recommendedAction", decision.getRecommendedAction()
                        ));
                    } else {
                        log.warn("Tool not found: {}", toolName);
                    }
                });

                meterRegistry.counter("agent.anomaly.detected",
                        "region", region, "severity", decision.getSeverity()).increment();
            }

            // 6. Persist decision + update memory
            memory.saveMetrics(region, current);
            memory.saveDecision(region, decision);

            meterRegistry.counter("agent.cycle.completed", "region", region).increment();

        } catch (Exception e) {
            log.error("Agent cycle failed for region: {}", region, e);
            meterRegistry.counter("agent.cycle.error", "region", region).increment();

            // Fallback: rule-based alert if LLM call fails
            runFallbackRuleCheck(region);
        }
    }

    /**
     * Rule-based fallback when LLM call fails.
     * Ensures monitoring continues even without LLM availability.
     */
    private void runFallbackRuleCheck(String region) {
        log.warn("Running rule-based fallback check for region: {}", region);
        try {
            AgentTool metricsTool = tools.get("fetch_order_metrics");
            OrderMetrics metrics = (OrderMetrics) metricsTool.execute(
                    Map.of("region", region, "window_minutes", 15));

            AgentProperties.AnomalyConfig config = agentProperties.getAnomaly();

            boolean orderDrop = metrics.getOrderDropPct() >= config.getOrderDropThresholdPct();
            boolean highFailure = metrics.getFailureRatePct() >= config.getFailureRateThresholdPct();

            if (orderDrop || highFailure) {
                AgentTool alertTool = tools.get("send_slack_alert");
                if (alertTool != null) {
                    alertTool.execute(Map.of(
                            "region", region,
                            "anomalyType", orderDrop ? "DROP_SPIKE" : "HIGH_FAILURE",
                            "severity", "HIGH",
                            "reasoning", "Fallback rule-based detection triggered",
                            "recommendedAction", "Investigate order pipeline immediately"
                    ));
                }
            }
        } catch (Exception ex) {
            log.error("Fallback rule check also failed for region: {}", region, ex);
        }
    }
}
