package com.eliza.aicompetition.service;

import com.eliza.aicompetition.common.FileTextExtractor;
import com.eliza.aicompetition.dto.agent.AgentTaskLogResponse;
import com.eliza.aicompetition.dto.agent.MaterialCheckResponse;
import com.eliza.aicompetition.dto.ai.AiCheckResult;
import com.eliza.aicompetition.dto.project.ProjectMaterialView;
import com.eliza.aicompetition.dto.project.ProjectProgressResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.entity.AgentTaskLog;
import com.eliza.aicompetition.entity.CompetitionProject;
import com.eliza.aicompetition.entity.FileAsset;
import com.eliza.aicompetition.entity.NotifyMessage;
import com.eliza.aicompetition.entity.ReviewRecord;
import com.eliza.aicompetition.mapper.AgentTaskLogMapper;
import com.eliza.aicompetition.mapper.FileAssetMapper;
import com.eliza.aicompetition.mapper.NotifyMessageMapper;
import com.eliza.aicompetition.mapper.ProjectMaterialMapper;
import com.eliza.aicompetition.mapper.ReviewRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AgentService {

    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProjectService projectService;
    private final ReviewRecordMapper reviewRecordMapper;
    private final NotifyMessageMapper notifyMessageMapper;
    private final AgentTaskLogMapper agentTaskLogMapper;
    private final AiService aiService;
    private final FileTextExtractor fileTextExtractor;
    private final FileAssetMapper fileAssetMapper;
    private final ProjectMaterialMapper projectMaterialMapper;

    public AgentService(
        ProjectService projectService,
        ReviewRecordMapper reviewRecordMapper,
        NotifyMessageMapper notifyMessageMapper,
        AgentTaskLogMapper agentTaskLogMapper,
        AiService aiService,
        FileTextExtractor fileTextExtractor,
        FileAssetMapper fileAssetMapper,
        ProjectMaterialMapper projectMaterialMapper
    ) {
        this.projectService = projectService;
        this.reviewRecordMapper = reviewRecordMapper;
        this.notifyMessageMapper = notifyMessageMapper;
        this.agentTaskLogMapper = agentTaskLogMapper;
        this.aiService = aiService;
        this.fileTextExtractor = fileTextExtractor;
        this.fileAssetMapper = fileAssetMapper;
        this.projectMaterialMapper = projectMaterialMapper;
    }

    @Transactional
    public MaterialCheckResponse checkMaterial(Long projectId) {
        CompetitionProject project = projectService.getProjectOrThrow(projectId);
        ProjectProgressResponse progress = projectService.refreshProjectProgress(projectId);

        // 1. Collect submitted materials with file content for LLM review
        List<ProjectMaterialView> allMaterials = projectMaterialMapper.findLatestDetailsByProjectId(projectId);
        List<ProjectMaterialView> submittedMaterials = allMaterials.stream()
            .filter(m -> "submitted".equalsIgnoreCase(m.getSubmitStatus()) && m.getFileId() != null)
            .toList();

        // 2. Extract text from submitted files
        StringBuilder fileContentsBuilder = new StringBuilder();
        for (ProjectMaterialView material : submittedMaterials) {
            FileAsset fileAsset = fileAssetMapper.selectById(material.getFileId());
            if (fileAsset != null && fileAsset.getFileBlob() != null) {
                String extracted = fileTextExtractor.extractText(fileAsset.getFileBlob(), fileAsset.getFileExt());
                fileContentsBuilder.append("=== ").append(material.getRequirementName())
                    .append("（").append(fileAsset.getFileName()).append("）===\n")
                    .append(extracted).append("\n\n");
            }
        }
        String extractedText = fileContentsBuilder.toString();

        // 3. Build project context for LLM
        String projectContext = String.format(
            "项目名称: %s\n团队名称: %s\n截止日期: %s\n当前状态: %s\n已完成材料: %d/%d\n缺失材料: %s",
            project.getProjectName(),
            project.getTeamName() != null ? project.getTeamName() : "未设置",
            project.getDeadline() != null ? project.getDeadline().format(DEADLINE_FORMATTER) : "未设置",
            progress.getStatus(),
            progress.getSubmittedTotal(),
            progress.getRequiredTotal(),
            progress.getMissingMaterials().isEmpty() ? "无" : String.join("、", progress.getMissingMaterials())
        );

        // 4. Call LLM for content review (or use completeness-only fallback)
        AiCheckResult aiResult;
        if (extractedText.isBlank()) {
            // No submitted files with extractable content — base result on completeness
            aiResult = new AiCheckResult(
                progress.getMissingMaterials().isEmpty() ? "pass" : "warning",
                progress.getMissingMaterials().isEmpty()
                    ? "系统检查通过：所有必交材料已提交。当前无可审核的文件内容。"
                    : "系统检查：以下材料尚未提交：" + String.join("、", progress.getMissingMaterials()) + "。请及时上传。"
            );
        } else {
            aiResult = aiService.checkMaterial(projectContext, extractedText);
        }

        // 5. Merge LLM result with completeness requirement
        String finalResult;
        String finalComment;
        if (!progress.getMissingMaterials().isEmpty()) {
            finalResult = "warning";
            finalComment = "【材料缺失】以下材料尚未提交：" + String.join("、", progress.getMissingMaterials()) + "。\n"
                + "【内容审核】" + aiResult.reviewComment();
        } else {
            finalResult = aiResult.reviewResult();
            finalComment = aiResult.reviewComment();
        }

        // 6. Save review record
        ReviewRecord reviewRecord = new ReviewRecord();
        reviewRecord.setProjectId(projectId);
        reviewRecord.setReviewType("ai");
        reviewRecord.setReviewResult(finalResult);
        reviewRecord.setReviewComment(finalComment);
        reviewRecordMapper.insert(reviewRecord);

        // 7. Save agent task log
        AgentTaskLog taskLog = new AgentTaskLog();
        taskLog.setProjectId(projectId);
        taskLog.setToolName("checkMaterialTool");
        taskLog.setInputSummary("审核项目材料: " + project.getProjectName()
            + ", 已提交=" + progress.getSubmittedTotal() + "/" + progress.getRequiredTotal()
            + ", 可审核文件=" + submittedMaterials.size());
        taskLog.setResultSummary(finalComment.length() > 500 ? finalComment.substring(0, 500) + "..." : finalComment);
        taskLog.setExecuteStatus("success");
        agentTaskLogMapper.insert(taskLog);

        // 8. Create notification if there are issues
        if (!"pass".equals(finalResult)) {
            NotifyMessage notifyMessage = new NotifyMessage();
            notifyMessage.setProjectId(projectId);
            notifyMessage.setReceiverId(project.getLeaderId());
            notifyMessage.setMsgType("material");
            notifyMessage.setMsgContent("项目 '" + project.getProjectName() + "' 材料检查结果：" + finalResult
                + "。请查看审核意见并及时处理。");
            notifyMessage.setIsRead(0);
            notifyMessageMapper.insert(notifyMessage);
        }

        return new MaterialCheckResponse(
            projectId,
            project.getProjectName(),
            finalResult,
            finalComment,
            progress.getCompletionRate(),
            progress.getMissingMaterials()
        );
    }

    public List<AgentTaskLogResponse> listTaskLogs(Long projectId, String toolName) {
        LambdaQueryWrapper<AgentTaskLog> queryWrapper = new LambdaQueryWrapper<>();
        if (projectId != null) {
            queryWrapper.eq(AgentTaskLog::getProjectId, projectId);
        }
        if (toolName != null && !toolName.isBlank()) {
            queryWrapper.eq(AgentTaskLog::getToolName, toolName);
        }
        queryWrapper.orderByDesc(AgentTaskLog::getCreatedAt);

        List<AgentTaskLog> logs = agentTaskLogMapper.selectList(queryWrapper);
        return logs.stream()
            .map(log -> new AgentTaskLogResponse(
                log.getTaskId(),
                log.getProjectId(),
                log.getToolName(),
                log.getInputSummary(),
                log.getResultSummary() != null && log.getResultSummary().length() > 200
                    ? log.getResultSummary().substring(0, 200) + "..."
                    : log.getResultSummary(),
                log.getExecuteStatus(),
                log.getCreatedAt()
            ))
            .collect(Collectors.toList());
    }
}
