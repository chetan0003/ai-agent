package com.ossom.monitoring.model;

/** Classification of the detected anomaly pattern. */
public enum AnomalyType {
    STABLE,
    SPIKE,
    HIGH_USAGE,
    SUSTAINED_HIGH,
    UNUSUAL_PATTERN
}
