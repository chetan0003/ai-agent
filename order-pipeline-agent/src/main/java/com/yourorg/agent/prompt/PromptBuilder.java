package com.yourorg.agent.prompt;

import com.yourorg.agent.model.OrderMetrics;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String buildAnomalyPrompt(OrderMetrics current,
                                     OrderMetrics previous,
                                     String thresholdsJson) {
        return """
                You are an order pipeline monitoring agent for a multi-tenant food delivery platform.
                Your job is to detect anomalies in order flow and decide if an alert should be sent.

                ## Current window metrics (%s to %s) — Region: %s
                - Total orders: %d
                - Failed orders: %d (%.1f%% failure rate)
                - Avg processing time: %.0fms
                - Order drop vs previous window: %.1f%%
                - Orders by status: %s

                ## Previous window metrics
                - Total orders: %d
                - Failed orders: %d

                ## Anomaly thresholds
                %s

                ## Your task
                1. Determine if an anomaly exists (order drop, high failure rate, stalled orders, unusual patterns)
                2. If yes — classify: DROP_SPIKE | HIGH_FAILURE | STALE_ORDERS | PROCESSING_DELAY | OTHER
                3. Rate severity: LOW | MEDIUM | HIGH | CRITICAL
                4. Explain your reasoning in 2-3 sentences
                5. Recommend an immediate action for the on-call engineer

                ## Response format (JSON only, no markdown)
                {
                  "anomalyDetected": true/false,
                  "anomalyType": "...",
                  "severity": "...",
                  "reasoning": "...",
                  "recommendedAction": "...",
                  "toolsToCall": ["send_slack_alert"] or []
                }
                """.formatted(
                current.getWindowStart(), current.getWindowEnd(), current.getRegion(),
                current.getTotalOrders(), current.getFailedOrders(), current.getFailureRatePct(),
                current.getAvgProcessingTimeMs(), current.getOrderDropPct(),
                current.getOrdersByStatus(),
                previous.getTotalOrders(), previous.getFailedOrders(),
                thresholdsJson
        );
    }
}
