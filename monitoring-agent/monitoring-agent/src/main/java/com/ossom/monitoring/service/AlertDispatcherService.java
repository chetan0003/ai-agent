package com.ossom.monitoring.service;

import com.ossom.monitoring.channel.AlertChannel;
import com.ossom.monitoring.model.*;
import com.ossom.monitoring.repository.AlertRecordRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Orchestrates alert delivery:
 *   1. Skip STABLE anomalies
 *   2. Check maintenance window
 *   3. Check per-host+metric cooldown (dedup)
 *   4. Fan out to all registered AlertChannel implementations
 *   5. Persist to DB
 *   6. Record Micrometer metric
 */
@Service
@Slf4j
public class AlertDispatcherService {

    private final MetricsHistoryService historyService;
    private final AlertRecordRepository alertRecordRepository;
    private final List<AlertChannel> channels;
    private final Counter alertsFired;
    private final Counter alertsSuppressed;

    public AlertDispatcherService(MetricsHistoryService historyService,
                                  AlertRecordRepository alertRecordRepository,
                                  List<AlertChannel> channels,
                                  MeterRegistry meterRegistry) {
        this.historyService = historyService;
        this.alertRecordRepository = alertRecordRepository;
        this.channels = channels;
        this.alertsFired = Counter.builder("monitoring.alerts.fired")
                .description("Total alerts dispatched")
                .register(meterRegistry);
        this.alertsSuppressed = Counter.builder("monitoring.alerts.suppressed")
                .description("Alerts suppressed by cooldown or maintenance")
                .register(meterRegistry);
    }

    public void dispatch(AlertPayload payload) {
        AnomalyResult anomaly = payload.getAnomaly();

        // 1. Skip STABLE
        if (anomaly.getType() == AnomalyType.STABLE) return;

        // 2. Maintenance window
        if (historyService.isInMaintenance(anomaly.getHostId())) {
            log.info("Alert suppressed — host {} is in maintenance window", anomaly.getHostId());
            alertsSuppressed.increment();
            return;
        }

        // 3. Cooldown check
        if (historyService.isOnCooldown(anomaly.getHostId(), anomaly.getMetric())) {
            log.debug("Alert suppressed (cooldown active) — host={} metric={}",
                    anomaly.getHostId(), anomaly.getMetric());
            alertsSuppressed.increment();
            return;
        }

        log.info("Dispatching {} alert for {} / {} ({})",
                anomaly.getSeverity(), anomaly.getSnapshot().getHostName(),
                anomaly.getMetric(), anomaly.getType());

        // 4. Fan out to channels
        int channelCount = 0;
        for (AlertChannel channel : channels) {
            if (channel.supports(anomaly.getSeverity())) {
                try {
                    channel.send(payload);
                    channelCount++;
                } catch (Exception ex) {
                    log.error("Alert channel {} failed: {}",
                            channel.getClass().getSimpleName(), ex.getMessage());
                }
            }
        }

        // 5. Persist
        persistAlert(payload);

        // 6. Set cooldown + metrics
        historyService.setCooldown(anomaly.getHostId(), anomaly.getMetric());
        alertsFired.increment();

        log.info("Alert dispatched to {} channel(s)", channelCount);
    }

    private void persistAlert(AlertPayload payload) {
        try {
            AnomalyResult a = payload.getAnomaly();
            AlertRecord record = AlertRecord.builder()
                    .hostId(a.getHostId())
                    .hostName(a.getSnapshot().getHostName())
                    .metric(a.getMetric())
                    .anomalyType(a.getType())
                    .severity(a.getSeverity())
                    .currentValue(a.getCurrentValue())
                    .baselineValue(a.getBaselineValue())
                    .deviationPercent(a.getDeviationPercent())
                    .summary(payload.getSummary())
                    .rootCauses(payload.getRootCauses())
                    .recommendedActions(payload.getRecommendedActions())
                    .llmEnriched(payload.isLlmEnriched())
                    .firedAt(payload.getFiredAt())
                    .build();
            alertRecordRepository.save(record);
        } catch (Exception ex) {
            log.error("Failed to persist alert record: {}", ex.getMessage());
        }
    }
}
