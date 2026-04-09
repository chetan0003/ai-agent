package com.ossom.monitoring.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA entity for persisting a historical record of every fired alert.
 * Useful for audit, dashboards, and long-term trend analysis.
 */
@Entity
@Table(name = "alert_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String hostId;
    private String hostName;
    private String metric;

    @Enumerated(EnumType.STRING)
    private AnomalyType anomalyType;

    @Enumerated(EnumType.STRING)
    private Severity severity;

    private double currentValue;
    private double baselineValue;
    private double deviationPercent;

    @Column(length = 2000)
    private String summary;

    @Column(length = 2000)
    private String rootCauses;

    @Column(length = 2000)
    private String recommendedActions;

    private boolean llmEnriched;
    private Instant firedAt;
}
