package com.eliza.aicompetition.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 配置。
 * <ul>
 *   <li>Key 使用 {@link StringRedisSerializer}，可读性强，方便调试和集群 key 分布。</li>
 *   <li>Value 使用 {@link GenericJackson2JsonRedisSerializer}，支持任意 Java 对象序列化，
 *       不依赖 Java 原生序列化（跨语言、跨版本更安全）。</li>
 *   <li>当前缓存和锁分别通过 RedisService 与 RedisLockService 的 StringRedisTemplate 实现；
 *       此对象模板保留供需要 JSON 对象模板的调用方，当前业务未使用。</li>
 * </ul>
 */
@Configuration
public class RedisConfig {

    /**
     * 创建 Jackson JSON 序列化的 RedisTemplate。
     *
     * @param factory Redis 连接工厂（Lettuce）
     * @return 配置好的 RedisTemplate 实例
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key 使用 String 序列化 —— 可读、可集群分片
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 使用 Jackson JSON 序列化 —— 不依赖 Java 序列化
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
