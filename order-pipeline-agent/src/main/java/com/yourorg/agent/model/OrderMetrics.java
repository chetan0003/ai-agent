package com.yourorg.agent.model;

import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class OrderMetrics implements Serializable {

    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private long totalOrders;
    private long failedOrders;
    private double avgProcessingTimeMs;
    private double orderDropPct;
    private double failureRatePct;
    private Map<String, Long> ordersByStatus;
    private String region;
}
