package com.eliza.aicompetition.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis cache and simple counter access. Distributed locks live in RedisLockService.
 * <p>
 * <b>核心设计原则</b>：Redis 不可用时不影响业务。
 * 所有方法内部 try-catch 包裹，异常时返回安全默认值，仅记录 WARN 日志。
 * </p>
 * <p>
 * <b>序列化策略</b>：使用 Jackson ObjectMapper 手动序列化为 JSON 字符串存储。
 * 避免 GenericJackson2JsonRedisSerializer 的 @class 元数据问题
 * 和 LinkedHashMap/Object 类型序列化兼容性问题。
 * </p>
 */
@Service
public class RedisService {

    private static final Logger log = LoggerFactory.getLogger(RedisService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 写入缓存并设置过期时间。
     *
     * @param key        缓存键
     * @param value      缓存值（通过 Jackson 序列化为 JSON）
     * @param ttlSeconds 过期秒数
     */
    public void setWithExpire(String key, Object value, long ttlSeconds) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redisTemplate.opsForValue().set(key, json, ttlSeconds, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("Redis SETEX 序列化失败: key={}, error={}", key, e.getMessage());
        } catch (Exception e) {
            log.warn("Redis SETEX 失败: key={}, error={}", key, e.getMessage());
        }
    }

    /**
     * 读取缓存并反序列化为目标类型。
     *
     * @param key   缓存键
     * @param clazz 目标类型
     * @param <T>   泛型
     * @return 缓存值，未命中或异常时返回 null
     */
    public <T> T get(String key, Class<T> clazz) {
        try {
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.warn("Redis GET 反序列化失败: key={}, error={}", key, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Redis GET 失败: key={}, error={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 自增计数器。
     *
     * @param key 计数器键
     * @return 自增后的值，异常时返回 0
     */
    public long increment(String key) {
        try {
            Long result = redisTemplate.opsForValue().increment(key);
            return result != null ? result : 0;
        } catch (Exception e) {
            log.warn("Redis INCR 失败: key={}, error={}", key, e.getMessage());
            return 0;
        }
    }
}
