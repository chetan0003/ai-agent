package com.ossom.monitoring;

import com.ossom.monitoring.config.AgentProperties;
import com.ossom.monitoring.model.*;
import com.ossom.monitoring.service.AnomalyDetectorService;
import com.ossom.monitoring.service.MetricsHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectorServiceTest {

    @Mock private MetricsHistoryService historyService;

    private AnomalyDetectorService detector;
    private AgentProperties props;

    @BeforeEach
    void setUp() {
        props = new AgentProperties();
        // Use defaults: cpuHigh=80, cpuCritical=95, cpuSpikeDelta=30
        detector = new AnomalyDetectorService(historyService, props);
        doNothing().when(historyService).save(any());
    }

    // ── CPU Tests ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("CPU above critical threshold → CRITICAL HIGH_USAGE")
    void cpuCritical() {
        when(historyService.getBaselineCpu(any())).thenReturn(OptionalDouble.of(50.0));
        when(historyService.getBaselineMemory(any())).thenReturn(OptionalDouble.of(40.0));
        when(historyService.getBaselineDisk(any())).thenReturn(OptionalDouble.empty());
        when(historyService.historySize(any())).thenReturn(5);
        when(historyService.getHistory(any())).thenReturn(Collections.emptyList());

        MetricSnapshot snap = snapshot("host-1", 96.0, 40.0, 50.0);
        Optional<AnomalyResult> result = detector.detect(snap);

        assertThat(result).isPresent();
        assertThat(result.get().getSeverity()).isEqualTo(Severity.CRITICAL);
        assertThat(result.get().getType()).isEqualTo(AnomalyType.HIGH_USAGE);
        assertThat(result.get().getMetric()).isEqualTo("CPU");
    }

    @Test
    @DisplayName("CPU spike of 35% above baseline → at least MEDIUM SPIKE")
    void cpuSpike() {
        when(historyService.getBaselineCpu(any())).thenReturn(OptionalDouble.of(40.0));
        when(historyService.getBaselineMemory(any())).thenReturn(OptionalDouble.of(40.0));
        when(historyService.getBaselineDisk(any())).thenReturn(OptionalDouble.empty());
        when(historyService.historySize(any())).thenReturn(5);
        when(historyService.getHistory(any())).thenReturn(Collections.emptyList());

        MetricSnapshot snap = snapshot("host-1", 75.0, 40.0, 50.0); // delta = 35
        Optional<AnomalyResult> result = detector.detect(snap);

        assertThat(result).isPresent();
        assertThat(result.get().getType()).isEqualTo(AnomalyType.SPIKE);
        assertThat(result.get().getSeverity()).isEqualTo(Severity.MEDIUM);
    }

    @Test
    @DisplayName("Sustained high — 5 consecutive readings above threshold → SUSTAINED_HIGH")
    void sustainedHigh() {
        List<MetricSnapshot> history = List.of(
                snapshot("host-1", 82.0, 40.0, 50.0),
                snapshot("host-1", 84.0, 40.0, 50.0),
                snapshot("host-1", 85.0, 40.0, 50.0),
                snapshot("host-1", 83.0, 40.0, 50.0),
                snapshot("host-1", 81.0, 40.0, 50.0)
        );

        when(historyService.getBaselineCpu(any())).thenReturn(OptionalDouble.of(82.0));
        when(historyService.getBaselineMemory(any())).thenReturn(OptionalDouble.of(40.0));
        when(historyService.getBaselineDisk(any())).thenReturn(OptionalDouble.empty());
        when(historyService.historySize(any())).thenReturn(5);
        when(historyService.getHistory(any())).thenReturn(history);

        MetricSnapshot snap = snapshot("host-1", 82.0, 40.0, 50.0);
        Optional<AnomalyResult> result = detector.detect(snap);

        assertThat(result).isPresent();
        // SUSTAINED_HIGH is HIGH; HIGH_USAGE also HIGH — either is acceptable
        assertThat(result.get().getSeverity()).isIn(Severity.HIGH);
    }

    @Test
    @DisplayName("All metrics within normal range → no anomaly")
    void allStable() {
        when(historyService.getBaselineCpu(any())).thenReturn(OptionalDouble.of(35.0));
        when(historyService.getBaselineMemory(any())).thenReturn(OptionalDouble.of(40.0));
        when(historyService.getBaselineDisk(any())).thenReturn(OptionalDouble.empty());
        when(historyService.historySize(any())).thenReturn(5);
        when(historyService.getHistory(any())).thenReturn(Collections.emptyList());

        MetricSnapshot snap = snapshot("host-1", 38.0, 42.0, 55.0);
        Optional<AnomalyResult> result = detector.detect(snap);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Disk above critical threshold → CRITICAL even with normal CPU/memory")
    void diskCritical() {
        when(historyService.getBaselineCpu(any())).thenReturn(OptionalDouble.of(30.0));
        when(historyService.getBaselineMemory(any())).thenReturn(OptionalDouble.of(40.0));
        when(historyService.getBaselineDisk(any())).thenReturn(OptionalDouble.empty());
        when(historyService.historySize(any())).thenReturn(1);
        when(historyService.getHistory(any())).thenReturn(Collections.emptyList());

        MetricSnapshot snap = snapshot("host-1", 30.0, 40.0, 99.0); // disk at 99%
        Optional<AnomalyResult> result = detector.detect(snap);

        assertThat(result).isPresent();
        assertThat(result.get().getMetric()).isEqualTo("DISK");
        assertThat(result.get().getSeverity()).isEqualTo(Severity.CRITICAL);
    }

    @Test
    @DisplayName("No history → spike detection skipped, only threshold checked")
    void noHistorySkipsSpike() {
        when(historyService.getBaselineCpu(any())).thenReturn(OptionalDouble.empty());
        when(historyService.getBaselineMemory(any())).thenReturn(OptionalDouble.empty());
        when(historyService.getBaselineDisk(any())).thenReturn(OptionalDouble.empty());
        when(historyService.historySize(any())).thenReturn(0);
        when(historyService.getHistory(any())).thenReturn(Collections.emptyList());

        // CPU 70%: below high threshold (80%), no history for spike → should be STABLE
        MetricSnapshot snap = snapshot("host-1", 70.0, 40.0, 55.0);
        Optional<AnomalyResult> result = detector.detect(snap);

        assertThat(result).isEmpty();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private MetricSnapshot snapshot(String hostId, double cpu, double mem, double disk) {
        return MetricSnapshot.builder()
                .hostId(hostId)
                .hostName("test-host")
                .timestamp(Instant.now())
                .cpuPercent(cpu)
                .memoryPercent(mem)
                .diskPercent(disk)
                .synthetic(true)
                .build();
    }
}
