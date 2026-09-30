package com.eliza.aicompetition.eval;

import com.eliza.aicompetition.common.AiTaskBudget;
import com.eliza.aicompetition.common.PdfOcrExtractor;
import com.eliza.aicompetition.config.LlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Phase63BoundedOcrTests {
    private byte[] scanned() throws Exception {
        return Files.readAllBytes(NoticeEvalGold.samplePath(NoticeEvalGold.load().path("samples").get(2)));
    }

    private PdfOcrExtractor extractor(RestTemplate http) {
        return new PdfOcrExtractor(http, new LlmProperties("https://unused.invalid", "test-key",
            "text", 0.1, 2048, "vision", 4096));
    }

    @Test
    void byteAndPageLimitsRejectBeforeAnyVisionCall() throws Exception {
        RestTemplate http = mock(RestTemplate.class);
        var ocr = extractor(http);
        ReflectionTestUtils.setField(ocr, "maxBytes", 100);
        assertEquals("OCR_BYTE_LIMIT", ocr.ocrPdfMeasured(scanned(), 1L).failureReason());
        ReflectionTestUtils.setField(ocr, "maxBytes", 20_000_000);
        ReflectionTestUtils.setField(ocr, "maxPages", 9);
        var overPages = ocr.ocrPdfMeasured(scanned(), 1L);
        assertEquals("OCR_PAGE_LIMIT", overPages.failureReason());
        assertEquals(10, overPages.skippedPages());
        verifyNoInteractions(http);
    }

    @Test
    void failedPageIsMarkedAndLaterPagesContinueInOrder() throws Exception {
        RestTemplate http = mock(RestTemplate.class);
        AtomicInteger calls = new AtomicInteger();
        when(http.postForEntity(anyString(), any(), eq(Map.class))).thenAnswer(invocation -> {
            int call = calls.incrementAndGet();
            if (call <= 2) throw new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
            return ResponseEntity.ok(Map.of("choices", List.of(Map.of("message", Map.of("content", "PAGE_TEXT")))));
        });
        var ocr = extractor(http);
        ReflectionTestUtils.setField(ocr, "concurrency", 1);
        var result = ocr.ocrPdfMeasured(scanned(), 2L);
        assertEquals(10, result.attemptedPages());
        assertEquals(9, result.succeededPages());
        assertEquals(1, result.failedPages());
        assertEquals(0, result.skippedPages());
        assertEquals(11, result.visionCalls());
        assertTrue(result.text().contains("【PDF第1页 OCR失败，需人工核对】"));
        assertTrue(result.text().indexOf("【PDF第4页】") < result.text().indexOf("【PDF第10页】"));
    }

    @Test
    void expiredTaskDoesNotRenderOrCallVision() throws Exception {
        RestTemplate http = mock(RestTemplate.class);
        AiTaskBudget.set(LocalDateTime.now().minusSeconds(1));
        try {
            var result = extractor(http).ocrPdfMeasured(scanned(), 3L);
            assertEquals("OCR_BUDGET_EXHAUSTED", result.failureReason());
            assertEquals(0, result.attemptedPages());
            assertEquals(10, result.skippedPages());
        } finally {
            AiTaskBudget.clear();
        }
        verifyNoInteractions(http);
    }

    @Test
    void visionConcurrencyNeverExceedsConfiguredTwo() throws Exception {
        RestTemplate http = mock(RestTemplate.class);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();
        when(http.postForEntity(anyString(), any(), eq(Map.class))).thenAnswer(invocation -> {
            int now = active.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(30);
                return ResponseEntity.ok(Map.of("choices", List.of(Map.of("message", Map.of("content", "TEXT")))));
            } finally {
                active.decrementAndGet();
            }
        });
        var result = extractor(http).ocrPdfMeasured(scanned(), 4L);
        assertEquals(10, result.succeededPages());
        assertTrue(peak.get() <= 2);
    }
}
