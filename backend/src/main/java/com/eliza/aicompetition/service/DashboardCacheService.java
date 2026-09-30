package com.eliza.aicompetition.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Caches only global bootstrap fields. Project, progress and AI state are always read with current membership. */
@Service
public class DashboardCacheService {
    private static final Logger log = LoggerFactory.getLogger(DashboardCacheService.class);
    private static final String EPOCH = "dashboard:bootstrap:global-epoch";
    private final RedisService redis;
    private final long ttlSeconds;

    public DashboardCacheService(RedisService redis,
            @Value("${dashboard.cache.ttl-seconds:120}") long ttlSeconds) {
        this.redis = redis;
        this.ttlSeconds = ttlSeconds;
    }

    public Map<String, Object> getGlobal(Long userId, String role) {
        if (userId == null) return null;
        Map<?, ?> cached = redis.get(key(userId), Map.class);
        Object cachedEpoch = cached == null ? null : cached.get("_epoch");
        if (cached == null || !Objects.equals(cached.get("_role"), role)
                || !(cachedEpoch instanceof Number number) || number.longValue() != epoch()) {
            log.debug("dashboard_cache_miss userId={}", userId);
            return null;
        }
        log.debug("dashboard_cache_hit userId={}", userId);
        Map<String, Object> global = new LinkedHashMap<>();
        global.put("notice", cached.get("notice"));
        global.put("noticeOptions", cached.get("noticeOptions"));
        global.put("userOptions", cached.get("userOptions"));
        return global;
    }

    public long epoch() {
        Long value = redis.get(EPOCH, Long.class);
        return value == null ? 0 : value;
    }

    public void cacheGlobal(Long userId, String role, long observedEpoch, Map<String, Object> global) {
        if (userId == null || observedEpoch != epoch()) return;
        Map<String, Object> cached = new LinkedHashMap<>(global);
        cached.put("_role", role);
        cached.put("_epoch", observedEpoch);
        redis.setWithExpire(key(userId), cached, ttlSeconds);
    }

    public void invalidateGlobalAfterCommit(String cause) {
        Runnable invalidate = () -> {
            long revision = redis.increment(EPOCH);
            if (revision == 0) {
                log.warn("dashboard_cache_invalidation_failed scope=global cause={}", cause);
            } else {
                log.info("dashboard_cache_invalidated scope=global cause={} revision={}", cause, revision);
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidate.run(); }
            });
        } else {
            invalidate.run();
        }
    }

    private String key(Long userId) {
        return "dashboard:bootstrap:" + userId;
    }
}
