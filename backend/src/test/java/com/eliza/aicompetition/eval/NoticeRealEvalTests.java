package com.eliza.aicompetition.eval;

import com.eliza.aicompetition.common.FileTextExtractor;
import com.eliza.aicompetition.common.PdfOcrExtractor;
import com.eliza.aicompetition.config.LlmConfig;
import com.eliza.aicompetition.config.LlmProperties;
import com.eliza.aicompetition.service.AiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;

/** Paid, DB-free opt-in run through the production Tika/OCR and AiService components. */
@EnabledIfSystemProperty(named = "phase6.eval.real", matches = "true")
class NoticeRealEvalTests {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void evaluateThreeOriginalFilesAndWriteCompactReport() throws Exception {
        JsonNode gold = NoticeEvalGold.load(); // SHA-256 check happens before any paid call.
        String key = System.getenv("DASHSCOPE_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Set DASHSCOPE_API_KEY only in the process environment");
        }
        Properties app = new Properties();
        try (InputStream stream = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (stream == null) throw new IllegalStateException("application.properties missing");
            app.load(stream);
        }
        LlmProperties settings = new LlmProperties(
            app.getProperty("llm.api.base-url"), key, app.getProperty("llm.api.model"),
            Double.valueOf(app.getProperty("llm.api.temperature")),
            Integer.valueOf(app.getProperty("llm.api.max-tokens")),
            app.getProperty("llm.api.vision-model"),
            Integer.valueOf(app.getProperty("llm.api.vision-max-tokens")));
        CapturingRestTemplate http = new CapturingRestTemplate(new LlmConfig().llmRestTemplate());
        FileTextExtractor extractor = new FileTextExtractor(new PdfOcrExtractor(http, settings));
        AiService ai = new AiService(http, settings, JSON);

        ObjectNode report = JSON.createObjectNode();
        report.put("runUtc", Instant.now().toString());
        report.put("goldVersion", gold.path("expectedVersion").asText());
        report.put("textModel", settings.model());
        report.put("visionModel", settings.visionModel());
        report.put("promptSourceSha256", sha256(Path.of("src/main/java/com/eliza/aicompetition/service/AiService.java")));
        report.put("runner", "DIRECT_COMPONENTS_NO_DB");
        report.put("timingScope", "extract/Tika/OCR/main LLM and local wall; no queue wait, HTTP accept or browser visible time");
        ArrayNode results = report.putArray("samples");
        String sampleFilter = System.getProperty("phase6.eval.sampleId", "");
        for (JsonNode sample : gold.path("samples")) {
            if (!sampleFilter.isEmpty() && !sampleFilter.equals(sample.path("id").asText())) continue;
            Path file = NoticeEvalGold.samplePath(sample);
            String ext = sample.path("kind").asText().equals("TXT") ? "txt" : "pdf";
            long started = System.nanoTime();
            FileTextExtractor.ExtractionResult extracted = extractor.extractTextMeasured(Files.readAllBytes(file), ext);
            http.clearCaptured();
            boolean extractionFailed = extracted.text().startsWith("[No extractable text")
                || extracted.text().startsWith("[Text extraction failed")
                || extracted.text().startsWith("[File is empty")
                || extracted.text().startsWith("[OCR unavailable");
            AiService.CallResult<?> call = extractionFailed ? null : ai.parseNoticeMeasured(extracted.text());
            String origin = call == null ? "NONE" : call.degraded() ? "FALLBACK" : "MODEL";
            String scoreInput = call != null && call.callCount() > 1
                ? JSON.writeValueAsString(call.result()) : http.captured();
            ObjectNode scored = NoticeEvalScorer.score(sample, scoreInput, origin);
            scored.put("scoreSource", call != null && call.callCount() > 1 ? "MERGED_RESULT" : "RAW_MODEL_JSON");
            scored.put("kind", sample.path("kind").asText());
            scored.put("fileSha256", sample.path("sha256").asText());
            scored.put("expectedVersion", gold.path("expectedVersion").asText());
            scored.put("filePages", sample.path("pages").asInt());
            scored.put("ocrAttempted", extracted.ocrAttempted());
            scored.put("ocrAttemptedPages", extracted.ocrAttemptedPages());
            scored.put("ocrSucceededPages", extracted.ocrSucceededPages());
            scored.put("ocrFailedPages", extracted.ocrFailedPages());
            scored.put("ocrSkippedPages", extracted.ocrSkippedPages());
            scored.put("visionCalls", extracted.visionCalls());
            scored.put("mainModelCalls", call == null ? 0 : call.callCount());
            scored.put("llmAttempted", call != null && call.llmAttempted());
            scored.put("extractionFailed", extractionFailed);
            scored.put("extractedChars", extractionFailed ? 0 : extracted.text().length());
            scored.put("parseInputTruncatedAt4000Chars", false);
            ObjectNode timing = scored.putObject("timingMs");
            timing.put("extract", extracted.totalMs());
            timing.put("tika", extracted.tikaMs());
            timing.put("ocr", extracted.ocrMs());
            timing.put("mainLlm", call == null ? 0 : call.llmMs());
            timing.put("localWall", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            results.add(scored);
        }
        ObjectNode totals = report.putObject("totals");
        int model = 0, fallback = 0, none = 0, validJson = 0, validSchema = 0;
        int titlePass = 0, titleScored = 0, organizerPass = 0, organizerScored = 0;
        int deadlinePass = 0, deadlineScored = 0, materialTp = 0, materialFp = 0, materialFn = 0;
        for (JsonNode result : results) {
            switch (result.path("origin").asText()) {
                case "MODEL" -> { model++; if (result.path("jsonValid").asBoolean()) validJson++;
                    if (result.path("schemaValid").asBoolean()) validSchema++;
                    JsonNode fields = result.path("fields");
                    if (!"UNSCORED".equals(fields.path("title").path("status").asText())) {
                        titleScored++;
                        if ("PASS".equals(fields.path("title").path("status").asText())) titlePass++;
                    }
                    if (!"UNSCORED".equals(fields.path("organizer").path("status").asText())) {
                        organizerScored++;
                        if ("PASS".equals(fields.path("organizer").path("status").asText())) organizerPass++;
                    }
                    if (!"UNSCORED".equals(fields.path("deadline").path("status").asText())) {
                        deadlineScored++;
                        if ("PASS".equals(fields.path("deadline").path("status").asText())) deadlinePass++;
                    }
                    materialTp += result.path("materials").path("truePositive").asInt();
                    materialFp += result.path("materials").path("falsePositive").asInt();
                    materialFn += result.path("materials").path("falseNegative").asInt();
                }
                case "FALLBACK" -> fallback++;
                default -> none++;
            }
        }
        totals.put("model", model);
        totals.put("fallback", fallback);
        totals.put("none", none);
        totals.put("modelJsonValid", validJson);
        totals.put("modelSchemaValid", validSchema);
        totals.put("modelQualityDenominator", model);
        totals.put("titleCorrect", titlePass);
        totals.put("titleScored", titleScored);
        totals.put("organizerCorrect", organizerPass);
        totals.put("organizerScored", organizerScored);
        totals.put("deadlineCorrect", deadlinePass);
        totals.put("deadlineScored", deadlineScored);
        totals.put("materialMicroPrecision", materialTp + materialFp == 0 ? 0.0
            : (double) materialTp / (materialTp + materialFp));
        totals.put("materialMicroRecall", materialTp + materialFn == 0 ? 0.0
            : (double) materialTp / (materialTp + materialFn));

        Path output = Path.of(System.getProperty("phase6.eval.report", "target/ai-eval/notice-report.json"));
        if (output.getParent() != null) Files.createDirectories(output.getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println("phase6_notice_eval_report=" + output.toAbsolutePath());
        System.out.println("phase6_notice_eval_totals=" + totals);
        assertFalse(results.isEmpty());
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(file)));
    }

    /** Captures only the latest chat content in memory; the report never writes raw model responses. */
    private static final class CapturingRestTemplate extends RestTemplate {
        private final RestTemplate delegate;
        private final AtomicReference<String> latest = new AtomicReference<>();

        private CapturingRestTemplate(RestTemplate delegate) {
            this.delegate = delegate;
        }

        @Override
        public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType,
                                                    Object... uriVariables) {
            ResponseEntity<T> response = delegate.postForEntity(url, request, responseType, uriVariables);
            if (response.getBody() instanceof Map<?, ?> body) {
                JsonNode content = JSON.valueToTree(body).path("choices").path(0).path("message").path("content");
                if (content.isTextual()) latest.set(content.asText());
            }
            return response;
        }

        private void clearCaptured() { latest.set(null); }
        private String captured() { return latest.get(); }
    }
}
