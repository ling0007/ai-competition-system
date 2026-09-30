package com.eliza.aicompetition.common;

import com.eliza.aicompetition.config.LlmProperties;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OCR fallback for scanned/image PDFs that Tika cannot extract text from.
 * Renders PDF pages as images and sends them to DashScope's
 * multimodal model (currently qwen3.7-flash) for text extraction.
 *
 * <p>Pages are processed in parallel to minimize total latency.</p>
 */
@Component
public class PdfOcrExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfOcrExtractor.class);

    /** Maximum PDF pages to OCR via vision model. */
    @Value("${ai.ocr.max-pages:20}") private int maxPages = 20;
    @Value("${ai.ocr.max-bytes:20971520}") private int maxBytes = 20 * 1024 * 1024;
    @Value("${ai.ocr.batch-size:2}") private int batchSize = 2;
    @Value("${ai.ocr.concurrency:2}") private int concurrency = 2;
    @Value("${ai.ocr.page-timeout-seconds:60}") private int pageTimeoutSeconds = 60;
    @Value("${ai.ocr.max-vision-calls:40}") private int maxVisionCalls = 40;

    /** Image DPI for rendering (100 is sufficient for OCR, reduces payload vs 150). */
    private static final int RENDER_DPI = 100;

    /** Maximum width in pixels — larger images are scaled down to save bandwidth. */
    private static final int MAX_WIDTH = 1200;

    /** Maximum raw image bytes before resize/compression (~2.5MB). */
    private static final int MAX_IMAGE_BYTES = 2_500_000;

    /** JPEG compression quality (0.0–1.0). 0.75 gives good size reduction with minimal quality loss. */
    private static final float JPEG_QUALITY = 0.75f;

    /** Timeout per page for parallel vision model calls. */
    private static final int MAX_PAGE_PIXELS = 4_000_000;

    private final RestTemplate restTemplate;
    private final LlmProperties llmProperties;

    public record OcrResult(String text, int totalPages, int attemptedPages,
                            int succeededPages, int failedPages, int skippedPages,
                            int visionCalls, String failureReason) {
        public OcrResult(String text, int totalPages, int attemptedPages,
                         int succeededPages, int failedPages, int skippedPages) {
            this(text, totalPages, attemptedPages, succeededPages, failedPages, skippedPages, 0, null);
        }
    }

    public PdfOcrExtractor(RestTemplate restTemplate, LlmProperties llmProperties) {
        this.restTemplate = restTemplate;
        this.llmProperties = llmProperties;
        log.info("PdfOcrExtractor initialized: model={}, maxPages={}, dpi={}, maxWidth={}",
            llmProperties.visionModel(), maxPages, RENDER_DPI, MAX_WIDTH);
    }

    /**
     * Attempt OCR on a PDF using the DashScope vision model.
     * Pages are rendered sequentially (CPU-bound), then sent to the
     * vision model in parallel (I/O-bound) to minimize total latency.
     *
     * @param pdfBytes the raw PDF file content
     * @return extracted text from all rendered pages, or null if OCR fails
     */
    public String ocrPdf(byte[] pdfBytes) {
        return ocrPdfMeasured(pdfBytes, null).text();
    }

    public OcrResult ocrPdfMeasured(byte[] pdfBytes, Long taskId) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return new OcrResult(null, 0, 0, 0, 0, 0);
        }

        long started = System.nanoTime();
        long renderMs = 0;
        long visionWallMs = 0;
        boolean success = false;
        log.info("Starting vision OCR taskId={} PDF size={} bytes, model={}", taskId, pdfBytes.length, llmProperties.visionModel());

        if (pdfBytes.length > maxBytes) {
            log.warn("ocr_rejected taskId={} reason=OCR_BYTE_LIMIT bytes={}", taskId, pdfBytes.length);
            return new OcrResult(null, 0, 0, 0, 0, 0, 0, "OCR_BYTE_LIMIT");
        }
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            if (totalPages > maxPages || totalPages < 1) {
                log.warn("ocr_rejected taskId={} reason=OCR_PAGE_LIMIT pages={}", taskId, totalPages);
                return new OcrResult(null, totalPages, 0, 0, 0, totalPages, 0, "OCR_PAGE_LIMIT");
            }
            if (maxVisionCalls < totalPages || batchSize < 1 || concurrency < 1) {
                return new OcrResult(null, totalPages, 0, 0, 0, totalPages, 0, "OCR_CALL_LIMIT");
            }
            if (llmProperties.key() == null || llmProperties.key().isBlank()) {
                log.warn("Skipping vision OCR taskId={}: API key is not configured", taskId);
                return new OcrResult(null, totalPages, 0, 0, 0, totalPages, 0, "API_KEY_MISSING");
            }
            PDFRenderer renderer = new PDFRenderer(document);
            ExecutorService executor = Executors.newFixedThreadPool(Math.min(concurrency, batchSize),
                r -> {
                    Thread t = new Thread(r, "ocr-vision");
                    t.setDaemon(true);
                    return t;
                });

            try {
                StringBuilder result = new StringBuilder();
                int attempted = 0, succeeded = 0, processed = 0;
                AtomicInteger calls = new AtomicInteger();
                String reason = null;
                for (int start = 0; start < totalPages; start += batchSize) {
                    if (AiTaskBudget.exhausted() || Thread.currentThread().isInterrupted()) {
                        reason = "OCR_BUDGET_EXHAUSTED";
                        break;
                    }
                    int end = Math.min(totalPages, start + batchSize);
                    var taskDeadline = AiTaskBudget.deadline();
                    List<Future<String>> futures = new ArrayList<>();
                    List<Long> submittedAt = new ArrayList<>();
                    for (int page = start; page < end; page++) {
                        long renderStarted = System.nanoTime();
                        var box = document.getPage(page).getCropBox();
                        long pixels = (long) Math.ceil(box.getWidth() * RENDER_DPI / 72.0)
                            * (long) Math.ceil(box.getHeight() * RENDER_DPI / 72.0);
                        String image = pixels > MAX_PAGE_PIXELS ? null : renderPageToBase64(renderer, page, totalPages);
                        renderMs += AiOperationTiming.elapsedMs(renderStarted);
                        final int pageNo = page + 1;
                        if (image == null) {
                            futures.add(null);
                            submittedAt.add(0L);
                        } else {
                            if (calls.get() >= maxVisionCalls || AiTaskBudget.exhausted()) {
                                reason = calls.get() >= maxVisionCalls ? "OCR_CALL_LIMIT" : "OCR_BUDGET_EXHAUSTED";
                                futures.add(null);
                                submittedAt.add(0L);
                                continue;
                            }
                            submittedAt.add(System.nanoTime());
                            futures.add(executor.submit(() -> {
                                AiTaskBudget.set(taskDeadline);
                                try {
                                    for (int retry = 0; retry < 2; retry++) {
                                        if (AiTaskBudget.exhausted() || Thread.currentThread().isInterrupted()) return null;
                                        int prior;
                                        do {
                                            prior = calls.get();
                                            if (prior >= maxVisionCalls) return null;
                                        } while (!calls.compareAndSet(prior, prior + 1));
                                        String value = callVisionModel(image, pageNo, taskId);
                                        if (value != null && !value.isBlank()) return value;
                                    }
                                    return null;
                                } finally {
                                    AiTaskBudget.clear();
                                }
                            }));
                        }
                    }
                    long visionStarted = System.nanoTime();
                    for (int i = 0; i < futures.size(); i++) {
                        processed++;
                        attempted++;
                        int pageNo = start + i + 1;
                        Future<String> future = futures.get(i);
                        String pageText = null;
                        if (future != null) {
                            try {
                                long pageRemainingMs = pageTimeoutSeconds * 1000L
                                    - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - submittedAt.get(i));
                                long waitMs = Math.min(pageRemainingMs, AiTaskBudget.remainingMillis());
                                if (waitMs <= 0) throw new TimeoutException("task deadline");
                                pageText = future.get(waitMs, TimeUnit.MILLISECONDS);
                            } catch (Exception failure) {
                                future.cancel(true);
                                if (failure instanceof TimeoutException) reason = "OCR_BUDGET_EXHAUSTED";
                                log.warn("ocr_page taskId={} page={} failure={}", taskId, pageNo,
                                    failure.getClass().getSimpleName());
                            }
                        }
                        if (pageText != null && !pageText.isBlank()) {
                            succeeded++;
                            result.append("【PDF第").append(pageNo).append("页】\n")
                                .append(pageText).append('\n');
                        } else {
                            result.append("【PDF第").append(pageNo).append("页 OCR失败，需人工核对】\n");
                        }
                        log.info("ocr_page taskId={} page={} status={}", taskId, pageNo,
                            pageText != null && !pageText.isBlank() ? "SUCCESS" : "FAILED");
                    }
                    visionWallMs += AiOperationTiming.elapsedMs(visionStarted);
                    log.info("ocr_batch taskId={} firstPage={} lastPage={} renderMs={} visionWallMs={} visionCalls={}",
                        taskId, start + 1, end, renderMs, visionWallMs, calls.get());
                    if (reason != null) break;
                }
                String text = result.toString().trim();
                int skipped = totalPages - processed;
                if (skipped > 0 && reason == null) reason = "OCR_BUDGET_EXHAUSTED";
                if (reason == null && calls.get() >= maxVisionCalls && succeeded < attempted)
                    reason = "OCR_CALL_LIMIT";
                success = succeeded > 0 && skipped == 0 && reason == null;
                return new OcrResult(succeeded == 0 || reason != null ? null : text,
                    totalPages, attempted, succeeded, attempted - succeeded, skipped, calls.get(), reason);

            } finally {
                executor.shutdownNow();
            }

        } catch (IOException e) {
            log.error("Failed to load/render PDF for vision OCR taskId={} errorType={}", taskId, e.getClass().getSimpleName());
            return new OcrResult(null, 0, 0, 0, 0, 0, 0, "OCR_FAILED");
        } catch (Exception e) {
            log.error("Vision OCR failed taskId={} errorType={}", taskId, e.getClass().getSimpleName());
            return new OcrResult(null, 0, 0, 0, 0, 0, 0, "OCR_FAILED");
        } finally {
            log.info("ocr_timing taskId={} totalMs={} renderMs={} visionWallMs={} status={}",
                taskId, AiOperationTiming.elapsedMs(started), renderMs, visionWallMs, success ? "SUCCESS" : "FAILED");
        }
    }

    /**
     * Render a single PDF page to a base64-encoded JPEG string.
     * Returns null if the page cannot be rendered or is too large.
     */
    private String renderPageToBase64(PDFRenderer renderer, int page, int totalPages) {
        try {
            log.info("Rendering page {}/{}", page + 1, totalPages);

            BufferedImage image = renderer.renderImageWithDPI(page, RENDER_DPI);

            // Scale down if too wide
            if (image.getWidth() > MAX_WIDTH) {
                image = resizeImage(image, MAX_WIDTH);
            }

            // Convert to JPEG
            byte[] imageBytes = toJpegBytes(image);

            log.info("Page {}: image {}x{}, {} bytes (JPEG q={})",
                page + 1, image.getWidth(), image.getHeight(),
                imageBytes.length, JPEG_QUALITY);

            if (imageBytes.length > MAX_IMAGE_BYTES) {
                log.warn("Page {} image too large ({} bytes), retrying with lower quality",
                    page + 1, imageBytes.length);
                imageBytes = toJpegBytes(image, 0.4f);
                log.info("Page {}: retry image size {} bytes", page + 1, imageBytes.length);
                if (imageBytes.length > MAX_IMAGE_BYTES) {
                    log.warn("Page {} image still too large, skipping", page + 1);
                    return null;
                }
            }

            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            log.info("Page {}: base64 length={}", page + 1, base64.length());
            return base64;

        } catch (IOException e) {
            log.error("Failed to render page {} errorType={}", page + 1, e.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * Call DashScope multimodal model with a base64-encoded page image.
     */
    @SuppressWarnings("unchecked")
    private String callVisionModel(String base64Image, int pageNumber, Long taskId) {
        String url = llmProperties.baseUrl() + "/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(llmProperties.key());

        // OpenAI-compatible multimodal message format
        List<Map<String, Object>> content = List.of(
            Map.of("type", "image_url",
                "image_url", Map.of("url", "data:image/jpeg;base64," + base64Image)),
            Map.of("type", "text",
                "text", "请提取这张图片中的所有文字内容，只输出文字，不要添加任何解释。如果是中文，请输出中文原文。")
        );

        String model = llmProperties.visionModel();
        int maxTokens = llmProperties.visionMaxTokens() != null ? llmProperties.visionMaxTokens() : 4096;

        Map<String, Object> requestBody = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "user", "content", content)
            ),
            "max_tokens", maxTokens
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            log.info("Calling vision model: model={}, image size={} chars, maxTokens={}",
                model, base64Image.length(), maxTokens);
            long httpStarted = System.nanoTime();
            ResponseEntity<Map> response;
            try {
                response = restTemplate.postForEntity(url, request, Map.class);
            } finally {
                log.info("ocr_http_timing taskId={} page={} llmMs={}", taskId, pageNumber, AiOperationTiming.elapsedMs(httpStarted));
            }

            if (response.getBody() == null) {
                log.warn("Vision model returned empty response body");
                return null;
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("Vision model response has no choices");
                return null;
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                log.warn("Vision model response choice has no message");
                return null;
            }

            String responseText = (String) message.get("content");
            log.info("Vision model responded: {} chars", responseText != null ? responseText.length() : 0);
            return responseText;

        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("Vision model HTTP {} (response body length={})",
                e.getStatusCode().value(), responseBody.length());

            if (e.getStatusCode().value() == 401) {
                log.error(
                    "401 Unauthorized — possible causes:\n" +
                    "  1. API key has no vision model access (check DashScope console → Model Library → Vision)\n" +
                    "  2. Model name '{}' is deprecated/wrong (try: qwen-vl-plus, qwen-vl-max-latest, qwen2.5-vl-72b-instruct)\n" +
                    "  3. DASHSCOPE_API_KEY env var is not set or has extra whitespace",
                    model);
            }
            return null;

        } catch (Exception e) {
            log.error("Vision model API call failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }

    private byte[] toJpegBytes(BufferedImage image) throws IOException {
        return toJpegBytes(image, JPEG_QUALITY);
    }

    private byte[] toJpegBytes(BufferedImage image, float quality) throws IOException {
        BufferedImage rgbImage = new BufferedImage(
            image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgbImage.createGraphics();
        g.drawImage(image, 0, 0, null);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        var jpegParams = new javax.imageio.plugins.jpeg.JPEGImageWriteParam(null);
        jpegParams.setCompressionMode(javax.imageio.plugins.jpeg.JPEGImageWriteParam.MODE_EXPLICIT);
        jpegParams.setCompressionQuality(quality);

        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (var ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new javax.imageio.IIOImage(rgbImage, null, null), jpegParams);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private BufferedImage resizeImage(BufferedImage original, int maxWidth) {
        double ratio = (double) maxWidth / original.getWidth();
        int newHeight = (int) (original.getHeight() * ratio);
        BufferedImage resized = new BufferedImage(maxWidth, newHeight, original.getType());
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, maxWidth, newHeight, null);
        g.dispose();
        log.info("Resized image: {}x{} → {}x{}", original.getWidth(), original.getHeight(), maxWidth, newHeight);
        return resized;
    }
}
