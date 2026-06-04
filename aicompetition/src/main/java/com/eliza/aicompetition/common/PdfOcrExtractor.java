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
import org.springframework.web.client.RestTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * OCR fallback for scanned/image PDFs that Tika cannot extract text from.
 * Renders PDF pages as images and sends them to DashScope's
 * multimodal model (qwen-vl-max) for text extraction.
 *
 * <p>No local OCR installation required — uses the same DashScope API.</p>
 */
@Component
public class PdfOcrExtractor {

    private static final Logger log = LoggerFactory.getLogger(PdfOcrExtractor.class);

    /** Maximum PDF pages to OCR via vision model. */
    private static final int MAX_PAGES = 3;

    /** Image DPI for rendering. */
    private static final int RENDER_DPI = 150;

    /** Maximum base64 image size (~10MB after encoding, ~7.5MB raw). */
    private static final int MAX_IMAGE_BYTES = 7_500_000;

    private final RestTemplate restTemplate;
    private final LlmProperties llmProperties;

    public PdfOcrExtractor(RestTemplate restTemplate, LlmProperties llmProperties) {
        this.restTemplate = restTemplate;
        this.llmProperties = llmProperties;
        log.info("PdfOcrExtractor initialized (DashScope multimodal OCR), max pages: {}", MAX_PAGES);
    }

    /**
     * Attempt OCR on a PDF using the DashScope vision model.
     *
     * @param pdfBytes the raw PDF file content
     * @return extracted text from all rendered pages, or null if OCR fails
     */
    @SuppressWarnings("unchecked")
    public String ocrPdf(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return null;
        }

        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            int totalPages = document.getNumberOfPages();
            int pagesToOcr = Math.min(totalPages, MAX_PAGES);
            PDFRenderer renderer = new PDFRenderer(document);
            StringBuilder result = new StringBuilder();

            for (int page = 0; page < pagesToOcr; page++) {
                log.info("OCR processing page {}/{}", page + 1, pagesToOcr);

                // Render page to image
                BufferedImage image = renderer.renderImageWithDPI(page, RENDER_DPI);
                byte[] imageBytes = toPngBytes(image);

                if (imageBytes.length > MAX_IMAGE_BYTES) {
                    log.warn("Page {} image too large ({} bytes), skipping", page + 1, imageBytes.length);
                    continue;
                }

                String base64Image = Base64.getEncoder().encodeToString(imageBytes);
                String pageText = callVisionModel(base64Image);

                if (pageText != null && !pageText.isBlank()) {
                    result.append(pageText).append("\n");
                    log.info("Page {}: extracted {} chars", page + 1, pageText.length());
                }
            }

            String text = result.toString().trim();
            if (text.isEmpty()) {
                log.warn("Vision OCR produced no text from {} pages", pagesToOcr);
                return null;
            }
            log.info("Vision OCR extracted {} chars total from {} PDF pages", text.length(), pagesToOcr);
            return text;

        } catch (IOException e) {
            log.error("Failed to load/render PDF for vision OCR: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("Vision OCR failed: {}", e.getMessage());
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
                "image_url", Map.of("url", "data:image/png;base64," + base64Image)),
            Map.of("type", "text",
                "text", "请提取这张图片中的所有文字内容，只输出文字，不要添加任何解释。如果是中文，请输出中文原文。")
        );

        Map<String, Object> requestBody = Map.of(
            "model", "qwen-vl-max",
            "messages", List.of(
                Map.of("role", "user", "content", content)
            ),
            "max_tokens", 4096
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);

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

            return (String) message.get("content");

        } catch (Exception e) {
            log.warn("Vision model API call failed: {}", e.getMessage());
            return null;
        }
    }

    private byte[] toPngBytes(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        return baos.toByteArray();
    }
}
