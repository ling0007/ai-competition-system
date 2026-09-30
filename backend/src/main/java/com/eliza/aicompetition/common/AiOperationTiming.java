package com.eliza.aicompetition.common;

import org.slf4j.Logger;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Per-invocation timings for the current synchronous AI paths; no request data or secrets are logged. */
public final class AiOperationTiming {
    private final long startedNanos = System.nanoTime();
    private final LocalDateTime startedAt = LocalDateTime.now();
    private long extractMs;
    private long tikaMs;
    private long ocrMs;
    private long llmMs;
    private boolean ocrAttempted;
    private boolean llmAttempted;
    private boolean degraded;
    private Long taskId;
    private int attemptNo = 1;
    private final Set<String> processingPaths = new LinkedHashSet<>();
    private long fileBytes;
    private int fileCount;
    private int pdfPages;
    private int ocrAttemptedPages;
    private int ocrSucceededPages;
    private int ocrFailedPages;
    private int ocrSkippedPages;
    private int visionCalls;
    private int mainModelCalls;
    private String textModel;
    private String visionModel;
    private String promptVersion;
    private String promptHash;
    private AiDiagnosticCode reason;
    private boolean workerMeasured;

    public LocalDateTime startedAt() { return startedAt; }
    public void taskId(Long value) { taskId = value; }
    public Long taskId() { return taskId; }
    public void attempt(int value) { attemptNo = value; }
    public void workerStarted() { workerMeasured = true; }
    public boolean workerMeasured() { return workerMeasured; }
    public void textPath() { processingPaths.add("TEXT"); }
    public void prompt(String version, String hash) { promptVersion = version; promptHash = hash; }
    public void models(String text, String vision) { textModel = text; visionModel = vision; }
    public void reason(AiDiagnosticCode code) { if (code != null) reason = code; }
    public AiDiagnosticCode reason() { return reason; }

    public void extraction(FileTextExtractor.ExtractionResult result) {
        extractMs += result.totalMs();
        tikaMs += result.tikaMs();
        ocrMs += result.ocrMs();
        ocrAttempted |= result.ocrAttempted();
        processingPaths.add(result.path());
        fileBytes += result.fileBytes();
        fileCount++;
        pdfPages += result.pdfPages();
        ocrAttemptedPages += result.ocrAttemptedPages();
        ocrSucceededPages += result.ocrSucceededPages();
        ocrFailedPages += result.ocrFailedPages();
        ocrSkippedPages += result.ocrSkippedPages();
        visionCalls += result.visionCalls();
        if (result.failureReason() != null) {
            try { reason = AiDiagnosticCode.valueOf(result.failureReason()); }
            catch (IllegalArgumentException ignored) { reason = AiDiagnosticCode.OCR_FAILED; }
        } else if (result.ocrAttempted() && result.pdfPages() > 0 && result.ocrAttemptedPages() == 0)
            reason = AiDiagnosticCode.API_KEY_MISSING;
        else if (result.ocrAttempted() && result.ocrSucceededPages() == 0)
            reason = AiDiagnosticCode.OCR_FAILED;
        else if (ocrFailedPages > 0) reason = AiDiagnosticCode.OCR_PARTIAL;
        else if (ocrSkippedPages > 0 && reason == null) reason = AiDiagnosticCode.OCR_PAGE_LIMIT;
    }

    public void call(long durationMs, boolean attempted, boolean usedFallback) {
        call(durationMs, attempted, usedFallback, attempted ? 1 : 0);
    }

    public void call(long durationMs, boolean attempted, boolean usedFallback, int calls) {
        llmMs += durationMs;
        mainModelCalls += calls;
        llmAttempted |= attempted;
        degraded |= usedFallback;
    }

    public void degraded() { degraded = true; }
    public boolean isDegraded() { return degraded; }

    public Map<String, Object> observation(String status, String origin) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", 1);
        data.put("workerMeasured", workerMeasured);
        data.put("processingPaths", processingPaths.isEmpty() ? Set.of("UNKNOWN") : processingPaths);
        data.put("textModel", llmAttempted ? textModel : null);
        data.put("visionModel", ocrAttemptedPages > 0 ? visionModel : null);
        data.put("promptVersion", promptVersion);
        data.put("promptHash", promptHash);
        data.put("ocrPromptVersion", ocrAttemptedPages > 0 ? "pdf-ocr-v1" : null);
        data.put("fileCount", fileCount);
        data.put("fileBytes", fileBytes);
        data.put("pdfPages", pdfPages);
        data.put("ocrAttemptedPages", ocrAttemptedPages);
        data.put("ocrSucceededPages", ocrSucceededPages);
        data.put("ocrFailedPages", ocrFailedPages);
        data.put("ocrSkippedPages", ocrSkippedPages);
        data.put("visionCalls", visionCalls);
        data.put("mainModelCalls", mainModelCalls);
        data.put("ocrCoverageIssue", ocrAttempted && pdfPages > 0 && ocrAttemptedPages == 0
            ? "OCR_NOT_ATTEMPTED" : ocrFailedPages > 0 ? "OCR_PARTIAL"
            : ocrSkippedPages > 0 ? "OCR_PAGE_LIMIT" : null);
        data.put("extractMs", extractMs);
        data.put("tikaMs", tikaMs);
        data.put("ocrMs", ocrMs);
        data.put("mainModelMs", llmMs);
        data.put("workerMs", elapsedMs(startedNanos));
        data.put("llmAttempted", llmAttempted);
        data.put("ocrAttempted", ocrAttempted);
        data.put("status", status);
        data.put("resultOrigin", origin);
        return data;
    }

    public void log(Logger logger, String operation, Long businessId, String status) {
        long totalMs = elapsedMs(startedNanos);
        long otherMs = Math.max(0, totalMs - extractMs - llmMs);
        logger.info("ai_timing operation={} businessId={} taskId={} extractMs={} tikaMs={} ocrMs={} "
                + "llmMs={} totalMs={} otherMs={} status={} attempt={} degraded={} ocrAttempted={} llmAttempted={}",
            operation, businessId, taskId, extractMs, tikaMs, ocrMs,
            llmMs, totalMs, otherMs, status, attemptNo, degraded, ocrAttempted, llmAttempted);
    }

    public static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }
}
