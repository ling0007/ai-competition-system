package com.eliza.aicompetition.service;

import com.eliza.aicompetition.config.LlmProperties;
import com.eliza.aicompetition.dto.ai.AiCheckResult;
import com.eliza.aicompetition.dto.ai.AiParseResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Central LLM invocation service using DashScope's OpenAI-compatible API.
 *
 * <p>All methods return degraded fallback results when the LLM call fails,
 * so that transactional boundaries in callers are never broken by AI errors.</p>
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final RestTemplate restTemplate;
    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;

    public AiService(RestTemplate restTemplate, LlmProperties llmProperties, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void checkApiKey() {
        if (llmProperties.key() == null || llmProperties.key().isBlank()) {
            log.warn("DASHSCOPE_API_KEY is not set — AI features will operate in degraded mode.");
        } else {
            log.info("DashScope API key detected (length={}), AI features are ready.", llmProperties.key().length());
        }
    }

    // ========================================================================
    // Tool 1: parseNoticeTool
    // ========================================================================

    /** Maximum characters of notice text to send to the LLM. */
    private static final int PARSE_MAX_INPUT_CHARS = 4000;

    /**
     * Parse a competition notice text via LLM and extract structured information.
     */
    public AiParseResult parseNotice(String rawText) {
        // Truncate to avoid blowing up the context window
        String truncated = rawText.length() > PARSE_MAX_INPUT_CHARS
            ? rawText.substring(0, PARSE_MAX_INPUT_CHARS) + "\n...(truncated)"
            : rawText;

        String systemPrompt = "You are a competition notice parser. Return ONLY valid JSON, no explanation, no markdown fences.";
        String userPrompt = buildParseNoticePrompt(truncated);
        try {
            log.info("Calling LLM parseNotice: input length={}", truncated.length());
            String response = callLlm(systemPrompt, userPrompt);
            log.info("LLM parseNotice raw response (first 500 chars): {}",
                response.length() > 500 ? response.substring(0, 500) + "..." : response);
            AiParseResult result = objectMapper.readValue(cleanJson(response), AiParseResult.class);
            log.info("LLM parseNotice parsed: organizer={}, materials={}, keyPoints={}",
                result.organizer(),
                result.materials() != null ? result.materials().size() : 0,
                result.keyPoints() != null ? result.keyPoints().length() : 0);
            return result;
        } catch (Exception e) {
            log.error("LLM parseNotice failed — returning fallback result. Error: {}", e.getMessage());
            return fallbackParseNotice();
        }
    }

    // ========================================================================
    // Tool 2: checkMaterialTool
    // ========================================================================

    /**
     * Review submitted project material content via LLM.
     */
    public AiCheckResult checkMaterial(String projectContext, String extractedFileContents) {
        String systemPrompt = "You are an AI material reviewer for university competitions. Return ONLY valid JSON, no explanation, no markdown fences.";
        String userPrompt = buildCheckMaterialPrompt(projectContext, extractedFileContents);
        try {
            String response = callLlm(systemPrompt, userPrompt);
            log.info("LLM checkMaterial response length: {}", response.length());
            return objectMapper.readValue(cleanJson(response), AiCheckResult.class);
        } catch (Exception e) {
            log.error("LLM checkMaterial failed — returning fallback result. Error: {}", e.getMessage());
            return fallbackCheckMaterial();
        }
    }

    // ========================================================================
    // Core LLM HTTP call (OpenAI-compatible chat completions API)
    // ========================================================================

    private String callLlm(String systemPrompt, String userPrompt) {
        String url = llmProperties.baseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmProperties.key());

        Map<String, Object> requestBody = Map.of(
            "model", llmProperties.model(),
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ),
            "temperature", llmProperties.temperature(),
            "max_tokens", llmProperties.maxTokens()
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        log.debug("Calling LLM: model={}, prompt length={}", llmProperties.model(), userPrompt.length());
        ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

        if (response.getBody() == null) {
            throw new RuntimeException("LLM returned empty response body");
        }

        // Navigate: choices[0].message.content
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("LLM response has no choices");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        if (message == null) {
            throw new RuntimeException("LLM response choice has no message");
        }

        String content = (String) message.get("content");
        if (content == null || content.isBlank()) {
            throw new RuntimeException("LLM response message has no content");
        }

        return content;
    }

    // ========================================================================
    // Prompt builders (Chinese — users and competition notices are in Chinese)
    // ========================================================================

    private String buildParseNoticePrompt(String rawText) {
        return """
            你是一个竞赛通知解析器。请从以下通知文本中提取结构化信息。

            需要提取的字段：
            1. organizer：主办单位名称。如果文本中提到了某个学校、学院或机构作为主办方，提取它。
            2. deadline：申报截止日期。格式必须是 yyyy-MM-dd HH:mm。从文本中查找日期和时间信息。
            3. targetGroup：面向的参赛对象，如"本科生"、"研究生"、"教师"等。
            4. keyPoints：用中文简要概括通知的核心内容（100字以内）。
            5. materials：申报需要提交的材料清单。每项材料必须包含：
               - name：材料名称（中文）
               - description：对该材料的简要说明
               - isRequired：是否必须提交（true/false）

            注意：如果某个字段在文本中确实找不到对应信息，设为 null，不要编造。

            返回格式：纯 JSON（不要加 ```json``` 标记），例如：
            {"organizer":"某某大学","deadline":"2026-06-15 23:59","targetGroup":"本科生","keyPoints":"...","materials":[{"name":"申报书","description":"...","isRequired":true}]}

            通知文本：
            ----------
            %s
            ----------
            """.formatted(rawText);
    }

    private String buildCheckMaterialPrompt(String projectContext, String extractedFileContents) {
        return """
            你是一个竞赛材料审核助手，请根据项目信息和已提交材料的实际内容进行审核。

            项目信息：
            ----------
            %s
            ----------

            已提交材料的文本内容：
            ----------
            %s
            ----------

            评估要点：
            1. 材料内容是否完整，是否涵盖了要求的各个部分
            2. 内容是否专业、规范
            3. 是否存在明显问题或缺失部分

            返回 JSON 格式：
            - reviewResult: "pass"（通过）、"warning"（有问题需注意）或 "reject"（不通过）（字符串）
            - reviewComment: 详细的审核意见（中文，字符串）
            """.formatted(projectContext, extractedFileContents);
    }

    // ========================================================================
    // JSON helpers
    // ========================================================================

    /**
     * Strips markdown code fences and extracts the outermost JSON object
     * from an LLM response.
     */
    String cleanJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        // Remove markdown code fences: ```json ... ``` or ``` ... ```
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start > 0 && end > start) {
                trimmed = trimmed.substring(start, end).trim();
            }
        }
        // Find first { and last }
        int startBrace = trimmed.indexOf('{');
        int endBrace = trimmed.lastIndexOf('}');
        if (startBrace >= 0 && endBrace > startBrace) {
            return trimmed.substring(startBrace, endBrace + 1);
        }
        return trimmed;
    }

    // ========================================================================
    // Fallback strategies — never throw, always return a usable result
    // ========================================================================

    private AiParseResult fallbackParseNotice() {
        return new AiParseResult(
            null, null, null,
            "AI解析暂时不可用，请稍后重试。当前使用默认配置。",
            Collections.emptyList()
        );
    }

    private AiCheckResult fallbackCheckMaterial() {
        return new AiCheckResult(
            "warning",
            "AI审核服务暂时不可用，系统已自动标记为待人工复核。请人工审核项目材料。"
        );
    }
}
