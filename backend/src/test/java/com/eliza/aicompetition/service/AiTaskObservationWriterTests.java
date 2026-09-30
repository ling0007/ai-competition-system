package com.eliza.aicompetition.service;

import com.eliza.aicompetition.common.AiDiagnosticCode;
import com.eliza.aicompetition.common.AiOperationTiming;
import com.eliza.aicompetition.common.FileTextExtractor;
import com.eliza.aicompetition.entity.AgentTaskLog;
import com.eliza.aicompetition.mapper.AgentTaskLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AiTaskObservationWriterTests {
    @Test
    void telemetryFailureCannotChangeCommittedTaskResult() {
        AgentTaskLogMapper mapper = mock(AgentTaskLogMapper.class);
        when(mapper.updateObservation(anyLong(), any(), any(), any(), any(), anyBoolean()))
            .thenThrow(new IllegalStateException("database telemetry unavailable"));
        AgentTaskLog terminal = terminal();
        AiOperationTiming timing = new AiOperationTiming();
        timing.workerStarted();
        assertDoesNotThrow(() -> new AiTaskObservationWriter(mapper, new ObjectMapper())
            .write(17L, terminal, timing, AiDiagnosticCode.OCR_PARTIAL));
        assertTrue("SUCCESS".equals(terminal.getExecuteStatus()));
        assertTrue("MODEL".equals(terminal.getResultOrigin()));
    }

    @Test
    void payloadIsAWhitelistAndNeverContainsPromptOrDocumentBody() {
        AgentTaskLogMapper mapper = mock(AgentTaskLogMapper.class);
        AiOperationTiming timing = new AiOperationTiming();
        timing.workerStarted();
        timing.textPath();
        timing.models("text-model", "vision-model");
        timing.prompt("notice-parse-v1", "hash-only");
        timing.call(12, true, false);
        new AiTaskObservationWriter(mapper, new ObjectMapper()).write(17L, terminal(), timing, null);
        var captured = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(mapper).updateObservation(eq(17L), eq("SUCCESS"), captured.capture(), any(), any(), eq(true));
        String payload = captured.getValue();
        assertTrue(payload.contains("notice-parse-v1"));
        assertFalse(payload.contains("promptBody"));
        assertFalse(payload.contains("rawText"));
        assertFalse(payload.contains("apiKey"));
        assertFalse(payload.contains("responsePayload"));
    }

    @Test
    void partialOcrDoesNotChangeModelOriginButRecordsMissingCoverage() {
        AiOperationTiming timing = new AiOperationTiming();
        timing.extraction(new FileTextExtractor.ExtractionResult("private document body", 90, 10, 80,
            true, "OCR", 12000, 10, 3, 2, 1, 7));
        timing.call(12, true, false);
        assertEquals(AiDiagnosticCode.OCR_PARTIAL, timing.reason());
        var payload = timing.observation("SUCCESS", "MODEL");
        assertEquals(10, payload.get("pdfPages"));
        assertEquals(1, payload.get("ocrFailedPages"));
        assertEquals(7, payload.get("ocrSkippedPages"));
        assertEquals("MODEL", payload.get("resultOrigin"));
        assertFalse(payload.toString().contains("private document body"));
    }

    private AgentTaskLog terminal() {
        AgentTaskLog task = new AgentTaskLog();
        task.setExecuteStatus("SUCCESS");
        task.setResultOrigin("MODEL");
        task.setCreatedAt(LocalDateTime.now().minusSeconds(2));
        task.setStartedAt(LocalDateTime.now().minusSeconds(1));
        task.setFinishedAt(LocalDateTime.now());
        return task;
    }
}
