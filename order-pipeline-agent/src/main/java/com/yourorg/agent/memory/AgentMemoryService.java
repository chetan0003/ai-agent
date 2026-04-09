package com.yourorg.agent.memory;

import com.yourorg.agent.agent.AgentDecision;
import com.yourorg.agent.model.AgentDecisionEntity;
import com.yourorg.agent.model.OrderMetrics;
import com.yourorg.agent.model.enums.AnomalyType;
import com.yourorg.agent.model.enums.Severity;
import com.yourorg.agent.repository.AgentDecisionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AgentMemoryService {

    private final CacheManager cacheManager;
    private final AgentDecisionRepository decisionRepository;

    private static final String METRICS_CACHE  = "metricsCache";
    private static final String DECISION_CACHE = "decisionCache";

    // ── Short-term memory (Caffeine) ──────────────────────────────────────────

    public void saveMetrics(String region, OrderMetrics metrics) {
        cache(METRICS_CACHE).put(region, metrics);
        log.debug("Saved metrics to Caffeine cache for region: {}", region);
    }

    public Optional<OrderMetrics> getPreviousMetrics(String region) {
        Cache.ValueWrapper wrapper = cache(METRICS_CACHE).get(region);
        return Optional.ofNullable(wrapper).map(v -> (OrderMetrics) v.get());
    }

    public void cacheDecision(String region, AgentDecision decision) {
        cache(DECISION_CACHE).put(region, decision);
        log.debug("Cached decision in Caffeine for region: {}", region);
    }

    public Optional<AgentDecision> getLastDecision(String region) {
        Cache.ValueWrapper wrapper = cache(DECISION_CACHE).get(region);
        return Optional.ofNullable(wrapper).map(v -> (AgentDecision) v.get());
    }

    // ── Long-term memory (PostgreSQL) ─────────────────────────────────────────

    public void saveDecision(String region, AgentDecision decision) {
        // Keep in Caffeine for fast alert-suppression lookups
        cacheDecision(region, decision);

        // Persist to PostgreSQL for audit trail and trend analysis
        AgentDecisionEntity entity = AgentDecisionEntity.builder()
                .region(region)
                .anomalyDetected(decision.isAnomalyDetected())
                .anomalyType(parseAnomalyType(decision.getAnomalyType()))
                .severity(parseSeverity(decision.getSeverity()))
                .reasoning(decision.getReasoning())
                .recommendedAction(decision.getRecommendedAction())
                .decidedAt(LocalDateTime.now())
                .build();

        decisionRepository.save(entity);
        log.debug("Persisted decision to PostgreSQL for region: {}", region);
    }

    /**
     * Alert suppression: returns true if the same anomaly was already alerted
     * within the last 30 minutes (decisionCache TTL = 30 min).
     * Caffeine eviction handles the window automatically — no manual time check needed.
     */
    public boolean wasRecentlyAlerted(String region, String anomalyType) {
        return getLastDecision(region)
                .map(d -> anomalyType.equalsIgnoreCase(d.getAnomalyType())
                        && d.isAnomalyDetected())
                .orElse(false);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Cache cache(String name) {
        Cache c = cacheManager.getCache(name);
        if (c == null) throw new IllegalStateException("Cache not found: " + name);
        return c;
    }

    private AnomalyType parseAnomalyType(String value) {
        if (value == null) return null;
        try { return AnomalyType.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException e) { return AnomalyType.OTHER; }
    }

    private Severity parseSeverity(String value) {
        if (value == null) return null;
        try { return Severity.valueOf(value.toUpperCase()); }
        catch (IllegalArgumentException e) { return Severity.LOW; }
    }
}
