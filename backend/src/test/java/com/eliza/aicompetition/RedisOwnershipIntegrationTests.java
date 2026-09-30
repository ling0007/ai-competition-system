package com.eliza.aicompetition;

import com.eliza.aicompetition.service.DashboardCacheService;
import com.eliza.aicompetition.service.RedisLockService;
import com.eliza.aicompetition.service.RedisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.ArgumentMatchers.anyString;

/** Opt-in real Redis coverage; REDIS_TEST_PORT points to a disposable Redis instance. */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST_PORT", matches = "\\d+")
class RedisOwnershipIntegrationTests {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate template;
    private RedisLockService locks;

    @BeforeEach
    void connect() {
        var config = LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(500)).build();
        factory = new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("localhost", Integer.parseInt(System.getenv("REDIS_TEST_PORT"))),
            config);
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        locks = new RedisLockService(template);
    }

    @AfterEach
    void close() {
        if (factory != null) factory.destroy();
    }

    @Test
    void ownerReleaseIsAtomicAndOldOwnerCannotDeleteReplacement() throws Exception {
        String key = "test:phase55:lock:" + UUID.randomUUID();
        try {
            var first = locks.acquire(key, Duration.ofSeconds(2));
            assertTrue(first.allowed());
            assertNotNull(first.ownerToken());
            assertTrue(template.getExpire(key, TimeUnit.MILLISECONDS) > 0);
            assertFalse(locks.acquire(key, Duration.ofSeconds(2)).allowed());
            locks.release(key, new RedisLockService.Lease(true, "different-owner"));
            assertFalse(locks.acquire(key, Duration.ofSeconds(2)).allowed());
            locks.release(key, first);
            assertNull(template.opsForValue().get(key));

            var expired = locks.acquire(key, Duration.ofMillis(500));
            Thread.sleep(650);
            var replacement = locks.acquire(key, Duration.ofSeconds(2));
            assertTrue(replacement.allowed());
            assertNotEquals(expired.ownerToken(), replacement.ownerToken());
            locks.release(key, expired);
            assertEquals(replacement.ownerToken(), template.opsForValue().get(key));
            locks.release(key, replacement);
            assertNull(template.opsForValue().get(key));
        } finally {
            template.delete(key);
        }
    }

    @Test
    void cacheHitAndGlobalEpochInvalidationUseRealRedis() {
        RedisService redis = new RedisService(template);
        DashboardCacheService cache = new DashboardCacheService(redis, 120);
        long userId = Math.abs(UUID.randomUUID().getMostSignificantBits());
        String key = "dashboard:bootstrap:" + userId;
        try {
            assertNull(cache.getGlobal(userId, "student"));
            long revision = cache.epoch();
            cache.cacheGlobal(userId, "student", revision,
                Map.of("noticeOptions", List.of(), "userOptions", List.of()));
            assertTrue(template.getExpire(key) > 0);
            assertNotNull(cache.getGlobal(userId, "student"));
            assertNull(cache.getGlobal(userId, "admin"));
            cache.invalidateGlobalAfterCommit("test");
            assertNull(cache.getGlobal(userId, "student"));
        } finally {
            template.delete(key);
        }
    }

    @Test
    void connectionFailureAllowsDatabaseGuardToProceed() {
        StringRedisTemplate unavailable = mock(StringRedisTemplate.class);
        when(unavailable.opsForValue()).thenThrow(new IllegalStateException("redis down"));
        RedisLockService fallback = new RedisLockService(unavailable);
        var lease = fallback.acquire("test:phase55:unavailable", Duration.ofSeconds(1));
        assertTrue(lease.allowed());
        assertNull(lease.ownerToken());
        assertDoesNotThrow(() -> fallback.release("test:phase55:unavailable", lease));
    }

    @Test
    void failedInvalidationLeavesOnlyBoundedGlobalStaleness() throws Exception {
        RedisService redis = spy(new RedisService(template));
        DashboardCacheService cache = new DashboardCacheService(redis, 1);
        long userId = Math.abs(UUID.randomUUID().getLeastSignificantBits());
        String key = "dashboard:bootstrap:" + userId;
        try {
            long revision = cache.epoch();
            cache.cacheGlobal(userId, "student", revision,
                Map.of("noticeOptions", List.of(), "userOptions", List.of()));
            assertNotNull(cache.getGlobal(userId, "student"));
            doReturn(0L).when(redis).increment(anyString());
            cache.invalidateGlobalAfterCommit("simulated-redis-failure");
            assertNotNull(cache.getGlobal(userId, "student"));
            Thread.sleep(1300);
            assertNull(cache.getGlobal(userId, "student"));
        } finally {
            template.delete(key);
        }
    }

    @Test
    void refusedRedisConnectionFallsBackForCacheAndLock() {
        var unavailableFactory = new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("127.0.0.1", 1),
            LettuceClientConfiguration.builder().commandTimeout(Duration.ofMillis(200)).build());
        unavailableFactory.afterPropertiesSet();
        try {
            var unavailableTemplate = new StringRedisTemplate(unavailableFactory);
            unavailableTemplate.afterPropertiesSet();
            assertNull(new RedisService(unavailableTemplate).get("test:phase55:cache", Map.class));
            var lease = new RedisLockService(unavailableTemplate)
                .acquire("test:phase55:lock", Duration.ofSeconds(1));
            assertTrue(lease.allowed());
            assertNull(lease.ownerToken());
        } finally {
            unavailableFactory.destroy();
        }
    }
}
