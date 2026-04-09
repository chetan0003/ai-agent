package com.ossom.monitoring.service;

import com.ossom.monitoring.config.AgentProperties;
import com.ossom.monitoring.model.MetricSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Persists rolling metric history in Redis and manages per-host alert cooldowns.
 *
 * Keys used:
 *   metrics:history:{hostId}       → LIST of MetricSnapshot (capped at windowSize)
 *   alert:cooldown:{hostId}:{metric} → STRING "1" with TTL = cooldownMinutes
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MetricsHistoryService {

    private static final String HISTORY_PREFIX  = "metrics:history:";
    private static final String COOLDOWN_PREFIX = "alert:cooldown:";

    private final RedisTemplate<String, MetricSnapshot> metricRedisTemplate;
    private final RedisTemplate<String, String> stringRedisTemplate;
    private final AgentProperties props;

    // ── History ──────────────────────────────────────────────────────────────

    public void save(MetricSnapshot snapshot) {
        String key = HISTORY_PREFIX + snapshot.getHostId();
        try {
            ListOperations<String, MetricSnapshot> ops = metricRedisTemplate.opsForList();
            ops.rightPush(key, snapshot);
            ops.trim(key, -props.getHistory().getWindowSize(), -1);
            metricRedisTemplate.expire(key, Duration.ofHours(24));
        } catch (Exception ex) {
            log.warn("Could not save snapshot to Redis for host {}: {}",
                    snapshot.getHostId(), ex.getMessage());
        }
    }

    public List<MetricSnapshot> getHistory(String hostId) {
        String key = HISTORY_PREFIX + hostId;
        try {
            List<MetricSnapshot> list = metricRedisTemplate.opsForList().range(key, 0, -1);
            return list != null ? list : Collections.emptyList();
        } catch (Exception ex) {
            log.warn("Could not read history from Redis for host {}: {}", hostId, ex.getMessage());
            return Collections.emptyList();
        }
    }

    public OptionalDouble getBaselineCpu(String hostId) {
        return getHistory(hostId).stream()
                .mapToDouble(MetricSnapshot::getCpuPercent)
                .average();
    }

    public OptionalDouble getBaselineMemory(String hostId) {
        return getHistory(hostId).stream()
                .mapToDouble(MetricSnapshot::getMemoryPercent)
                .average();
    }

    public OptionalDouble getBaselineDisk(String hostId) {
        return getHistory(hostId).stream()
                .mapToDouble(MetricSnapshot::getDiskPercent)
                .average();
    }

    public int historySize(String hostId) {
        return getHistory(hostId).size();
    }

    // ── Cooldown (dedup) ──────────────────────────────────────────────────────

    public boolean isOnCooldown(String hostId, String metric) {
        String key = COOLDOWN_PREFIX + hostId + ":" + metric;
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(key));
        } catch (Exception ex) {
            log.warn("Redis cooldown check failed for {} {}: {}", hostId, metric, ex.getMessage());
            return false;  // fail open — allow alert through
        }
    }

    public void setCooldown(String hostId, String metric) {
        String key = COOLDOWN_PREFIX + hostId + ":" + metric;
        try {
            stringRedisTemplate.opsForValue().set(key, "1",
                    Duration.ofMinutes(props.getHistory().getCooldownMinutes()));
        } catch (Exception ex) {
            log.warn("Redis cooldown set failed for {} {}: {}", hostId, metric, ex.getMessage());
        }
    }

    // ── Maintenance window support ────────────────────────────────────────────

    public void setMaintenanceWindow(String hostId, Duration duration) {
        String key = "maintenance:" + hostId;
        stringRedisTemplate.opsForValue().set(key, "1", duration);
        log.info("Maintenance window set for host {} — duration {}", hostId, duration);
    }

    public boolean isInMaintenance(String hostId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey("maintenance:" + hostId));
    }
}
