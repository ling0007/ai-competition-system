package com.eliza.aicompetition.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * A best-effort contention guard. Callers must retain a database correctness guard.
 * A null token means Redis was unavailable and the caller should use its DB fallback.
 */
@Service
public class RedisLockService {
    private static final Logger log = LoggerFactory.getLogger(RedisLockService.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
        "if redis.call('get', KEYS[1]) == ARGV[1] then "
            + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;

    public RedisLockService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public Lease acquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            if (Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, ttl))) {
                return new Lease(true, token);
            }
            log.info("redis_lock_contended key={}", key);
            return new Lease(false, null);
        } catch (Exception failure) {
            log.warn("redis_lock_acquire_failed key={} reason={}", key, failure.getClass().getSimpleName());
            return new Lease(true, null);
        }
    }

    public void release(String key, Lease lease) {
        if (lease == null || lease.ownerToken() == null) return;
        try {
            Long removed = redis.execute(RELEASE, List.of(key), lease.ownerToken());
            if (!Long.valueOf(1L).equals(removed)) log.info("redis_lock_owner_mismatch key={}", key);
        } catch (Exception failure) {
            log.warn("redis_lock_release_failed key={} reason={}", key, failure.getClass().getSimpleName());
        }
    }

    public record Lease(boolean allowed, String ownerToken) {}
}
