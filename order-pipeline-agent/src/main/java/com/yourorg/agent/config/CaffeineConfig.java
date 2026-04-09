package com.yourorg.agent.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CaffeineConfig {

    /**
     * "metricsCache"  — stores OrderMetrics per region.
     *   TTL: 2 hours (covers two 15-min agent windows with headroom).
     *   Max 100 entries — one per region key.
     *
     * "decisionCache" — stores last AgentDecision per region.
     *   TTL: 30 minutes (drives alert-suppression logic).
     *   Max 100 entries.
     */
    @Bean
    public CacheManager cacheManager() {
        CaffeineCache metricsCache = buildCache("metricsCache", 120, 100);
        CaffeineCache decisionCache = buildCache("decisionCache", 30, 100);

        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(metricsCache, decisionCache));
        return manager;
    }

    private CaffeineCache buildCache(String name, long ttlMinutes, long maxSize) {
        return new CaffeineCache(name,
                Caffeine.newBuilder()
                        .expireAfterWrite(ttlMinutes, TimeUnit.MINUTES)
                        .maximumSize(maxSize)
                        .recordStats()       // exposes hit/miss via Micrometer
                        .build());
    }
}
