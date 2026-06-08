package com.eliza.aicompetition.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code llm.api.*} properties from application.properties.
 */
@ConfigurationProperties(prefix = "llm.api")
public record LlmProperties(
    String baseUrl,
    String key,
    String model,
    Double temperature,
    Integer maxTokens
) {}
