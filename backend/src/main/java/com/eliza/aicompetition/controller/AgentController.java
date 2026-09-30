package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.common.PageResult;
import com.eliza.aicompetition.dto.agent.AgentTaskLogResponse;
import com.eliza.aicompetition.dto.agent.MaterialCheckTaskResponse;
import com.eliza.aicompetition.dto.agent.MaterialTaskView;
import com.eliza.aicompetition.dto.agent.ProjectAiCheckView;
import com.eliza.aicompetition.service.AgentService;
import com.eliza.aicompetition.service.NoticeAiTaskDispatcher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentService agentService;
    private final NoticeAiTaskDispatcher dispatcher;

    public AgentController(AgentService agentService, NoticeAiTaskDispatcher dispatcher) {
        this.agentService = agentService;
        this.dispatcher = dispatcher;
    }

    @PostMapping("/check-material/{projectId}")
    public ApiResponse<MaterialCheckTaskResponse> checkMaterial(@PathVariable Long projectId) {
        MaterialCheckTaskResponse accepted = agentService.checkMaterial(projectId);
        dispatcher.dispatchMaterial(accepted.taskId());
        return ApiResponse.success("AI材料核验任务已接受", accepted);
    }

    @GetMapping("/material-tasks/{taskId}")
    public ApiResponse<MaterialTaskView> materialTask(@PathVariable Long taskId) {
        return ApiResponse.success(agentService.findMaterialTask(taskId));
    }

    @GetMapping("/projects/{projectId}/material-task/latest")
    public ApiResponse<MaterialTaskView> latestMaterialTask(@PathVariable Long projectId) {
        return ApiResponse.success(agentService.latestMaterialTask(projectId));
    }

    @GetMapping("/task-logs")
    public ApiResponse<PageResult<AgentTaskLogResponse>> listTaskLogs(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) String toolName,
        @RequestParam(required = false, defaultValue = "1") int pageNum,
        @RequestParam(required = false, defaultValue = "10") int pageSize
    ) {
        return ApiResponse.success("审计日志查询成功",
            agentService.listTaskLogs(projectId, toolName, pageNum, pageSize));
    }

    @GetMapping("/projects/{projectId}/checks")
    public ApiResponse<List<ProjectAiCheckView>> listProjectChecks(@PathVariable Long projectId) {
        return ApiResponse.success("AI 检查历史查询成功", agentService.listProjectChecks(projectId));
    }
}
