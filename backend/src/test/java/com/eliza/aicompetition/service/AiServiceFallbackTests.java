package com.eliza.aicompetition.service;

import com.eliza.aicompetition.config.LlmProperties;
import com.eliza.aicompetition.common.AiDiagnosticCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.List;
import java.net.SocketTimeoutException;
import java.net.ConnectException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiServiceFallbackTests {
    private final AiService ai = new AiService(new RestTemplate(),
        new LlmProperties("https://unused.invalid", "", "qwen3.7-flash", 0.1, 2048,
            "qwen3.7-flash", 4096), new ObjectMapper());

    @Test
    void noKeyReturnsExplicitDegradedResultsWithoutHttpAttempt() {
        var notice = ai.parseNoticeMeasured("通知正文");
        assertTrue(notice.degraded());
        assertFalse(notice.llmAttempted());
        assertEquals(AiDiagnosticCode.API_KEY_MISSING, notice.fallbackReason());
        assertTrue(notice.result().materials().isEmpty());

        var material = ai.checkMaterialMeasured("项目", "材料正文");
        assertTrue(material.degraded());
        assertFalse(material.llmAttempted());
        assertEquals(AiDiagnosticCode.API_KEY_MISSING, material.fallbackReason());
        assertTrue(material.result().reviewComment().contains("人工复核"));
    }

    @Test
    void httpAndMalformedResponseHaveStableDistinctCategories() {
        RestTemplate http = mock(RestTemplate.class);
        when(http.postForEntity(anyString(), any(), eq(Map.class)))
            .thenThrow(new HttpServerErrorException(HttpStatus.BAD_GATEWAY));
        AiService failed = new AiService(http, configured(), new ObjectMapper());
        var httpResult = failed.parseNoticeMeasured("通知");
        assertEquals(AiDiagnosticCode.MODEL_HTTP_ERROR, httpResult.fallbackReason());
        assertTrue(httpResult.llmAttempted());

        RestTemplate malformed = mock(RestTemplate.class);
        when(malformed.postForEntity(anyString(), any(), eq(Map.class))).thenReturn(
            ResponseEntity.ok(Map.of("choices", List.of(Map.of("message", Map.of("content", "not-json"))))));
        var invalid = new AiService(malformed, configured(), new ObjectMapper()).parseNoticeMeasured("通知");
        assertEquals(AiDiagnosticCode.MODEL_INVALID_RESPONSE, invalid.fallbackReason());
    }

    @Test
    void transportFailureAndSocketTimeoutAreNotConflated() {
        RestTemplate timeout = mock(RestTemplate.class);
        when(timeout.postForEntity(anyString(), any(), eq(Map.class)))
            .thenThrow(new ResourceAccessException("timeout", new SocketTimeoutException()));
        assertEquals(AiDiagnosticCode.MODEL_TIMEOUT,
            new AiService(timeout, configured(), new ObjectMapper()).parseNoticeMeasured("通知").fallbackReason());

        RestTemplate unavailable = mock(RestTemplate.class);
        when(unavailable.postForEntity(anyString(), any(), eq(Map.class)))
            .thenThrow(new ResourceAccessException("connect", new ConnectException()));
        assertEquals(AiDiagnosticCode.MODEL_TRANSPORT_ERROR,
            new AiService(unavailable, configured(), new ObjectMapper()).parseNoticeMeasured("通知").fallbackReason());
    }

    private static LlmProperties configured() {
        return new LlmProperties("https://unused.invalid", "fake-test-key", "qwen3.7-flash", 0.1, 2048,
            "vision-test", 4096);
    }
}
