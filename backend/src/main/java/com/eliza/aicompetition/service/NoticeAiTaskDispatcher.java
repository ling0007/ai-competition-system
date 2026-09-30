package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.entity.AgentTaskLog;
import com.eliza.aicompetition.mapper.AgentTaskLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** DB remains the queue of record; duplicate submissions are fenced by claim CAS. */
@Service
public class NoticeAiTaskDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NoticeAiTaskDispatcher.class);
    private final NoticeService noticeService;
    private final AgentService agentService;
    private final AgentTaskLogMapper taskMapper;
    private final ThreadPoolTaskExecutor executor;
    private final Set<Long> submitted = ConcurrentHashMap.newKeySet();

    public NoticeAiTaskDispatcher(NoticeService noticeService, AgentService agentService, AgentTaskLogMapper taskMapper,
                                  @Qualifier("aiExecutor") ThreadPoolTaskExecutor executor) {
        this.noticeService = noticeService;
        this.agentService = agentService;
        this.taskMapper = taskMapper;
        this.executor = executor;
    }

    public void dispatch(Long taskId) {
        submit(taskId, "NOTICE_PARSE");
    }

    public void dispatchMaterial(Long taskId) {
        submit(taskId, "MATERIAL_CHECK");
    }

    private void submit(Long taskId, String type) {
        if (!submitted.add(taskId)) return;
        try {
            executor.execute(() -> {
                try {
                    if ("MATERIAL_CHECK".equals(type)) agentService.executeMaterialTask(taskId);
                    else noticeService.executeParseTask(taskId);
                } finally {
                    submitted.remove(taskId);
                }
            });
        } catch (TaskRejectedException rejected) {
            submitted.remove(taskId);
            // The durable PENDING row is picked up by the next scan, or times out.
            log.warn("ai_task_dispatch_rejected type={} taskId={} active={} queued={}",
                type, taskId, executor.getActiveCount(), executor.getThreadPoolExecutor().getQueue().size());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        // Single-instance deployment: there are no surviving workers from the previous process.
        for (AgentTaskLog task : activeTasks("RUNNING")) {
            noticeService.failAbandonedRunningTask(task.getTaskId());
        }
        for (AgentTaskLog task : activeTasks("MATERIAL_CHECK", "RUNNING")) {
            agentService.failAbandonedMaterialTask(task.getTaskId());
        }
        scan();
    }

    @Scheduled(fixedDelayString = "${notice.ai.recovery-scan-ms:5000}")
    public void scan() {
        for (AgentTaskLog task : activeTasks("RUNNING")) {
            noticeService.timeoutParseTask(task.getTaskId());
        }
        for (AgentTaskLog task : activeTasks("MATERIAL_CHECK", "RUNNING")) {
            agentService.timeoutMaterialTask(task.getTaskId());
        }
        for (AgentTaskLog task : activeTasks("PENDING")) {
            noticeService.timeoutParseTask(task.getTaskId());
            dispatch(task.getTaskId());
        }
        for (AgentTaskLog task : activeTasks("MATERIAL_CHECK", "PENDING")) {
            agentService.timeoutMaterialTask(task.getTaskId());
            dispatchMaterial(task.getTaskId());
        }
    }

    private List<AgentTaskLog> activeTasks(String status) {
        return activeTasks("NOTICE_PARSE", status);
    }

    private List<AgentTaskLog> activeTasks(String type, String status) {
        return taskMapper.selectList(new LambdaQueryWrapper<AgentTaskLog>()
            .eq(AgentTaskLog::getBusinessType, type)
            .eq(AgentTaskLog::getExecuteStatus, status)
            .eq(AgentTaskLog::getActiveMarker, 1)
            .orderByAsc(AgentTaskLog::getTaskId).last("LIMIT 100"));
    }
}
