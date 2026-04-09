package com.ossom.monitoring.controller;

import com.ossom.monitoring.model.AlertRecord;
import com.ossom.monitoring.model.Severity;
import com.ossom.monitoring.repository.AlertRecordRepository;
import com.ossom.monitoring.service.MetricsHistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * REST API for querying alert history and managing maintenance windows.
 *
 * Endpoints:
 *   GET  /api/alerts                        — all recent alerts
 *   GET  /api/alerts?hostId=X               — alerts for a specific host
 *   GET  /api/alerts?severity=CRITICAL      — alerts by severity
 *   GET  /api/alerts/stats                  — counts by severity
 *   POST /api/maintenance/{hostId}?minutes=N — set maintenance window
 *   DELETE /api/maintenance/{hostId}        — clear maintenance window
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AlertController {

    private final AlertRecordRepository alertRecordRepository;
    private final MetricsHistoryService historyService;

    // ── Alert History ─────────────────────────────────────────────────────────

    @GetMapping("/alerts")
    public List<AlertRecord> getAlerts(
            @RequestParam(required = false) String hostId,
            @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {

        if (hostId != null) {
            return alertRecordRepository.findByHostIdOrderByFiredAtDesc(hostId);
        }
        if (severity != null) {
            return alertRecordRepository.findBySeverityOrderByFiredAtDesc(severity);
        }
        if (from != null && to != null) {
            return alertRecordRepository.findByFiredAtBetweenOrderByFiredAtDesc(from, to);
        }
        return alertRecordRepository.findAll();
    }

    @GetMapping("/alerts/{id}")
    public ResponseEntity<AlertRecord> getAlert(@PathVariable Long id) {
        return alertRecordRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/alerts/stats")
    public Map<String, Object> getStats() {
        return Map.of(
                "total",    alertRecordRepository.count(),
                "critical", alertRecordRepository.countBySeverity(Severity.CRITICAL),
                "high",     alertRecordRepository.countBySeverity(Severity.HIGH),
                "medium",   alertRecordRepository.countBySeverity(Severity.MEDIUM),
                "low",      alertRecordRepository.countBySeverity(Severity.LOW)
        );
    }

    // ── Maintenance Windows ───────────────────────────────────────────────────

    @PostMapping("/maintenance/{hostId}")
    public ResponseEntity<Map<String, String>> setMaintenance(
            @PathVariable String hostId,
            @RequestParam(defaultValue = "60") int minutes) {
        historyService.setMaintenanceWindow(hostId, Duration.ofMinutes(minutes));
        return ResponseEntity.ok(Map.of(
                "status",  "set",
                "hostId",  hostId,
                "minutes", String.valueOf(minutes),
                "until",   Instant.now().plus(Duration.ofMinutes(minutes)).toString()
        ));
    }

    @DeleteMapping("/maintenance/{hostId}")
    public ResponseEntity<Map<String, String>> clearMaintenance(@PathVariable String hostId) {
        // Overwrite with a 1-second TTL to effectively clear it immediately
        historyService.setMaintenanceWindow(hostId, Duration.ofSeconds(1));
        return ResponseEntity.ok(Map.of("status", "cleared", "hostId", hostId));
    }

    @GetMapping("/maintenance/{hostId}")
    public ResponseEntity<Map<String, Object>> getMaintenanceStatus(@PathVariable String hostId) {
        boolean inMaintenance = historyService.isInMaintenance(hostId);
        return ResponseEntity.ok(Map.of(
                "hostId",        hostId,
                "inMaintenance", inMaintenance
        ));
    }

    // ── Health check ──────────────────────────────────────────────────────────

    @GetMapping("/status")
    public Map<String, String> status() {
        return Map.of("status", "UP", "service", "monitoring-agent");
    }
}
