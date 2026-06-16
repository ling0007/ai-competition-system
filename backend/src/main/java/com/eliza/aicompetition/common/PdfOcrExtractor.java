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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * OCR fallback for scanned/image PDFs that Tika cannot extract text from.
 * Renders PDF pages as images and sends them to DashScope's
 * multimodal model (e.g. qwen-vl-plus) for text extraction.
 *
 * <p>Pages are processed in parallel to minimize total latency.</p>
 */
@Component
public class PdfOcrExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfOcrExtractor.class);

    /** Maximum PDF pages to OCR via vision model. */
    private static final int MAX_PAGES = 3;

    /** Image DPI for rendering (100 is sufficient for OCR, reduces payload vs 150). */
    private static final int RENDER_DPI = 100;

    /** Maximum width in pixels — larger images are scaled down to save bandwidth. */
    private static final int MAX_WIDTH = 1200;

    /** Maximum raw image bytes before resize/compression (~2.5MB). */
    private static final int MAX_IMAGE_BYTES = 2_500_000;

    /** JPEG compression quality (0.0–1.0). 0.75 gives good size reduction with minimal quality loss. */
    private static final float JPEG_QUALITY = 0.75f;

    /** Timeout per page for parallel vision model calls. */
    private static final long PAGE_TIMEOUT_SECONDS = 120;

    private final RestTemplate restTemplate;
    private final LlmProperties llmProperties;

    public PdfOcrExtractor(RestTemplate restTemplate, LlmProperties llmProperties) {
        this.restTemplate = restTemplate;
        this.llmProperties = llmProperties;
        log.info("PdfOcrExtractor initialized: model={}, maxPages={}, dpi={}, maxWidth={}",
            llmProperties.visionModel(), MAX_PAGES, RENDER_DPI, MAX_WIDTH);
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
        if (pdfBytes == null || pdfBytes.length == 0) {
            return null;
        }

        log.info("Starting vision OCR: PDF size={} bytes, model={}", pdfBytes.length, llmProperties.visionModel());

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            int pagesToOcr = Math.min(totalPages, MAX_PAGES);
            PDFRenderer renderer = new PDFRenderer(document);

            // Phase 1: Render all pages to base64 images (CPU-bound, sequential is fine)
            String[] pageImages = new String[pagesToOcr];
            for (int page = 0; page < pagesToOcr; page++) {
                String base64 = renderPageToBase64(renderer, page, pagesToOcr);
                pageImages[page] = base64;
                if (base64 == null) {
                    log.warn("Page {} rendering failed, will skip it", page + 1);
                }
            }

            // Phase 2: Send all valid pages to vision model in parallel (I/O-bound)
            ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(pagesToOcr, 3),
                r -> {
                    Thread t = new Thread(r, "ocr-vision");
                    t.setDaemon(true);
                    return t;
                });

            try {
                @SuppressWarnings("unchecked")
                CompletableFuture<String>[] futures = new CompletableFuture[pagesToOcr];
                for (int page = 0; page < pagesToOcr; page++) {
                    final int pageNum = page;
                    final String base64 = pageImages[page];
                    if (base64 == null) {
                        futures[page] = CompletableFuture.completedFuture(null);
                    } else {
                        futures[page] = CompletableFuture.supplyAsync(
                            () -> {
                                log.info("Vision call started for page {}", pageNum + 1);
                                String text = callVisionModel(base64);
                                log.info("Vision call finished for page {}: {} chars",
                                    pageNum + 1, text != null ? text.length() : 0);
                                return text;
                            },
                            executor
                        ).orTimeout(PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                         .exceptionally(ex -> {
                             log.warn("Page {} vision call failed: {}", pageNum + 1, ex.getMessage());
                             return null;
                         });
                    }
                }

                // Wait for all pages and assemble in order
                CompletableFuture.allOf(futures).join();

                StringBuilder result = new StringBuilder();
                for (int page = 0; page < pagesToOcr; page++) {
                    String pageText = futures[page].getNow(null);
                    if (pageText != null && !pageText.isBlank()) {
                        result.append(pageText).append("\n");
                    }
                }

                String text = result.toString().trim();
                if (text.isEmpty()) {
                    log.warn("Vision OCR produced no text from {} pages", pagesToOcr);
                    return null;
                }
                log.info("Vision OCR extracted {} chars total from {} PDF pages (parallel)",
                    text.length(), pagesToOcr);
                return text;

            } finally {
                executor.shutdownNow();
            }

        } catch (IOException e) {
            log.error("Failed to load/render PDF for vision OCR: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Vision OCR failed: {}", e.getMessage(), e);
            return null;
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
            log.error("Failed to render page {}: {}", page + 1, e.getMessage());
            return null;
        }
    }

    /**
     * Call DashScope multimodal model with a base64-encoded page image.
     */
    @SuppressWarnings("unchecked")
    private String callVisionModel(String base64Image) {
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
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

            if (response.getBody() == null) {
                log.warn("Vision model returned empty response body");
                return null;
            }

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("Vision model response has no choices: body={}", response.getBody());
                return null;
            }

            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            if (message == null) {
                log.warn("Vision model response choice has no message: choices={}", choices);
                return null;
            }

            String responseText = (String) message.get("content");
            log.info("Vision model responded: {} chars", responseText != null ? responseText.length() : 0);
            return responseText;

        } catch (HttpClientErrorException e) {
            String responseBody = e.getResponseBodyAsString();
            log.error("Vision model HTTP {}: {}\nResponse headers: {}\nResponse body: {}",
                e.getStatusCode().value(), e.getMessage(),
                e.getResponseHeaders(),
                responseBody.isEmpty() ? "[empty body — request may be rejected at gateway/CDN level]" : responseBody);

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
            log.error("Vision model API call failed: {} ({})", e.getMessage(), e.getClass().getSimpleName());
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
