package com.ossom.monitoring.scheduler;

import com.ossom.monitoring.model.MetricSnapshot;
import com.ossom.monitoring.service.AlertDispatcherService;
import com.ossom.monitoring.service.AnomalyDetectorService;
import com.ossom.monitoring.service.LlmExplainerService;
import com.ossom.monitoring.service.MetricsFetcherService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Fixed-delay scheduler that drives the full monitoring pipeline:
 *   fetchAll → detect anomaly → LLM explain → dispatch alert
 *
 * Each host is processed independently so one failure doesn't block others.
 * Pipeline duration is tracked via Micrometer Timer.
 */
@Component
@Slf4j
public class MetricsScheduler {

    private final MetricsFetcherService fetcher;
    private final AnomalyDetectorService detector;
    private final LlmExplainerService explainer;
    private final AlertDispatcherService dispatcher;
    private final Timer pipelineTimer;

    public MetricsScheduler(MetricsFetcherService fetcher,
                            AnomalyDetectorService detector,
                            LlmExplainerService explainer,
                            AlertDispatcherService dispatcher,
                            MeterRegistry meterRegistry) {
        this.fetcher    = fetcher;
        this.detector   = detector;
        this.explainer  = explainer;
        this.dispatcher = dispatcher;
        this.pipelineTimer = Timer.builder("monitoring.pipeline.duration")
                .description("End-to-end monitoring pipeline execution time")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${agent.poll-interval-ms:60000}",
               initialDelayString = "${agent.initial-delay-ms:5000}")
    public void run() {
        pipelineTimer.record(() -> {
            log.debug("Monitoring cycle starting...");
            List<MetricSnapshot> snapshots = fetcher.fetchAll();

            if (snapshots.isEmpty()) {
                log.warn("No snapshots returned — skipping cycle");
                return;
            }

            log.info("Processing {} host(s)", snapshots.size());

            for (MetricSnapshot snap : snapshots) {
                try {
                    processHost(snap);
                } catch (Exception ex) {
                    log.error("Unhandled error in pipeline for host {}: {}",
                            snap.getHostId(), ex.getMessage(), ex);
                }
            }

            log.debug("Monitoring cycle complete");
        });
    }

    private void processHost(MetricSnapshot snap) {
        detector.detect(snap)
                .map(anomaly -> {
                    log.info("Anomaly detected for {} — {} {} at {:.1f}%",
                            snap.getHostName(), anomaly.getSeverity(),
                            anomaly.getMetric(), anomaly.getCurrentValue());
                    return explainer.explain(anomaly);
                })
                .ifPresent(dispatcher::dispatch);
    }
}
