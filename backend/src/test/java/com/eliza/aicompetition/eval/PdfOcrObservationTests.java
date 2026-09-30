package com.eliza.aicompetition.eval;

import com.eliza.aicompetition.common.PdfOcrExtractor;
import com.eliza.aicompetition.config.LlmProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PdfOcrObservationTests {
    @Test
    void transientFailureIsRetriedAndAllPagesAreAttempted() throws Exception {
        var sample = NoticeEvalGold.load().path("samples").get(2);
        byte[] pdf = Files.readAllBytes(NoticeEvalGold.samplePath(sample));
        RestTemplate http = mock(RestTemplate.class);
        AtomicInteger calls = new AtomicInteger();
        when(http.postForEntity(anyString(), any(), eq(Map.class))).thenAnswer(invocation -> {
            if (calls.incrementAndGet() == 1) throw new HttpServerErrorException(HttpStatus.BAD_GATEWAY);
            return ResponseEntity.ok(Map.of("choices", List.of(Map.of("message", Map.of("content", "PRIVATE_OCR_MARKER")))));
        });
        var extractor = new PdfOcrExtractor(http,
            new LlmProperties("https://unused.invalid", "fake-test-key", "unused", 0.1, 2048, "vision-test", 4096));
        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PdfOcrExtractor.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        PdfOcrExtractor.OcrResult result;
        try {
            result = extractor.ocrPdfMeasured(pdf, 123L);
        } finally {
            logger.detachAppender(appender);
        }
        assertNotNull(result.text());
        assertEquals(10, result.totalPages());
        assertEquals(10, result.attemptedPages());
        assertEquals(10, result.succeededPages());
        assertEquals(0, result.failedPages());
        assertEquals(0, result.skippedPages());
        assertEquals(11, result.visionCalls());
        String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
            .collect(java.util.stream.Collectors.joining("\n"));
        org.junit.jupiter.api.Assertions.assertFalse(logs.contains("PRIVATE_OCR_MARKER"));
        org.junit.jupiter.api.Assertions.assertFalse(logs.contains("fake-test-key"));
    }
}
