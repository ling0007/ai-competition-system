package com.eliza.aicompetition.service;

import com.eliza.aicompetition.common.AiDiagnosticCode;
import com.eliza.aicompetition.common.AiOperationTiming;
import com.eliza.aicompetition.entity.AgentTaskLog;
import com.eliza.aicompetition.mapper.AgentTaskLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;

/** Best-effort terminal observation. This is deliberately outside the business transaction. */
@Component
public class AiTaskObservationWriter {
    private static final Logger log = LoggerFactory.getLogger(AiTaskObservationWriter.class);
    private final AgentTaskLogMapper mapper;
    private final ObjectMapper json;

    public AiTaskObservationWriter(AgentTaskLogMapper mapper, ObjectMapper json) {
        this.mapper = mapper;
        this.json = json;
    }

    public void write(Long taskId, AgentTaskLog terminal, AiOperationTiming timing, AiDiagnosticCode code) {
        if (terminal == null || !isTerminal(terminal.getExecuteStatus())) return;
        String status = terminal.getExecuteStatus();
        String origin = terminal.getResultOrigin();
        AiDiagnosticCode effective = code == null ? timing.reason() : code;
        if ("TIMEOUT".equals(status)) effective = AiDiagnosticCode.TASK_TIMEOUT;
        if ("FAILED".equals(status) && effective == null) effective = AiDiagnosticCode.TASK_ERROR;
        if ("SUCCESS".equals(status) && "FALLBACK".equals(origin) && effective == null)
            effective = AiDiagnosticCode.UNSPECIFIED_FALLBACK;
        String error = "SUCCESS".equals(status) || effective == null ? null : effective.name();
        String degradation = "SUCCESS".equals(status) && effective != null ? effective.name() : null;
        try {
            Map<String, Object> payload = timing.observation(status, origin);
            LocalDateTime created = terminal.getCreatedAt();
            LocalDateTime started = terminal.getStartedAt();
            LocalDateTime finished = terminal.getFinishedAt();
            payload.put("queueWaitMs", created != null && started != null
                ? Math.max(0, Duration.between(created, started).toMillis()) : null);
            payload.put("taskTotalMs", created != null && finished != null
                ? Math.max(0, Duration.between(created, finished).toMillis()) : null);
            int written = mapper.updateObservation(taskId, status,
                json.writeValueAsString(payload), error, degradation, timing.workerMeasured());
            if (written == 1)
                log.info("ai_trace taskId={} status={} origin={} errorCategory={} degradationReason={}",
                    taskId, status, origin, error, degradation);
        } catch (Exception observationFailure) {
            // Never roll back a draft/review and terminal task state for telemetry.
            log.warn("ai_observation_write_failed taskId={} errorType={}",
                taskId, observationFailure.getClass().getSimpleName());
        }
    }

    private boolean isTerminal(String status) {
        return "SUCCESS".equals(status) || "FAILED".equals(status) || "TIMEOUT".equals(status);
    }
}
