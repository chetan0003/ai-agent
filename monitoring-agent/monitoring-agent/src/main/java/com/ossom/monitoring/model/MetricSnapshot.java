package com.ossom.monitoring.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Raw metric reading from a single host at a single point in time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MetricSnapshot {

    private String hostId;
    private String hostName;
    private Instant timestamp;

    private double cpuPercent;
    private double memoryPercent;
    private double diskPercent;

    /** Optional: additional custom metrics (e.g. JVM heap, DB connections) */
    private Map<String, Double> customMetrics;

    /** True if this snapshot was synthetically generated (e.g. in demo/test mode) */
    private boolean synthetic;
}
