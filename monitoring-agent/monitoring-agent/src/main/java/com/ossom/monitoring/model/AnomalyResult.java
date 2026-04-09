package com.ossom.monitoring.model;

import lombok.Builder;
import lombok.Data;

/**
 * Result of anomaly detection for a single host + metric combination.
 */
@Data
@Builder
public class AnomalyResult {

    private String hostId;

    /** e.g. "CPU", "MEMORY", "DISK" */
    private String metric;

    private AnomalyType type;
    private Severity severity;

    private double currentValue;
    private double baselineValue;

    /** ((current - baseline) / baseline) * 100 */
    private double deviationPercent;

    /** The raw snapshot that triggered this result */
    private MetricSnapshot snapshot;
}
