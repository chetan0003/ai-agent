package com.yourorg.agent.tool;

import com.yourorg.agent.model.OrderMetrics;
import com.yourorg.agent.model.enums.OrderStatus;
import com.yourorg.agent.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("fetch_order_metrics")
@Slf4j
@RequiredArgsConstructor
public class OrderMetricsTool implements AgentTool {

    private final OrderRepository orderRepository;

    @Override
    public String getName() {
        return "fetch_order_metrics";
    }

    @Override
    public String getDescription() {
        return "Fetches order pipeline metrics for a given time window and region.";
    }

    @Override
    public OrderMetrics execute(Map<String, Object> params) {
        String region = (String) params.get("region");
        int windowMinutes = (int) params.getOrDefault("window_minutes", 15);

        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusMinutes(windowMinutes);

        log.debug("Fetching metrics for region={}, window={}min", region, windowMinutes);

        long total = orderRepository.countByRegionAndCreatedAtBetween(region, start, end);
        long failed = orderRepository.countByRegionAndStatusAndCreatedAtBetween(
                region, OrderStatus.FAILED, start, end);

        Double avgTime = orderRepository.avgProcessingTimeMs(region, start, end);
        double avgProcessingTime = avgTime != null ? avgTime : 0.0;

        // Compare with prior window for drop %
        long prevTotal = orderRepository.countByRegionAndCreatedAtBetween(
                region, start.minusMinutes(windowMinutes), start);

        double dropPct = prevTotal > 0
                ? ((prevTotal - total) / (double) prevTotal) * 100.0
                : 0.0;

        // Build status breakdown map from Object[] results
        List<Object[]> rawStatus = orderRepository.countGroupedByStatus(region, start, end);
        Map<String, Long> statusBreakdown = new HashMap<>();
        for (Object[] row : rawStatus) {
            statusBreakdown.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }

        return OrderMetrics.builder()
                .windowStart(start)
                .windowEnd(end)
                .totalOrders(total)
                .failedOrders(failed)
                .avgProcessingTimeMs(avgProcessingTime)
                .orderDropPct(dropPct)
                .failureRatePct(total > 0 ? (failed / (double) total) * 100.0 : 0.0)
                .ordersByStatus(statusBreakdown)
                .region(region)
                .build();
    }
}
