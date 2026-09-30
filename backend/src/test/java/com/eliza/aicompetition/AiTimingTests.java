package com.eliza.aicompetition;

import com.eliza.aicompetition.common.FileTextExtractor;
import com.eliza.aicompetition.common.PdfOcrExtractor;
import com.eliza.aicompetition.config.LlmProperties;
import com.eliza.aicompetition.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class AiTimingTests {
    private final RestTemplate http = mock(RestTemplate.class);
    private final AiService aiService = new AiService(http,
        new LlmProperties("https://example.invalid", "test-only", "test-model", 0.1, 512, "vision", 512),
        new ObjectMapper());

    @Test
    void successfulNoticeCallRecordsExternalHttpTimeWithoutDegradation() {
        String json = "{\"title\":\"测试通知\",\"materials\":[]}";
        when(http.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenAnswer(call -> {
                Thread.sleep(12);
                return ResponseEntity.ok(Map.of("choices", List.of(Map.of("message", Map.of("content", json)))));
            });

        var result = aiService.parseNoticeMeasured("通知正文");

        assertEquals("测试通知", result.result().title());
        assertTrue(result.llmAttempted());
        assertFalse(result.degraded());
        assertTrue(result.llmMs() >= 10);
    }

    @Test
    void failedMaterialCallKeepsAttemptDurationAndMarksFallback() {
        when(http.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenAnswer(call -> {
                Thread.sleep(12);
                throw new IllegalStateException("simulated upstream failure");
            });

        var result = aiService.checkMaterialMeasured("项目", "材料正文");

        assertTrue(result.llmAttempted());
        assertTrue(result.degraded());
        assertTrue(result.llmMs() >= 10);
        assertNotNull(result.result().reviewComment());
    }

    @Test
    void missingKeyDegradesWithoutPretendingAnExternalCallHappened() {
        AiService noKey = new AiService(http,
            new LlmProperties("https://example.invalid", "", "test-model", 0.1, 512, "vision", 512),
            new ObjectMapper());

        var result = noKey.parseNoticeMeasured("通知正文");

        assertTrue(result.degraded());
        assertFalse(result.llmAttempted());
        assertEquals(0, result.llmMs());
        verifyNoInteractions(http);
    }

    @Test
    void textExtractionReportsZeroOcrWhenFallbackWasNotUsed() {
        FileTextExtractor extractor = new FileTextExtractor(mock(PdfOcrExtractor.class));

        var result = extractor.extractTextMeasured("可提取的通知正文".getBytes(StandardCharsets.UTF_8), "txt");

        assertTrue(result.text().contains("通知正文"));
        assertFalse(result.ocrAttempted());
        assertEquals(0, result.ocrMs());
        assertTrue(result.totalMs() >= result.tikaMs());
    }

    @Test
    void qwen37FlashUsesNonThinkingModeForStructuredJson() {
        AiService flash = new AiService(http,
            new LlmProperties("https://example.invalid", "test-only", "qwen3.7-flash", 0.1, 512, "vision", 512),
            new ObjectMapper());
        when(http.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
            .thenReturn(ResponseEntity.ok(Map.of("choices", List.of(Map.of("message",
                Map.of("content", "{\"title\":\"通知\",\"materials\":[]}"))))));

        flash.parseNoticeMeasured("通知正文");

        ArgumentCaptor<HttpEntity> request = ArgumentCaptor.forClass(HttpEntity.class);
        verify(http).postForEntity(anyString(), request.capture(), eq(Map.class));
        assertEquals(false, ((Map<?, ?>) request.getValue().getBody()).get("enable_thinking"));
    }
}
