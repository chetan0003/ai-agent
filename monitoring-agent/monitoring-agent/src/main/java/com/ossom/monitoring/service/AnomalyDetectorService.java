package com.ossom.monitoring.service;

import com.ossom.monitoring.config.AgentProperties;
import com.ossom.monitoring.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Rule-based anomaly detector.
 *
 * Rules applied per metric:
 *   1. Absolute threshold breach (HIGH_USAGE / CRITICAL)
 *   2. Spike detection — delta from rolling baseline ≥ cpuSpikeDelta
 *   3. Sustained high — last 5 consecutive readings all above threshold
 *
 * The worst-severity finding across all metrics is returned.
 * Returns Optional.empty() if all metrics are STABLE (no alert needed).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnomalyDetectorService {

    private final MetricsHistoryService historyService;
    private final AgentProperties props;

    // Minimum readings before spike detection activates (avoid false positives on cold start)
    private static final int MIN_HISTORY_FOR_SPIKE = 3;

    public Optional<AnomalyResult> detect(MetricSnapshot current) {
        historyService.save(current);

        List<AnomalyResult> findings = new ArrayList<>();
        findings.addAll(analyzeCpu(current));
        findings.addAll(analyzeMemory(current));
        findings.addAll(analyzeDisk(current));

        if (findings.isEmpty()) {
            log.debug("Host {} — all metrics STABLE", current.getHostId());
            return Optional.empty();
        }

        // Return the single worst finding
        return findings.stream()
                .max(Comparator.comparingInt(r -> r.getSeverity().ordinal()));
    }

    // ── CPU Analysis ──────────────────────────────────────────────────────────

    private List<AnomalyResult> analyzeCpu(MetricSnapshot snap) {
        double cpu = snap.getCpuPercent();
        AgentProperties.ThresholdProps t = props.getThresholds();
        List<AnomalyResult> results = new ArrayList<>();

        OptionalDouble baseline = historyService.getBaselineCpu(snap.getHostId());

        // Rule 1: absolute threshold
        if (cpu >= t.getCpuCritical()) {
            results.add(build(snap, "CPU", cpu, baseline.orElse(cpu),
                    AnomalyType.HIGH_USAGE, Severity.CRITICAL));
        } else if (cpu >= t.getCpuHigh()) {
            results.add(build(snap, "CPU", cpu, baseline.orElse(cpu),
                    AnomalyType.HIGH_USAGE, Severity.HIGH));
        }

        // Rule 2: spike vs. baseline
        if (baseline.isPresent()
                && historyService.historySize(snap.getHostId()) >= MIN_HISTORY_FOR_SPIKE) {
            double delta = cpu - baseline.getAsDouble();
            if (delta >= t.getCpuSpikeDelta()) {
                Severity spikeSev = spikeToSeverity(delta);
                results.add(build(snap, "CPU", cpu, baseline.getAsDouble(),
                        AnomalyType.SPIKE, spikeSev));
            }
        }

        // Rule 3: sustained high (last 5 readings all above cpuHigh)
        List<MetricSnapshot> history = historyService.getHistory(snap.getHostId());
        if (history.size() >= 5) {
            boolean sustained = history.subList(history.size() - 5, history.size())
                    .stream()
                    .allMatch(s -> s.getCpuPercent() >= t.getCpuHigh());
            if (sustained) {
                results.add(build(snap, "CPU", cpu, baseline.orElse(cpu),
                        AnomalyType.SUSTAINED_HIGH, Severity.HIGH));
            }
        }

        return results;
    }

    // ── Memory Analysis ───────────────────────────────────────────────────────

    private List<AnomalyResult> analyzeMemory(MetricSnapshot snap) {
        double mem = snap.getMemoryPercent();
        AgentProperties.ThresholdProps t = props.getThresholds();
        List<AnomalyResult> results = new ArrayList<>();

        OptionalDouble baseline = historyService.getBaselineMemory(snap.getHostId());

        if (mem >= t.getMemoryCritical()) {
            results.add(build(snap, "MEMORY", mem, baseline.orElse(mem),
                    AnomalyType.HIGH_USAGE, Severity.CRITICAL));
        } else if (mem >= t.getMemoryHigh()) {
            results.add(build(snap, "MEMORY", mem, baseline.orElse(mem),
                    AnomalyType.HIGH_USAGE, Severity.HIGH));
        }

        // Memory spike detection (same delta threshold as CPU for simplicity)
        if (baseline.isPresent()
                && historyService.historySize(snap.getHostId()) >= MIN_HISTORY_FOR_SPIKE) {
            double delta = mem - baseline.getAsDouble();
            if (delta >= props.getThresholds().getCpuSpikeDelta()) {
                results.add(build(snap, "MEMORY", mem, baseline.getAsDouble(),
                        AnomalyType.SPIKE, spikeToSeverity(delta)));
            }
        }

        return results;
    }

    // ── Disk Analysis ─────────────────────────────────────────────────────────

    private List<AnomalyResult> analyzeDisk(MetricSnapshot snap) {
        double disk = snap.getDiskPercent();
        AgentProperties.ThresholdProps t = props.getThresholds();

        if (disk >= t.getDiskCritical()) {
            return List.of(build(snap, "DISK", disk, 0,
                    AnomalyType.HIGH_USAGE, Severity.CRITICAL));
        } else if (disk >= t.getDiskHigh()) {
            return List.of(build(snap, "DISK", disk, 0,
                    AnomalyType.HIGH_USAGE, Severity.HIGH));
        }

        return Collections.emptyList();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AnomalyResult build(MetricSnapshot snap, String metric,
                                double current, double baseline,
                                AnomalyType type, Severity severity) {
        double dev = baseline > 0 ? ((current - baseline) / baseline) * 100 : 0;
        log.info("Anomaly detected — host={} metric={} type={} severity={} current={}% baseline={}% dev={}%",
                snap.getHostId(), metric, type, severity,
                String.format("%.1f", current),
                String.format("%.1f", baseline),
                String.format("%.1f", dev));
        return AnomalyResult.builder()
                .hostId(snap.getHostId())
                .metric(metric)
                .currentValue(current)
                .baselineValue(baseline)
                .deviationPercent(dev)
                .type(type)
                .severity(severity)
                .snapshot(snap)
                .build();
    }

    private Severity spikeToSeverity(double delta) {
        if (delta >= 50) return Severity.CRITICAL;
        if (delta >= 40) return Severity.HIGH;
        if (delta >= 30) return Severity.MEDIUM;
        return Severity.LOW;
    }
}
