package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eliza.aicompetition.common.AiOperationTiming;
import com.eliza.aicompetition.common.AiDiagnosticCode;
import com.eliza.aicompetition.common.FileTextExtractor;
import com.eliza.aicompetition.common.PageResult;
import com.eliza.aicompetition.common.enums.AgentTaskStatus;
import com.eliza.aicompetition.common.enums.AiCheckResult;
import com.eliza.aicompetition.common.enums.ProjectStatus;
import com.eliza.aicompetition.dto.agent.*;
import com.eliza.aicompetition.dto.ai.AiCheckResponse;
import com.eliza.aicompetition.dto.project.ProjectMaterialView;
import com.eliza.aicompetition.entity.*;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.*;
import com.eliza.aicompetition.security.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AgentService {
    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final String TYPE = "MATERIAL_CHECK";
    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private final ProjectService projectService;
    private final ReviewRecordMapper reviewRecordMapper;
    private final NotifyMessageMapper notifyMessageMapper;
    private final AgentTaskLogMapper taskMapper;
    private final ProjectAiCheckMapper checkMapper;
    private final AiService aiService;
    private final FileTextExtractor extractor;
    private final FileAssetMapper fileMapper;
    private final CompetitionProjectMapper projectMapper;
    private final CompetitionNoticeMapper noticeMapper;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final long timeoutSeconds;
    private final AiTaskObservationWriter observationWriter;

    public AgentService(ProjectService projectService, ReviewRecordMapper reviewRecordMapper,
            NotifyMessageMapper notifyMessageMapper, AgentTaskLogMapper taskMapper,
            ProjectAiCheckMapper checkMapper, AiService aiService, FileTextExtractor extractor,
            FileAssetMapper fileMapper, CompetitionProjectMapper projectMapper,
            CompetitionNoticeMapper noticeMapper,
            TransactionTemplate transactions, ObjectMapper json, AiTaskObservationWriter observationWriter,
            @Value("${material.ai.task-timeout-seconds:600}") long timeoutSeconds) {
        this.projectService = projectService;
        this.reviewRecordMapper = reviewRecordMapper;
        this.notifyMessageMapper = notifyMessageMapper;
        this.taskMapper = taskMapper;
        this.checkMapper = checkMapper;
        this.aiService = aiService;
        this.extractor = extractor;
        this.fileMapper = fileMapper;
        this.projectMapper = projectMapper;
        this.noticeMapper = noticeMapper;
        this.transactions = transactions;
        this.json = json;
        this.timeoutSeconds = timeoutSeconds;
        this.observationWriter = observationWriter;
    }

    public MaterialCheckTaskResponse checkMaterial(Long projectId) {
        String role = SecurityUtils.getCurrentUserRole();
        if (!"student".equalsIgnoreCase(role) && !"admin".equalsIgnoreCase(role))
            throw new BusinessException(403, "只有学生或管理员可以运行 AI 材料检查");
        projectService.checkProjectAccess(projectId);
        try {
            return transactions.execute(tx -> {
                CompetitionProject project = lockProject(projectId);
                requireCheckable(project);
                lockNotice(project.getNoticeId());
                List<ProjectMaterialView> materials = projectService.findCurrentMaterialViews(projectId);
                AgentTaskLog previous = latest(projectId);
                AgentTaskLog task = new AgentTaskLog();
                task.setProjectId(projectId);
                task.setToolName("checkMaterialTool");
                task.setBusinessType(TYPE);
                task.setBusinessId(projectId);
                task.setParentTaskId(previous == null ? null : previous.getTaskId());
                task.setAttemptNo(previous == null ? 1 : Math.max(1, previous.getAttemptNo() == null ? 1 : previous.getAttemptNo()) + 1);
                task.setInputSummary("审核项目材料: " + project.getProjectName());
                task.setRequestPayload(snapshot(project, materials));
                task.setExecuteStatus("PENDING");
                task.setResultOrigin("NONE");
                task.setActiveMarker(1);
                taskMapper.insert(task);
                return new MaterialCheckTaskResponse(task.getTaskId(), projectId, "PENDING");
            });
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(409, "AI 检查正在进行中");
        }
    }

    public void executeMaterialTask(Long taskId) {
        LocalDateTime started = LocalDateTime.now();
        int claimed = taskMapper.update(null, new LambdaUpdateWrapper<AgentTaskLog>()
            .eq(AgentTaskLog::getTaskId, taskId).eq(AgentTaskLog::getBusinessType, TYPE)
            .eq(AgentTaskLog::getExecuteStatus, "PENDING").eq(AgentTaskLog::getActiveMarker, 1)
            .set(AgentTaskLog::getExecuteStatus, "RUNNING").set(AgentTaskLog::getStartedAt, started));
        if (claimed != 1) return;
        AgentTaskLog task = taskMapper.selectById(taskId);
        AiOperationTiming timing = new AiOperationTiming();
        timing.taskId(taskId);
        timing.workerStarted();
        timing.attempt(task.getAttemptNo());
        timing.models(aiService.textModel(), aiService.visionModel());
        timing.prompt(AiService.MATERIAL_PROMPT_VERSION, aiService.materialPromptHash());
        com.eliza.aicompetition.common.AiTaskBudget.set(
            (task.getCreatedAt() == null ? started : task.getCreatedAt()).plusSeconds(timeoutSeconds), taskId);
        long queueMs = task.getCreatedAt() == null ? -1 : Duration.between(task.getCreatedAt(), started).toMillis();
        log.info("material_task_claimed projectId={} taskId={} queueWaitMs={} attempt={}",
            task.getBusinessId(), taskId, queueMs, task.getAttemptNo());
        try {
            if (expired(task)) { timeoutMaterialTask(taskId); return; }
            Long projectId = task.getBusinessId();
            CompetitionProject project = projectService.getProjectOrThrow(projectId);
            List<ProjectMaterialView> materials = projectService.findCurrentMaterialViews(projectId);
            if (!Objects.equals(task.getRequestPayload(), snapshot(project, materials))
                    || !checkable(project)) throw new BusinessException(409, "项目状态或材料清单已变化，请重新运行 AI 核验");
            List<ProjectMaterialView> uploaded = materials.stream()
                .filter(m -> m.getCurrentVersionId() != null && m.getFileId() != null).toList();
            StringBuilder contents = new StringBuilder();
            for (ProjectMaterialView material : uploaded) {
                FileAsset file = fileMapper.selectById(material.getFileId());
                if (file == null || file.getFileBlob() == null) continue;
                FileTextExtractor.ExtractionResult extraction = extractor.extractTextMeasured(file.getFileBlob(), file.getFileExt(), taskId);
                timing.extraction(extraction);
                if (extraction.text().startsWith("[No extractable text")
                        || extraction.text().startsWith("[Text extraction failed")
                        || extraction.text().startsWith("[File is empty")
                        || extraction.text().startsWith("[OCR unavailable")) {
                    timing.degraded();
                    if (timing.reason() == null) timing.reason(AiDiagnosticCode.INPUT_UNREADABLE);
                }
                contents.append("=== ").append(material.getRequirementName()).append("（")
                    .append(file.getFileName()).append("）===\n").append(extraction.text()).append("\n\n");
            }
            List<String> missing = materials.stream()
                .filter(m -> Integer.valueOf(1).equals(m.getRequiredFlag()) && m.getCurrentVersionId() == null)
                .map(ProjectMaterialView::getRequirementName).toList();
            long required = materials.stream().filter(m -> Integer.valueOf(1).equals(m.getRequiredFlag())).count();
            String context = String.format("项目名称: %s\n团队名称: %s\n截止日期: %s\n当前状态: %s\n已完成材料: %d/%d\n缺失材料: %s",
                project.getProjectName(), project.getTeamName() == null ? "未设置" : project.getTeamName(),
                project.getDeadline() == null ? "未设置" : project.getDeadline().format(DEADLINE_FORMATTER),
                project.getStatus(), required - missing.size(), required,
                missing.isEmpty() ? "无" : String.join("、", missing));
            AiCheckResponse response;
            if (contents.isEmpty()) {
                timing.degraded();
                timing.reason(AiDiagnosticCode.NO_REVIEWABLE_CONTENT);
                response = new AiCheckResponse(missing.isEmpty() ? "pass" : "warning",
                    missing.isEmpty() ? "系统检查通过：所有必交材料已提交。当前无可审核的文件内容。"
                        : "系统检查：以下材料尚未提交：" + String.join("、", missing) + "。请及时上传。");
            } else {
                log.info("main_model_started taskId={} operation=MATERIAL_CHECK model={} promptVersion={}",
                    taskId, aiService.textModel(), AiService.MATERIAL_PROMPT_VERSION);
                AiService.CallResult<AiCheckResponse> call = aiService.checkMaterialMeasured(context, contents.toString());
                timing.call(call.llmMs(), call.llmAttempted(), call.degraded(), call.callCount());
                timing.reason(call.fallbackReason());
                log.info("main_model_finished taskId={} attempted={} fallback={} reason={} llmMs={}",
                    taskId, call.llmAttempted(), call.degraded(), call.fallbackReason(), call.llmMs());
                response = call.result();
            }
            AiCheckResult result = missing.isEmpty() ? AiCheckResult.fromRawResult(response.reviewResult()) : AiCheckResult.WARNING;
            if (timing.reason() == AiDiagnosticCode.OCR_PARTIAL
                    || timing.reason() == AiDiagnosticCode.OCR_FAILED
                    || timing.reason() == AiDiagnosticCode.OCR_PAGE_LIMIT
                    || timing.reason() == AiDiagnosticCode.OCR_BYTE_LIMIT
                    || timing.reason() == AiDiagnosticCode.OCR_CALL_LIMIT
                    || timing.reason() == AiDiagnosticCode.OCR_BUDGET_EXHAUSTED
                    || timing.reason() == AiDiagnosticCode.INPUT_UNREADABLE) result = AiCheckResult.WARNING;
            String comment = missing.isEmpty() ? response.reviewComment()
                : "【材料缺失】以下材料尚未提交：" + String.join("、", missing) + "。\n【内容审核】" + response.reviewComment();
            if (timing.reason() == AiDiagnosticCode.OCR_PARTIAL)
                comment = "【OCR_PARTIAL：部分 PDF 页面识别失败，请按原件人工核对】\n" + comment;
            else if (timing.reason() == AiDiagnosticCode.OCR_FAILED
                    || timing.reason() == AiDiagnosticCode.OCR_PAGE_LIMIT
                    || timing.reason() == AiDiagnosticCode.OCR_BYTE_LIMIT
                    || timing.reason() == AiDiagnosticCode.OCR_CALL_LIMIT
                    || timing.reason() == AiDiagnosticCode.OCR_BUDGET_EXHAUSTED)
                comment = "【PDF OCR 不可用：请人工核对原件或提供可提取文本】\n" + comment;
            final AiCheckResult reviewResult = result;
            final String reviewComment = comment;
            transactions.executeWithoutResult(tx -> {
                AgentTaskLog locked = lockTask(taskId);
                if (locked == null || !"RUNNING".equals(locked.getExecuteStatus())) return;
                if (expired(locked)) { markTimeout(locked); return; }
                CompetitionProject current = lockProject(projectId);
                lockNotice(current.getNoticeId());
                if (!checkable(current) || !Objects.equals(task.getRequestPayload(),
                        snapshot(current, projectService.findCurrentMaterialViews(projectId)))) {
                    timing.reason(AiDiagnosticCode.INPUT_CHANGED);
                    failLocked(locked, "项目状态或材料清单已变化，请重新运行 AI 核验");
                    return;
                }
                ReviewRecord review = new ReviewRecord();
                review.setProjectId(projectId);
                review.setReviewType("ai");
                review.setReviewResult(reviewResult.name());
                review.setReviewComment(reviewComment);
                reviewRecordMapper.insert(review);
                ProjectAiCheck check = new ProjectAiCheck();
                check.setProjectId(projectId);
                check.setAgentTaskId(taskId);
                check.setMaterialSnapshot(task.getRequestPayload());
                check.setResult(reviewResult.name());
                check.setIssueSummary(abbreviate(reviewComment, 500));
                checkMapper.insert(check);
                if (reviewResult == AiCheckResult.WARNING) {
                    NotifyMessage message = new NotifyMessage();
                    message.setProjectId(projectId);
                    message.setReceiverId(current.getLeaderId());
                    message.setMsgType("material");
                    message.setMsgContent("项目 '" + current.getProjectName() + "' 材料检查结果：" + reviewResult + "。请查看审核意见并及时处理。");
                    message.setIsRead(0);
                    notifyMessageMapper.insert(message);
                }
                locked.setResultSummary(abbreviate(reviewComment, 500));
                locked.setResultOrigin(timing.isDegraded() ? "FALLBACK" : "MODEL");
                locked.setActiveMarker(null);
                locked.markSuccess();
                taskMapper.updateById(locked);
            });
        } catch (Exception failure) {
            timing.reason(failure instanceof BusinessException b && b.getCode() == 409
                ? AiDiagnosticCode.INPUT_CHANGED : AiDiagnosticCode.TASK_ERROR);
            String summary = failure instanceof BusinessException b && b.getCode() == 409
                ? b.getMessage() : "材料核验失败，请检查输入后重试";
            transactions.executeWithoutResult(tx -> {
                AgentTaskLog locked = lockTask(taskId);
                if (locked != null && "RUNNING".equals(locked.getExecuteStatus())) failLocked(locked, summary);
            });
        } finally {
            com.eliza.aicompetition.common.AiTaskBudget.clear();
            AgentTaskLog done = taskMapper.selectById(taskId);
            observationWriter.write(taskId, done, timing, null);
            timing.log(log, TYPE, task.getBusinessId(), done == null ? "UNKNOWN" : done.getExecuteStatus());
            log.info("material_task_finished projectId={} taskId={} status={} queueWaitMs={} attempt={}",
                task.getBusinessId(), taskId, done == null ? "UNKNOWN" : done.getExecuteStatus(), queueMs, task.getAttemptNo());
        }
    }

    public void timeoutMaterialTask(Long taskId) {
        transactions.executeWithoutResult(tx -> {
            AgentTaskLog task = lockTask(taskId);
            if (task != null && ("PENDING".equals(task.getExecuteStatus()) || "RUNNING".equals(task.getExecuteStatus()))
                    && expired(task)) markTimeout(task);
        });
        AgentTaskLog done = taskMapper.selectById(taskId);
        if (done != null && "TIMEOUT".equals(done.getExecuteStatus()))
            observationWriter.write(taskId, done, new AiOperationTiming(), AiDiagnosticCode.TASK_TIMEOUT);
    }

    public void failAbandonedMaterialTask(Long taskId) {
        transactions.executeWithoutResult(tx -> {
            AgentTaskLog task = lockTask(taskId);
            if (task == null || !"RUNNING".equals(task.getExecuteStatus())) return;
            if (expired(task)) markTimeout(task);
            else failLocked(task, "应用重启时工作线程已中断，请重新运行 AI 核验");
        });
        AgentTaskLog done = taskMapper.selectById(taskId);
        if (done != null && ("FAILED".equals(done.getExecuteStatus()) || "TIMEOUT".equals(done.getExecuteStatus())))
            observationWriter.write(taskId, done, new AiOperationTiming(),
                "TIMEOUT".equals(done.getExecuteStatus()) ? AiDiagnosticCode.TASK_TIMEOUT : AiDiagnosticCode.WORKER_INTERRUPTED);
    }

    public MaterialTaskView findMaterialTask(Long taskId) {
        AgentTaskLog task = taskMapper.selectById(taskId);
        if (task == null || !TYPE.equals(task.getBusinessType())) throw new BusinessException(404, "材料核验任务不存在");
        projectService.checkProjectReadAccess(task.getBusinessId());
        return taskView(task);
    }

    public MaterialTaskView latestMaterialTask(Long projectId) {
        projectService.checkProjectReadAccess(projectId);
        AgentTaskLog task = latest(projectId);
        return task == null ? null : taskView(task);
    }

    private MaterialTaskView taskView(AgentTaskLog task) {
        return new MaterialTaskView(task.getTaskId(), task.getBusinessId(), task.getExecuteStatus(),
            task.getResultOrigin(), task.getAttemptNo(), task.getCreatedAt(), task.getStartedAt(),
            task.getFinishedAt(), task.getErrorMessage());
    }

    private AgentTaskLog latest(Long projectId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AgentTaskLog>()
            .eq(AgentTaskLog::getBusinessType, TYPE).eq(AgentTaskLog::getBusinessId, projectId)
            .orderByDesc(AgentTaskLog::getTaskId).last("LIMIT 1"));
    }

    private AgentTaskLog lockTask(Long taskId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<AgentTaskLog>()
            .eq(AgentTaskLog::getTaskId, taskId).eq(AgentTaskLog::getBusinessType, TYPE).last("FOR UPDATE"));
    }

    private CompetitionProject lockProject(Long projectId) {
        CompetitionProject project = projectMapper.selectOne(new LambdaQueryWrapper<CompetitionProject>()
            .eq(CompetitionProject::getProjectId, projectId).last("FOR UPDATE"));
        if (project == null) throw new BusinessException(404, "项目不存在");
        return project;
    }

    /** Requirement confirmation updates this notice row before changing its ACTIVE set. */
    private void lockNotice(Long noticeId) {
        if (noticeMapper.selectOne(new LambdaQueryWrapper<CompetitionNotice>()
                .eq(CompetitionNotice::getNoticeId, noticeId).last("FOR UPDATE")) == null)
            throw new BusinessException(404, "通知不存在");
    }

    private boolean expired(AgentTaskLog task) {
        LocalDateTime origin = task.getCreatedAt() == null ? task.getStartedAt() : task.getCreatedAt();
        return origin != null && !LocalDateTime.now().isBefore(origin.plusSeconds(timeoutSeconds));
    }

    private void markTimeout(AgentTaskLog task) {
        task.setActiveMarker(null);
        task.setResultOrigin("NONE");
        task.setErrorMessage("材料核验任务超过截止时间，请重新运行");
        task.markTimeout();
        taskMapper.updateById(task);
    }

    private void failLocked(AgentTaskLog task, String summary) {
        task.setActiveMarker(null);
        task.setResultOrigin("NONE");
        task.markFailed(summary);
        taskMapper.updateById(task);
    }

    private boolean checkable(CompetitionProject project) {
        return project.getProjectStatus() == ProjectStatus.DRAFT
            || project.getProjectStatus() == ProjectStatus.REVISION_REQUIRED;
    }

    private void requireCheckable(CompetitionProject project) {
        if (!checkable(project)) throw new BusinessException(409, "当前项目状态不允许运行 AI 材料检查");
    }

    /** Versioned fingerprint includes every ACTIVE requirement, including missing and optional items. */
    private String snapshot(CompetitionProject project, List<ProjectMaterialView> materials) {
        try {
            var sorted = materials.stream().sorted(Comparator.comparing(ProjectMaterialView::getRequirementId)).toList();
            var rows = json.createArrayNode();
            var versions = json.createArrayNode();
            var requirementIds = json.createArrayNode();
            for (ProjectMaterialView item : sorted) {
                var row = rows.addObject();
                row.put("id", item.getRequirementId());
                row.put("required", item.getRequiredFlag());
                row.put("name", item.getRequirementName());
                row.put("description", item.getDescription());
                row.put("version", item.getCurrentVersionId());
                row.put("file", item.getFileId());
                var identity = requirementIds.addObject();
                identity.put("id", item.getRequirementId());
                identity.put("required", item.getRequiredFlag());
                identity.put("currentVersionId", item.getCurrentVersionId());
                if (item.getCurrentVersionId() != null) versions.add(item.getCurrentVersionId());
            }
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(rows));
            var root = json.createObjectNode();
            root.put("v", 2);
            root.put("projectId", project.getProjectId());
            root.put("projectStatus", project.getStatus());
            root.put("projectName", project.getProjectName());
            root.put("teamName", project.getTeamName());
            root.put("deadline", project.getDeadline() == null ? null : project.getDeadline().toString());
            root.put("requirementsSha256", HexFormat.of().formatHex(digest));
            root.set("requirements", requirementIds);
            root.set("materialVersionIds", versions);
            return root.toString();
        } catch (Exception error) {
            throw new IllegalStateException("无法建立材料输入快照", error);
        }
    }

    public List<ProjectAiCheckView> listProjectChecks(Long projectId) {
        projectService.checkProjectReadAccess(projectId);
        String current = snapshot(projectService.getProjectOrThrow(projectId),
            projectService.findCurrentMaterialViews(projectId));
        return checkMapper.selectList(new LambdaQueryWrapper<ProjectAiCheck>()
            .eq(ProjectAiCheck::getProjectId, projectId).orderByDesc(ProjectAiCheck::getCreatedAt)
            .orderByDesc(ProjectAiCheck::getId)).stream().map(item -> {
                List<Long> versions = parseVersions(item.getMaterialSnapshot());
                // Legacy version-only snapshots cannot prove ACTIVE requirement identity; treat conservatively as stale.
                boolean stale = !Objects.equals(current, item.getMaterialSnapshot());
                return new ProjectAiCheckView(item.getId(), item.getProjectId(), item.getResult(),
                    item.getIssueSummary(), versions, stale, item.getCreatedAt());
            }).toList();
    }

    private List<Long> parseVersions(String raw) {
        if (raw == null) return List.of();
        try {
            JsonNode node = json.readTree(raw);
            if (node.isObject()) node = node.path("materialVersionIds");
            if (node.isArray()) {
                List<Long> ids = new ArrayList<>();
                node.forEach(value -> { if (value.canConvertToLong()) ids.add(value.asLong()); });
                return ids;
            }
        } catch (Exception ignored) { /* Historical comma-separated snapshots. */ }
        try {
            return Arrays.stream(raw.replace("[", "").replace("]", "").split(","))
                .map(String::trim).filter(s -> !s.isBlank()).map(Long::valueOf).toList();
        } catch (NumberFormatException ignored) { return List.of(); }
    }

    private String abbreviate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    public PageResult<AgentTaskLogResponse> listTaskLogs(Long projectId, String toolName, int pageNum, int pageSize) {
        if (projectId == null) {
            if (!"admin".equalsIgnoreCase(SecurityUtils.getCurrentUserRole()))
                throw new BusinessException(403, "只有管理员可以查询全部AI任务日志");
        } else projectService.checkProjectReadAccess(projectId);
        LambdaQueryWrapper<AgentTaskLog> query = new LambdaQueryWrapper<>();
        if (projectId != null) query.eq(AgentTaskLog::getProjectId, projectId);
        if (toolName != null && !toolName.isBlank()) query.eq(AgentTaskLog::getToolName, toolName);
        query.orderByDesc(AgentTaskLog::getCreatedAt);
        Page<AgentTaskLog> page = taskMapper.selectPage(new Page<>(pageNum, pageSize), query);
        return PageResult.of(page, page.getRecords().stream().map(task -> new AgentTaskLogResponse(
            task.getTaskId(), task.getProjectId(), task.getToolName(), task.getInputSummary(),
            task.getResultSummary() == null ? null : abbreviate(task.getResultSummary(), 200),
            task.getExecuteStatus(), task.getResultOrigin(), task.getCreatedAt())).collect(Collectors.toList()));
    }
}
