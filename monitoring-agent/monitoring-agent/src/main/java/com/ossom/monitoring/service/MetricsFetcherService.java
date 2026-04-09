package com.ossom.monitoring.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ossom.monitoring.config.AgentProperties;
import com.ossom.monitoring.model.MetricSnapshot;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;

/**
 * Fetches current metrics from Site24x7.
 *
 * DEMO MODE: If no real API key is configured (value starts with "demo"),
 * synthetic snapshots are returned so the agent pipeline can be tested
 * without a live Site24x7 account.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsFetcherService {

    private final AgentProperties props;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    // Rolling counter used to inject occasional spikes in demo mode
    private int demoCallCount = 0;

    @CircuitBreaker(name = "site247", fallbackMethod = "fetchAllFallback")
    @Retry(name = "site247")
    public List<MetricSnapshot> fetchAll() {
        String apiKey = props.getSite247().getApiKey();

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("demo")) {
            log.info("[DEMO MODE] No real Site24x7 key found — returning synthetic snapshots");
            return generateDemoSnapshots();
        }

        return props.getSite247().getDeviceIds().stream()
                .map(this::fetchForDevice)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }

    private Optional<MetricSnapshot> fetchForDevice(String deviceId) {
        try {
            String url = props.getSite247().getBaseUrl() + "/current_status/" + deviceId;
            String response = restClient.get()
                    .uri(url)
                    .header("Authorization", "Zoho-authtoken " + props.getSite247().getApiKey())
                    .header("Accept", "application/json; version=2.0")
                    .retrieve()
                    .body(String.class);

            return Optional.of(parseSite247Response(deviceId, response));
        } catch (Exception ex) {
            log.error("Failed to fetch metrics for device {}: {}", deviceId, ex.getMessage());
            return Optional.empty();
        }
    }

    private MetricSnapshot parseSite247Response(String deviceId, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode data = root.path("data");
            String hostName = data.path("display_name").asText("unknown-" + deviceId);

            // Site24x7 returns performance_data as nested object
            JsonNode perf = data.path("performance_data");
            double cpu    = perf.path("cpu_used_percent").asDouble(0);
            double memory = perf.path("mem_used_percent").asDouble(0);
            double disk   = perf.path("disk_used_percent").asDouble(0);

            return MetricSnapshot.builder()
                    .hostId(deviceId)
                    .hostName(hostName)
                    .timestamp(Instant.now())
                    .cpuPercent(cpu)
                    .memoryPercent(memory)
                    .diskPercent(disk)
                    .synthetic(false)
                    .build();
        } catch (Exception ex) {
            log.error("Error parsing Site24x7 response for {}: {}", deviceId, ex.getMessage());
            return MetricSnapshot.builder()
                    .hostId(deviceId)
                    .hostName("parse-error")
                    .timestamp(Instant.now())
                    .synthetic(true)
                    .build();
        }
    }

    // ── Demo / synthetic mode ──────────────────────────────────────────────

    private List<MetricSnapshot> generateDemoSnapshots() {
        demoCallCount++;
        List<MetricSnapshot> snapshots = new ArrayList<>();

        // Host 1: normal baseline
        snapshots.add(MetricSnapshot.builder()
                .hostId("demo-host-01")
                .hostName("prod-app-server-1")
                .timestamp(Instant.now())
                .cpuPercent(normalCpu(demoCallCount))
                .memoryPercent(45.0 + random(-5, 5))
                .diskPercent(55.0)
                .synthetic(true)
                .build());

        // Host 2: simulate a slow memory climb
        snapshots.add(MetricSnapshot.builder()
                .hostId("demo-host-02")
                .hostName("prod-db-server-1")
                .timestamp(Instant.now())
                .cpuPercent(30.0 + random(-5, 5))
                .memoryPercent(60.0 + (demoCallCount * 3.0) % 40)   // climbs then resets
                .diskPercent(72.0)
                .synthetic(true)
                .build());

        return snapshots;
    }

    /** Returns a CPU value with a spike injected every 5 calls (call 5 = 92%) */
    private double normalCpu(int callCount) {
        if (callCount % 5 == 0) {
            log.info("[DEMO] Injecting CPU spike on call #{}", callCount);
            return 90.0 + random(0, 8);
        }
        return 35.0 + random(-10, 15);
    }

    private double random(double min, double max) {
        return min + Math.random() * (max - min);
    }

    @SuppressWarnings("unused")
    private List<MetricSnapshot> fetchAllFallback(Exception ex) {
        log.warn("Site24x7 circuit breaker OPEN — pipeline paused. Cause: {}", ex.getMessage());
        return Collections.emptyList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Site247Response(String code, Object data) {}
}
