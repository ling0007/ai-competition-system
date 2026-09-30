package com.eliza.aicompetition.service;

import com.eliza.aicompetition.config.LlmProperties;
import com.eliza.aicompetition.common.AiDiagnosticCode;
import com.eliza.aicompetition.common.AiTaskBudget;
import com.eliza.aicompetition.dto.ai.AiCheckResponse;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.text.Normalizer;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

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
    private static final int PARSE_CHUNK_CHARS = 4000;
    private static final int PARSE_MAX_CHUNKS = 8;

    /**
     * Parse a competition notice text via LLM and extract structured information.
     */
    public AiParseResult parseNotice(String rawText) {
        return parseNoticeMeasured(rawText).result();
    }

    /** llmMs measures only the external HTTP request, including failures, not prompt or JSON parsing. */
    public record CallResult<T>(T result, long llmMs, boolean llmAttempted, boolean degraded,
                                AiDiagnosticCode fallbackReason, int callCount) {
        public CallResult(T result, long llmMs, boolean llmAttempted, boolean degraded) {
            this(result, llmMs, llmAttempted, degraded, null, llmAttempted ? 1 : 0);
        }
        public CallResult(T result, long llmMs, boolean llmAttempted, boolean degraded,
                          AiDiagnosticCode fallbackReason) {
            this(result, llmMs, llmAttempted, degraded, fallbackReason, llmAttempted ? 1 : 0);
        }
    }

    public String textModel() { return llmProperties.model(); }
    public String visionModel() { return llmProperties.visionModel(); }
    public static final String NOTICE_PROMPT_VERSION = "notice-parse-v3-stage";
    public static final String MATERIAL_PROMPT_VERSION = "material-check-v1";
    public static final String OCR_PROMPT_VERSION = "pdf-ocr-v1";

    /** Hash the actual stable template with fixed placeholders, never user input. */
    public String noticePromptHash() {
        return promptHash("You are a competition notice parser. Return ONLY valid JSON, no explanation, no markdown fences."
            + buildParseNoticePrompt("{NOTICE_TEXT}"));
    }

    public String materialPromptHash() {
        return promptHash("You are an AI material reviewer for university competitions. Return ONLY valid JSON, no explanation, no markdown fences."
            + buildCheckMaterialPrompt("{PROJECT_CONTEXT}", "{MATERIAL_CONTENTS}"));
    }

    private static String promptHash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static final class CallTimer {
        private long llmMs;
        private boolean attempted;
        private int calls;
    }

    public CallResult<AiParseResult> parseNoticeMeasured(String rawText) {
        String systemPrompt = "You are a competition notice parser. Return ONLY valid JSON, no explanation, no markdown fences.";
        CallTimer timer = new CallTimer();
        try {
            List<String> chunks = splitNotice(rawText);
            if (chunks.size() > PARSE_MAX_CHUNKS) {
                return new CallResult<>(fallbackParseNotice(), 0, false, true,
                    AiDiagnosticCode.INPUT_TOO_LONG, 0);
            }
            List<AiParseResult> parts = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                if (AiTaskBudget.exhausted()) throw new IllegalStateException("task deadline exhausted");
                log.info("parse_chunk taskId={} index={} total={} chars={}", AiTaskBudget.taskId(),
                    i + 1, chunks.size(), chunks.get(i).length());
                String response = callLlm(systemPrompt, buildParseNoticePrompt(chunks.get(i)), timer);
                parts.add(objectMapper.readValue(cleanJson(response), AiParseResult.class));
            }
            AiParseResult merged = mergeNotice(parts, chunks);
            String formalTitle = extractFormalTitle(rawText);
            if (formalTitle != null) {
                merged = new AiParseResult(formalTitle, merged.organizer(), merged.deadline(),
                    merged.targetGroup(), merged.keyPoints(), merged.materials());
            }
            return new CallResult<>(merged, timer.llmMs, timer.attempted, false, null, timer.calls);
        } catch (Exception e) {
            log.error("LLM parseNotice failed — returning fallback result: {}", e.getClass().getSimpleName());
            return new CallResult<>(fallbackParseNotice(), timer.llmMs, timer.attempted, true,
                AiTaskBudget.exhausted() ? AiDiagnosticCode.TASK_TIMEOUT : classify(e, timer.attempted), timer.calls);
        }
    }

    private List<String> splitNotice(String text) {
        List<String> chunks = new ArrayList<>();
        String[] sections = text.split("(?=【PDF第\\d+页】)");
        StringBuilder current = new StringBuilder();
        for (String section : sections) {
            if (section.isEmpty()) continue;
            String source = section.startsWith("【PDF第") ? section.substring(0, section.indexOf('】') + 1) : "";
            int offset = 0;
            while (offset < section.length()) {
                int room = PARSE_CHUNK_CHARS - current.length();
                if (room < 200) { chunks.add(current.toString()); current.setLength(0); room = PARSE_CHUNK_CHARS; }
                if (offset > 0 && !source.isEmpty() && current.isEmpty()) {
                    current.append(source).append('\n');
                    room = PARSE_CHUNK_CHARS - current.length();
                }
                int end = Math.min(section.length(), offset + room);
                if (end < section.length()) {
                    int newline = section.lastIndexOf('\n', end);
                    if (newline > offset + room / 2) end = newline + 1;
                }
                current.append(section, offset, end);
                offset = end;
            }
        }
        if (!current.isEmpty() || chunks.isEmpty()) chunks.add(current.toString());
        return chunks;
    }

    private AiParseResult mergeNotice(List<AiParseResult> parts, List<String> chunks) {
        String title = null, organizer = null, deadline = null, target = null;
        boolean deadlineConflict = false;
        StringBuilder notes = new StringBuilder();
        LinkedHashMap<String, AiParseResult.AiMaterialRequirement> materials = new LinkedHashMap<>();
        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
            AiParseResult part = parts.get(partIndex);
            var pageMatcher = java.util.regex.Pattern.compile("【PDF第(\\d+)页】").matcher(chunks.get(partIndex));
            List<String> pages = new ArrayList<>();
            while (pageMatcher.find()) pages.add(pageMatcher.group(1));
            String source = pages.isEmpty() ? "" : "【来源：PDF第" + String.join("、", pages) + "页】";
            if (title == null && part.title() != null && !source.isEmpty()) notes.append("【标题").append(source).append("】");
            if (organizer == null && part.organizer() != null && !source.isEmpty()) notes.append("【主办方").append(source).append("】");
            if (deadline == null && part.deadline() != null && !source.isEmpty()) notes.append("【截止时间").append(source).append("】");
            title = mergeScalar("标题", title, part.title(), notes);
            organizer = mergeScalar("主办方", organizer, part.organizer(), notes);
            if (deadline != null && part.deadline() != null && !part.deadline().isBlank()
                    && !deadline.equals(part.deadline())) deadlineConflict = true;
            deadline = mergeScalar("截止时间", deadline, part.deadline(), notes);
            target = mergeScalar("对象", target, part.targetGroup(), notes);
            if (part.keyPoints() != null && notes.length() < 300)
                notes.append(part.keyPoints(), 0, Math.min(part.keyPoints().length(), 100)).append(' ');
            if (part.materials() != null) for (var item : part.materials()) {
                if (item == null || item.name() == null || item.name().isBlank()) continue;
                String key = Normalizer.normalize(item.name(), Normalizer.Form.NFKC).replaceAll("\\s+", "");
                var prior = materials.get(key);
                if (prior == null) materials.put(key, new AiParseResult.AiMaterialRequirement(
                    item.name(), (item.description() == null ? "" : item.description()) + source, item.isRequired()));
                else materials.put(key, new AiParseResult.AiMaterialRequirement(prior.name(),
                    prior.description() == null ? item.description() : prior.description(),
                    prior.isRequired() || item.isRequired()));
            }
        }
        return new AiParseResult(title, organizer, deadlineConflict ? null : deadline, target,
            notes.isEmpty() ? null : notes.substring(0, Math.min(300, notes.length())),
            new ArrayList<>(materials.values()));
    }

    private String mergeScalar(String label, String current, String next, StringBuilder notes) {
        if (next == null || next.isBlank()) return current;
        if (current == null || current.isBlank()) return next;
        if (!Normalizer.normalize(current, Normalizer.Form.NFKC).replaceAll("\\s+", "")
            .equals(Normalizer.normalize(next, Normalizer.Form.NFKC).replaceAll("\\s+", ""))) {
            notes.append("【跨段").append(label).append("冲突，请人工核对】");
            if ("截止时间".equals(label)) return null;
        }
        return current;
    }

    /**
     * Preserve a formal multi-line title from the first page. Vision OCR commonly keeps the lines,
     * while a structuring model may omit a slogan between "关于" and the final "通知".
     */
    private String extractFormalTitle(String rawText) {
        if (rawText == null || rawText.isBlank()) return null;
        int secondPage = rawText.indexOf("【PDF第2页】");
        String firstPage = secondPage >= 0 ? rawText.substring(0, secondPage)
            : rawText.substring(0, Math.min(rawText.length(), 2_000));
        List<String> lines = firstPage.lines().map(String::trim)
            .filter(line -> !line.isBlank() && !line.startsWith("【PDF第"))
            .toList();
        for (int start = 0; start < lines.size(); start++) {
            if (!lines.get(start).startsWith("关于")) continue;
            StringBuilder candidate = new StringBuilder();
            for (int end = start; end < Math.min(lines.size(), start + 4); end++) {
                candidate.append(lines.get(end));
                if (lines.get(end).endsWith("通知")) {
                    return candidate.length() <= 200 ? candidate.toString() : null;
                }
            }
            return null;
        }
        return null;
    }

    // ========================================================================
    // Tool 2: checkMaterialTool
    // ========================================================================

    /**
     * Review submitted project material content via LLM.
     */
    public AiCheckResponse checkMaterial(String projectContext, String extractedFileContents) {
        return checkMaterialMeasured(projectContext, extractedFileContents).result();
    }

    public CallResult<AiCheckResponse> checkMaterialMeasured(String projectContext, String extractedFileContents) {
        String systemPrompt = "You are an AI material reviewer for university competitions. Return ONLY valid JSON, no explanation, no markdown fences.";
        String userPrompt = buildCheckMaterialPrompt(projectContext, extractedFileContents);
        CallTimer timer = new CallTimer();
        try {
            String response = callLlm(systemPrompt, userPrompt, timer);
            log.info("LLM checkMaterial response length: {}", response.length());
            return new CallResult<>(objectMapper.readValue(cleanJson(response), AiCheckResponse.class),
                timer.llmMs, timer.attempted, false);
        } catch (Exception e) {
            log.error("LLM checkMaterial failed — returning fallback result: {}", e.getClass().getSimpleName());
            return new CallResult<>(fallbackCheckMaterial(), timer.llmMs, timer.attempted, true,
                AiTaskBudget.remainingMillis() < 1_000 ? AiDiagnosticCode.TASK_TIMEOUT : classify(e, timer.attempted));
        }
    }

    private AiDiagnosticCode classify(Exception failure, boolean attempted) {
        if (!attempted && (llmProperties.key() == null || llmProperties.key().isBlank()))
            return AiDiagnosticCode.API_KEY_MISSING;
        if (failure instanceof ResourceAccessException) {
            Throwable cause = failure;
            while (cause != null) {
                if (cause instanceof java.net.SocketTimeoutException)
                    return AiDiagnosticCode.MODEL_TIMEOUT;
                cause = cause.getCause();
            }
            return AiDiagnosticCode.MODEL_TRANSPORT_ERROR;
        }
        if (failure instanceof HttpStatusCodeException) return AiDiagnosticCode.MODEL_HTTP_ERROR;
        if (failure instanceof JsonProcessingException || failure instanceof ClassCastException)
            return AiDiagnosticCode.MODEL_INVALID_RESPONSE;
        return AiDiagnosticCode.MODEL_ERROR;
    }

    // ========================================================================
    // Core LLM HTTP call (OpenAI-compatible chat completions API)
    // ========================================================================

    private String callLlm(String systemPrompt, String userPrompt, CallTimer timer) {
        if (AiTaskBudget.remainingMillis() < 1_000)
            throw new IllegalStateException("task deadline exhausted");
        if (llmProperties.key() == null || llmProperties.key().isBlank()) {
            throw new IllegalStateException("DASHSCOPE_API_KEY is not configured");
        }
        String url = llmProperties.baseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmProperties.key());

        Map<String, Object> requestBody = new HashMap<>(Map.of(
            "model", llmProperties.model(),
            "messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
            ),
            "temperature", llmProperties.temperature(),
            "max_tokens", llmProperties.maxTokens()
        ));
        // Qwen3.7 默认开启思考；此处需要直接返回结构化 JSON，保持同步调用语义。
        if (llmProperties.model().startsWith("qwen3.7-")) {
            requestBody.put("enable_thinking", false);
        }

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        log.debug("Calling LLM: model={}, prompt length={}", llmProperties.model(), userPrompt.length());
        long httpStarted = System.nanoTime();
        timer.attempted = true;
        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(url, request, Map.class);
        } finally {
            timer.llmMs += TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - httpStarted);
            timer.calls++;
        }

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
            1. title：通知首页的完整正式标题，不要改写为赛事简称。
            2. organizer：主办单位名称。如果文本中提到了某个学校、学院或机构作为主办方，提取它。
            3. deadline：申报截止日期。格式必须是 yyyy-MM-dd HH:mm。从文本中查找日期和时间信息。
            4. targetGroup：面向的参赛对象，如"本科生"、"研究生"、"教师"等。
            5. keyPoints：用中文简要概括通知的核心内容（100字以内）。
            6. materials：申报需要提交的材料清单。每项材料必须包含：
               - name：材料名称（中文）
               - description：对该材料的简要说明
               - isRequired：是否必须提交（true/false）

            注意：
            - 如果某个字段在文本中确实找不到对应信息，设为 null，不要编造。
            - deadline 字段必须使用标准格式 yyyy-MM-dd HH:mm（如 2026-06-15 23:59）。如果文本中只有日期没有时间，默认补充 23:59。
            - 不要使用中文日期格式（如"2026年6月15日"），必须转换为 yyyy-MM-dd HH:mm。
            - materials 只收录原文明确要求在报名或初赛阶段提交的材料；复赛、决赛等后续阶段的不同材料不要混入同一个清单，可在 keyPoints 提醒人工查看。
            - 不要把参赛资格、作品主题、评审指标、泛称“参赛作品”等推断成具体提交材料。
            - 当前文本可能只是一部分页；没有出现的字段返回 null，不依赖其他页猜测。

            返回格式：纯 JSON（不要加 ```json``` 标记），例如：
            {"title":"关于举办2026年大学生创新创业大赛的通知","organizer":"某某大学","deadline":"2026-06-15 23:59","targetGroup":"本科生","keyPoints":"...","materials":[{"name":"申报书","description":"...","isRequired":true}]}

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
            null,
            null, null, null,
            "AI解析暂时不可用，请稍后重试。当前使用默认配置。",
            Collections.emptyList()
        );
    }

    private AiCheckResponse fallbackCheckMaterial() {
        return new AiCheckResponse(
            "warning",
            "AI审核服务暂时不可用，系统已自动标记为待人工复核。请人工审核项目材料。"
        );
    }
}
