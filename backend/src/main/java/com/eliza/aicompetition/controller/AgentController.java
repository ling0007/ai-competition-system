package com.eliza.aicompetition.controller;

import com.eliza.aicompetition.common.ApiResponse;
import com.eliza.aicompetition.dto.agent.AgentTaskLogResponse;
import com.eliza.aicompetition.dto.agent.MaterialCheckResponse;
import com.eliza.aicompetition.service.AgentService;
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

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/check-material/{projectId}")
    public ApiResponse<MaterialCheckResponse> checkMaterial(@PathVariable Long projectId) {
        return ApiResponse.success("AI材料检查完成", agentService.checkMaterial(projectId));
    }

    @GetMapping("/task-logs")
    public ApiResponse<List<AgentTaskLogResponse>> listTaskLogs(
        @RequestParam(required = false) Long projectId,
        @RequestParam(required = false) String toolName
    ) {
        return ApiResponse.success("审计日志查询成功", agentService.listTaskLogs(projectId, toolName));
    }
}
