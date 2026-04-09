package com.ossom.monitoring;

import com.ossom.monitoring.channel.AlertChannel;
import com.ossom.monitoring.model.*;
import com.ossom.monitoring.repository.AlertRecordRepository;
import com.ossom.monitoring.service.AlertDispatcherService;
import com.ossom.monitoring.service.MetricsHistoryService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertDispatcherServiceTest {

    @Mock private MetricsHistoryService historyService;
    @Mock private AlertRecordRepository alertRecordRepository;
    @Mock private AlertChannel mockChannel;

    private AlertDispatcherService dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new AlertDispatcherService(
                historyService,
                alertRecordRepository,
                List.of(mockChannel),
                new SimpleMeterRegistry()
        );
    }

    @Test
    @DisplayName("CRITICAL alert with no cooldown → channel receives payload")
    void criticalAlertDispatched() {
        when(historyService.isInMaintenance(any())).thenReturn(false);
        when(historyService.isOnCooldown(any(), any())).thenReturn(false);
        when(mockChannel.supports(Severity.CRITICAL)).thenReturn(true);

        AlertPayload payload = buildPayload(Severity.CRITICAL, AnomalyType.HIGH_USAGE);
        dispatcher.dispatch(payload);

        verify(mockChannel, times(1)).send(payload);
        verify(historyService).setCooldown("host-1", "CPU");
        verify(alertRecordRepository).save(any());
    }

    @Test
    @DisplayName("Alert during maintenance window → channel NOT called")
    void maintenanceWindowSuppressesAlert() {
        when(historyService.isInMaintenance("host-1")).thenReturn(true);

        AlertPayload payload = buildPayload(Severity.HIGH, AnomalyType.SPIKE);
        dispatcher.dispatch(payload);

        verify(mockChannel, never()).send(any());
    }

    @Test
    @DisplayName("Alert within cooldown period → channel NOT called")
    void cooldownSuppressesAlert() {
        when(historyService.isInMaintenance(any())).thenReturn(false);
        when(historyService.isOnCooldown("host-1", "CPU")).thenReturn(true);

        AlertPayload payload = buildPayload(Severity.HIGH, AnomalyType.SPIKE);
        dispatcher.dispatch(payload);

        verify(mockChannel, never()).send(any());
    }

    @Test
    @DisplayName("STABLE anomaly type → always ignored")
    void stableAnomalyIgnored() {
        AlertPayload payload = buildPayload(Severity.LOW, AnomalyType.STABLE);
        dispatcher.dispatch(payload);

        verify(mockChannel, never()).send(any());
        verify(historyService, never()).isOnCooldown(any(), any());
    }

    @Test
    @DisplayName("Channel throws exception → other channels still called")
    void channelFailureDoesNotBlockOthers() {
        AlertChannel failingChannel = mock(AlertChannel.class);
        AlertChannel workingChannel = mock(AlertChannel.class);

        when(failingChannel.supports(any())).thenReturn(true);
        when(workingChannel.supports(any())).thenReturn(true);
        doThrow(new RuntimeException("Slack down")).when(failingChannel).send(any());

        when(historyService.isInMaintenance(any())).thenReturn(false);
        when(historyService.isOnCooldown(any(), any())).thenReturn(false);

        AlertDispatcherService multiChannelDispatcher = new AlertDispatcherService(
                historyService, alertRecordRepository,
                List.of(failingChannel, workingChannel),
                new SimpleMeterRegistry()
        );

        AlertPayload payload = buildPayload(Severity.HIGH, AnomalyType.SPIKE);
        multiChannelDispatcher.dispatch(payload);   // should NOT throw

        verify(workingChannel, times(1)).send(payload);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private AlertPayload buildPayload(Severity severity, AnomalyType type) {
        MetricSnapshot snap = MetricSnapshot.builder()
                .hostId("host-1").hostName("test-host")
                .timestamp(Instant.now())
                .cpuPercent(90.0).memoryPercent(60.0).diskPercent(50.0)
                .build();
        AnomalyResult anomaly = AnomalyResult.builder()
                .hostId("host-1").metric("CPU")
                .type(type).severity(severity)
                .currentValue(90.0).baselineValue(50.0).deviationPercent(80.0)
                .snapshot(snap)
                .build();
        return AlertPayload.builder()
                .anomaly(anomaly)
                .summary("Test alert")
                .rootCauses("[\"Root cause A\"]")
                .recommendedActions("[\"Action A\"]")
                .firedAt(Instant.now())
                .llmEnriched(false)
                .build();
    }
}
