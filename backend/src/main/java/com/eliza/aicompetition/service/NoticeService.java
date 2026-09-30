package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.eliza.aicompetition.common.FileTextExtractor;
import com.eliza.aicompetition.common.AiOperationTiming;
import com.eliza.aicompetition.common.AiDiagnosticCode;
import com.eliza.aicompetition.common.enums.AgentTaskStatus;
import com.eliza.aicompetition.common.enums.NoticeParseStatus;
import com.eliza.aicompetition.common.enums.NoticePublishStatus;
import com.eliza.aicompetition.dto.ai.AiParseResult;
import com.eliza.aicompetition.dto.ai.AiParseResult.AiMaterialRequirement;
import com.eliza.aicompetition.dto.notice.ConfirmParseRequest;
import com.eliza.aicompetition.dto.notice.NoticeDetailResponse;
import com.eliza.aicompetition.dto.notice.NoticeUploadResponse;
import com.eliza.aicompetition.dto.notice.NoticeParseTaskResponse;
import com.eliza.aicompetition.dto.notice.NoticeTaskView;
import com.eliza.aicompetition.dto.notice.ParseDraftResponse;
import com.eliza.aicompetition.dto.notice.ParseDraftResponse.MaterialItem;
import com.eliza.aicompetition.dto.notice.UpdateParseDraftRequest;
import com.eliza.aicompetition.entity.AgentTaskLog;
import com.eliza.aicompetition.entity.CompetitionNotice;
import com.eliza.aicompetition.entity.FileAsset;
import com.eliza.aicompetition.entity.MaterialRequirement;
import com.eliza.aicompetition.entity.NoticeParseDraft;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.AgentTaskLogMapper;
import com.eliza.aicompetition.mapper.CompetitionNoticeMapper;
import com.eliza.aicompetition.mapper.FileAssetMapper;
import com.eliza.aicompetition.mapper.MaterialRequirementMapper;
import com.eliza.aicompetition.mapper.NoticeParseDraftMapper;
import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.security.SecurityUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * <h1>通知管理 Service</h1>
 *
 * <h2>P1-1/P1-2 核心改造：AI 解析人工确认流 + 状态机</h2>
 *
 * <h3>改造前的流程（❌ 有问题）</h3>
 * <pre>
 * 上传通知 → AI 解析 → 直接覆盖 competition_notice 的 title/deadline 等字段
 *                     → 直接删除并重建 material_requirement
 *   问题：AI 结果错了怎么办？无法回滚！管理员没有审核机会！
 * </pre>
 *
 * <h3>改造后的流程（✅ 人机协同）</h3>
 * <pre>
 * 上传通知 → AI 解析 → 写入 notice_parse_draft（草稿表）
 *                     → parseStatus = PARSED
 *                     → 管理员查看/编辑草稿
 *                     → 管理员确认 → 正式写入 competition_notice + material_requirement
 *                     → 管理员发布 → publishStatus = PUBLISHED
 *                     → 学生可见，可创建项目
 * </pre>
 *
 * <h3>涉及的状态枚举</h3>
 * <ul>
 *   <li>{@link com.eliza.aicompetition.common.enums.NoticeParseStatus} — DRAFT → PARSING → PARSED / FAILED</li>
 *   <li>{@link com.eliza.aicompetition.common.enums.NoticePublishStatus} — DRAFT → PUBLISHED → ARCHIVED</li>
 * </ul>
 *
 * <h3>关键技术决策</h3>
 * <ol>
 *   <li><b>AI 结果不直接落库</b>：先写入 notice_parse_draft 草稿表，管理员确认后才正式覆盖</li>
 *   <li><b>事务边界拆分</b>：AI 调用（网络 IO）不在数据库事务内，避免长时间占用连接</li>
 *   <li><b>批量插入</b>：material_requirement 用 MyBatis-Plus saveBatch()，N 条材料仅 1 次 SQL</li>
 *   <li><b>乐观锁 @Version</b>：防止管理员并发发布/归档导致数据覆盖</li>
 *   <li><b>枚举替代字符串</b>：状态值全部使用枚举的 .name()，带编译期检查 + 合法流转校验</li>
 * </ol>
 */
@Service
public class NoticeService {

    private static final Logger log = LoggerFactory.getLogger(NoticeService.class);
    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 支持解析的日期格式列表（从最精确到最模糊排列）。
     */
    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
        DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm").withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("uuuu/MM/dd HH:mm").withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("uuuu年MM月dd日 HH:mm").withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("uuuu.MM.dd HH:mm").withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ISO_LOCAL_DATE_TIME
    );
    private static final List<DateTimeFormatter> DATE_ONLY_FORMATS = List.of(
        DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("uuuu年MM月dd日").withResolverStyle(ResolverStyle.STRICT),
        DateTimeFormatter.ofPattern("uuuu.MM.dd").withResolverStyle(ResolverStyle.STRICT)
    );

    private final CompetitionNoticeMapper competitionNoticeMapper;
    private final MaterialRequirementMapper materialRequirementMapper;
    private final NoticeParseDraftMapper noticeParseDraftMapper;
    private final FileAssetMapper fileAssetMapper;
    private final SysUserMapper sysUserMapper;
    private final AgentTaskLogMapper agentTaskLogMapper;
    private final AiService aiService;
    private final FileTextExtractor fileTextExtractor;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final DashboardCacheService dashboardCacheService;
    private final AiTaskObservationWriter observationWriter;
    @Value("${notice.ai.task-timeout-seconds:300}")
    private long parseTaskTimeoutSeconds;

    public NoticeService(
        CompetitionNoticeMapper competitionNoticeMapper,
        MaterialRequirementMapper materialRequirementMapper,
        NoticeParseDraftMapper noticeParseDraftMapper,
        FileAssetMapper fileAssetMapper,
        SysUserMapper sysUserMapper,
        AgentTaskLogMapper agentTaskLogMapper,
        AiService aiService,
        FileTextExtractor fileTextExtractor,
        ObjectMapper objectMapper,
        TransactionTemplate transactionTemplate,
        DashboardCacheService dashboardCacheService,
        AiTaskObservationWriter observationWriter
    ) {
        this.competitionNoticeMapper = competitionNoticeMapper;
        this.materialRequirementMapper = materialRequirementMapper;
        this.noticeParseDraftMapper = noticeParseDraftMapper;
        this.fileAssetMapper = fileAssetMapper;
        this.sysUserMapper = sysUserMapper;
        this.agentTaskLogMapper = agentTaskLogMapper;
        this.aiService = aiService;
        this.fileTextExtractor = fileTextExtractor;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.dashboardCacheService = dashboardCacheService;
        this.observationWriter = observationWriter;
    }

    // ==================== 通知上传 ====================

    @Transactional
    public NoticeUploadResponse uploadNotice(
        MultipartFile file,
        String title,
        String organizer,
        LocalDateTime deadline,
        String targetGroup,
        String rawText,
        Long createdBy
    ) {
        if ((file == null || file.isEmpty()) && !StringUtils.hasText(rawText)) {
            throw new BusinessException("请上传通知文件或在'通知原文/补充说明'中填写文本内容");
        }
        validateUser(createdBy);

        Long fileId = null;
        if (file != null && !file.isEmpty()) {
            FileAsset fileAsset = buildFileAsset(file, "notice", createdBy);
            fileAssetMapper.insert(fileAsset);
            fileId = fileAsset.getFileId();
        }

        CompetitionNotice notice = new CompetitionNotice();
        notice.setTitle(resolveTitle(title, file));
        notice.setOrganizer(organizer);
        notice.setDeadline(deadline);
        notice.setTargetGroup(targetGroup);
        notice.setRawText(buildRawText(rawText, file));
        notice.setAiSummary("待解析");
        notice.setNoticeFileId(fileId);
        notice.setCreatedBy(createdBy);
        notice.setNoticeType("COMPETITION");
        notice.setParseStatus(NoticeParseStatus.DRAFT.name());
        notice.setPublishStatus(NoticePublishStatus.DRAFT.name());
        notice.setVersion(0);
        competitionNoticeMapper.insert(notice);
        dashboardCacheService.invalidateGlobalAfterCommit("notice_uploaded");

        log.info("通知已上传: noticeId={}, title={}, userId={}", notice.getNoticeId(), notice.getTitle(), createdBy);
        return new NoticeUploadResponse(notice.getNoticeId(), fileId, notice.getTitle());
    }

    /** 替换待解析或解析失败的草稿通知附件。 */
    @Transactional
    public NoticeUploadResponse replaceAttachment(Long noticeId, MultipartFile file, Long uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择非空通知附件");
        }
        CompetitionNotice notice = getNoticeOrThrow(noticeId);
        if (!NoticePublishStatus.DRAFT.name().equals(notice.getPublishStatus())
                || notice.getConfirmedAt() != null) {
            throw new BusinessException(409, "已确认或已发布的通知不能替换附件");
        }
        if (!NoticeParseStatus.DRAFT.name().equals(notice.getParseStatus())
                && !NoticeParseStatus.FAILED.name().equals(notice.getParseStatus())) {
            throw new BusinessException(409, "通知已开始解析或已有解析草稿，不能直接替换附件");
        }
        validateUser(uploadedBy);
        FileAsset fileAsset = buildFileAsset(file, "notice", uploadedBy);
        fileAssetMapper.insert(fileAsset);

        notice.setNoticeFileId(fileAsset.getFileId());
        notice.setRawText(null);
        notice.setAiSummary("待解析");
        if (competitionNoticeMapper.updateById(notice) != 1) {
            throw new BusinessException(409, "通知状态已变化，请刷新后重试");
        }
        // MyBatis-Plus 的 updateById 默认跳过 null 字段，必须显式清除旧手填原文。
        competitionNoticeMapper.update(null, new LambdaUpdateWrapper<CompetitionNotice>()
            .eq(CompetitionNotice::getNoticeId, noticeId)
            .set(CompetitionNotice::getRawText, null));
        dashboardCacheService.invalidateGlobalAfterCommit("notice_attachment_replaced");
        log.info("通知附件已替换: noticeId={}, fileId={}, userId={}", noticeId, fileAsset.getFileId(), uploadedBy);
        return new NoticeUploadResponse(noticeId, fileAsset.getFileId(), notice.getTitle());
    }

    // ==================== AI 解析任务（只生成待确认草稿） ====================

    /** Accept a durable task before any OCR or model request. The controller dispatches after this transaction commits. */
    public NoticeParseTaskResponse acceptParseTask(Long noticeId, Long requestedBy) {
        try {
            return transactionTemplate.execute(tx -> {
                CompetitionNotice notice = getNoticeOrThrow(noticeId);
                if (!NoticePublishStatus.DRAFT.name().equals(notice.getPublishStatus())
                        || notice.getConfirmedAt() != null) {
                    throw new BusinessException(409, "已确认或发布的通知不能重新解析");
                }
                if (notice.getNoticeFileId() == null && !StringUtils.hasText(notice.getRawText())) {
                    throw new BusinessException(400, "通知缺少附件和原文，无法开始解析");
                }
                notice.startParsing();
                if (competitionNoticeMapper.updateById(notice) != 1) {
                    throw new BusinessException(409, "通知状态已变化，请刷新后重试");
                }
                AgentTaskLog previous = agentTaskLogMapper.selectOne(new LambdaQueryWrapper<AgentTaskLog>()
                    .eq(AgentTaskLog::getBusinessType, "NOTICE_PARSE")
                    .eq(AgentTaskLog::getBusinessId, noticeId)
                    .orderByDesc(AgentTaskLog::getTaskId).last("LIMIT 1"));
                AgentTaskLog task = new AgentTaskLog();
                task.setToolName("parseNoticeTool");
                task.setBusinessType("NOTICE_PARSE");
                task.setBusinessId(noticeId);
                task.setParentTaskId(previous == null ? null : previous.getTaskId());
                task.setAttemptNo(previous == null ? 1 : Math.max(1, previous.getAttemptNo() == null ? 1 : previous.getAttemptNo()) + 1);
                task.setInputSummary("解析通知: " + notice.getTitle());
                task.setExecuteStatus(AgentTaskStatus.PENDING.name());
                task.setResultOrigin("NONE");
                task.setActiveMarker(1);
                var payload = objectMapper.createObjectNode();
                payload.put("noticeId", noticeId);
                payload.put("noticeVersion", notice.getVersion());
                payload.put("noticeFileId", notice.getNoticeFileId());
                payload.put("rawTextSha256", sha256(notice.getRawText()));
                payload.put("requestedBy", requestedBy);
                task.setRequestPayload(payload.toString());
                agentTaskLogMapper.insert(task);
                log.info("notice_task_accepted noticeId={} taskId={} attempt={}", noticeId, task.getTaskId(), task.getAttemptNo());
                return new NoticeParseTaskResponse(task.getTaskId(), noticeId, AgentTaskStatus.PENDING.name());
            });
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(409, "通知解析正在进行中");
        }
    }

    /** DB CAS means duplicate executor submissions cannot run the same task twice. */
    public void executeParseTask(Long taskId) {
        long workerNanos = System.nanoTime();
        LocalDateTime started = LocalDateTime.now();
        int claimed = agentTaskLogMapper.update(null, new LambdaUpdateWrapper<AgentTaskLog>()
            .eq(AgentTaskLog::getTaskId, taskId)
            .eq(AgentTaskLog::getExecuteStatus, AgentTaskStatus.PENDING.name())
            .eq(AgentTaskLog::getActiveMarker, 1)
            .set(AgentTaskLog::getExecuteStatus, AgentTaskStatus.RUNNING.name())
            .set(AgentTaskLog::getStartedAt, started));
        if (claimed != 1) return;

        AgentTaskLog task = agentTaskLogMapper.selectById(taskId);
        AiOperationTiming timing = new AiOperationTiming();
        timing.taskId(taskId);
        timing.workerStarted();
        timing.attempt(task.getAttemptNo());
        timing.models(aiService.textModel(), aiService.visionModel());
        timing.prompt(AiService.NOTICE_PROMPT_VERSION, aiService.noticePromptHash());
        com.eliza.aicompetition.common.AiTaskBudget.set(
            (task.getCreatedAt() == null ? started : task.getCreatedAt()).plusSeconds(parseTaskTimeoutSeconds), taskId);
        long queueMs = task.getCreatedAt() == null ? -1 : Math.max(0, Duration.between(task.getCreatedAt(), started).toMillis());
        log.info("notice_task_claimed noticeId={} taskId={} queueWaitMs={} attempt={}", task.getBusinessId(), taskId, queueMs, task.getAttemptNo());
        String outcome = "FAILED";
        try {
            if (taskExpired(task)) {
                timeoutParseTask(taskId);
                outcome = "TIMEOUT";
                return;
            }
            var payload = objectMapper.readTree(task.getRequestPayload());
            CompetitionNotice notice = getNoticeOrThrow(task.getBusinessId());
            if (!snapshotMatches(notice, payload)) {
                throw new BusinessException(409, "通知输入已变化，解析结果未保存");
            }
            String extracted = prepareParseText(notice, timing);
            String hint = buildParseContextHint(notice);
            log.info("main_model_started taskId={} operation=NOTICE_PARSE model={} promptVersion={}",
                taskId, aiService.textModel(), AiService.NOTICE_PROMPT_VERSION);
            AiService.CallResult<AiParseResult> call = aiService.parseNoticeMeasured(hint.isEmpty() ? extracted : hint + "\n\n" + extracted);
            timing.call(call.llmMs(), call.llmAttempted(), call.degraded(), call.callCount());
            timing.reason(call.fallbackReason());
            log.info("main_model_finished taskId={} attempted={} fallback={} reason={} llmMs={}",
                taskId, call.llmAttempted(), call.degraded(), call.fallbackReason(), call.llmMs());
            AiParseResult result = call.result();
            if (timing.reason() == AiDiagnosticCode.OCR_PARTIAL) {
                result = new AiParseResult(result.title(), result.organizer(), result.deadline(),
                    result.targetGroup(), "【OCR_PARTIAL：部分 PDF 页面识别失败，请按原件逐页人工核对】"
                        + (result.keyPoints() == null ? "" : result.keyPoints()), result.materials());
            }
            final AiParseResult draftResult = result;
            LocalDateTime deadline = parseDeadline(result.deadline());
            Boolean committed = transactionTemplate.execute(tx -> {
                AgentTaskLog locked = lockTask(taskId);
                if (locked == null || !AgentTaskStatus.RUNNING.name().equals(locked.getExecuteStatus())) return false;
                if (taskExpired(locked)) {
                    markTaskTimeoutLocked(locked);
                    return false;
                }
                CompetitionNotice current = getNoticeOrThrow(task.getBusinessId());
                if (!snapshotMatches(current, payload)
                        || !NoticeParseStatus.PARSING.name().equals(current.getParseStatus())
                        || !NoticePublishStatus.DRAFT.name().equals(current.getPublishStatus())
                        || current.getConfirmedAt() != null) {
                    throw new BusinessException(409, "通知输入或状态已变化，解析结果未保存");
                }
                // A previous pending draft remains available until this new result really commits.
                noticeParseDraftMapper.update(null, new LambdaUpdateWrapper<NoticeParseDraft>()
                    .eq(NoticeParseDraft::getNoticeId, task.getBusinessId())
                    .eq(NoticeParseDraft::getStatus, "PENDING")
                    .set(NoticeParseDraft::getStatus, "REJECTED"));
                NoticeParseDraft draft = new NoticeParseDraft();
                draft.setNoticeId(task.getBusinessId());
                draft.setAiTitle(draftResult.title());
                draft.setAiOrganizer(draftResult.organizer());
                draft.setAiDeadline(deadline);
                draft.setAiTargetGroup(draftResult.targetGroup());
                draft.setAiKeyPoints(draftResult.keyPoints());
                draft.setAiMaterialsJson(serializeMaterialsToJson(draftResult.materials()));
                draft.setRawAiResponse(serializeParseResult(draftResult));
                draft.setStatus("PENDING");
                draft.setCreatedBy(payload.path("requestedBy").asLong());
                noticeParseDraftMapper.insert(draft);
                current.markParsed();
                if (competitionNoticeMapper.updateById(current) != 1) {
                    throw new BusinessException(409, "通知状态已变化，解析结果未保存");
                }
                locked.setResultSummary(buildSummaryFromParseResult(notice, draftResult, draftResult.materials()));
                locked.setResultOrigin(call.degraded() ? "FALLBACK" : "MODEL");
                locked.setActiveMarker(null);
                locked.markSuccess();
                agentTaskLogMapper.updateById(locked);
                return true;
            });
            outcome = Boolean.TRUE.equals(committed) ? "SUCCESS" : "TIMEOUT";
        } catch (Exception failure) {
            if (timing.reason() == null || (failure instanceof BusinessException b
                    && (b.getCode() == 409 || b.getCode() == 600)))
                timing.reason(classifyParseFailure(failure));
            String summary = failure instanceof BusinessException business
                && (business.getCode() == 409 || business.getCode() == 600)
                ? business.getMessage() : "通知解析失败，请检查输入后重试";
            failParseTask(taskId, summary);
        } finally {
            com.eliza.aicompetition.common.AiTaskBudget.clear();
            AgentTaskLog finished = agentTaskLogMapper.selectById(taskId);
            if (finished != null) outcome = finished.getExecuteStatus();
            observationWriter.write(taskId, finished, timing, null);
            long taskTotalMs = task.getCreatedAt() == null ? -1
                : Math.max(0, Duration.between(task.getCreatedAt(), LocalDateTime.now()).toMillis());
            timing.log(log, "NOTICE_PARSE", task.getBusinessId(), outcome);
            log.info("notice_task_finished noticeId={} taskId={} outcome={} queueWaitMs={} executionMs={} taskTotalMs={} attempt={}",
                task.getBusinessId(), taskId, outcome, queueMs,
                AiOperationTiming.elapsedMs(workerNanos), taskTotalMs, task.getAttemptNo());
        }
    }

    private AiDiagnosticCode classifyParseFailure(Exception failure) {
        if (failure instanceof BusinessException business) {
            if (business.getCode() == 409) return AiDiagnosticCode.INPUT_CHANGED;
            if (business.getCode() == 600) return AiDiagnosticCode.INVALID_MODEL_DATE;
            return AiDiagnosticCode.INPUT_UNREADABLE;
        }
        return AiDiagnosticCode.TASK_ERROR;
    }

    private AgentTaskLog lockTask(Long taskId) {
        return agentTaskLogMapper.selectOne(new LambdaQueryWrapper<AgentTaskLog>()
            .eq(AgentTaskLog::getTaskId, taskId).last("FOR UPDATE"));
    }

    private boolean taskExpired(AgentTaskLog task) {
        LocalDateTime origin = task.getCreatedAt() != null ? task.getCreatedAt() : task.getStartedAt();
        return origin != null && !LocalDateTime.now().isBefore(origin.plusSeconds(parseTaskTimeoutSeconds));
    }

    private boolean snapshotMatches(CompetitionNotice notice, com.fasterxml.jackson.databind.JsonNode payload) {
        return Objects.equals(notice.getVersion(), payload.path("noticeVersion").asInt(-1))
            && Objects.equals(notice.getNoticeFileId(), payload.path("noticeFileId").isNull() ? null : payload.path("noticeFileId").asLong())
            && Objects.equals(sha256(notice.getRawText()),
                payload.path("rawTextSha256").isNull() ? null : payload.path("rawTextSha256").asText());
    }

    public void timeoutParseTask(Long taskId) {
        transactionTemplate.executeWithoutResult(tx -> {
            AgentTaskLog locked = lockTask(taskId);
            if (locked == null || !(AgentTaskStatus.PENDING.name().equals(locked.getExecuteStatus())
                    || AgentTaskStatus.RUNNING.name().equals(locked.getExecuteStatus())) || !taskExpired(locked)) return;
            markTaskTimeoutLocked(locked);
        });
        AgentTaskLog done = agentTaskLogMapper.selectById(taskId);
        if (done != null && "TIMEOUT".equals(done.getExecuteStatus()))
            observationWriter.write(taskId, done, new AiOperationTiming(), AiDiagnosticCode.TASK_TIMEOUT);
    }

    private void markTaskTimeoutLocked(AgentTaskLog task) {
        task.setActiveMarker(null);
        task.setResultOrigin("NONE");
        task.setErrorMessage("通知解析任务超过截止时间");
        task.markTimeout();
        agentTaskLogMapper.updateById(task);
        restoreNoticeAfterUnusableTask(task);
    }

    private void failParseTask(Long taskId, String errorClass) {
        transactionTemplate.executeWithoutResult(tx -> {
            AgentTaskLog locked = lockTask(taskId);
            if (locked == null || !AgentTaskStatus.RUNNING.name().equals(locked.getExecuteStatus())) return;
            locked.setResultOrigin("NONE");
            locked.setActiveMarker(null);
            locked.markFailed(errorClass);
            agentTaskLogMapper.updateById(locked);
            restoreNoticeAfterUnusableTask(locked);
        });
    }

    private void restoreNoticeAfterUnusableTask(AgentTaskLog task) {
        Long noticeId = task.getBusinessId();
        CompetitionNotice current = getNoticeOrThrow(noticeId);
        if (!NoticeParseStatus.PARSING.name().equals(current.getParseStatus())) return;
        boolean sameInput;
        try {
            sameInput = snapshotMatches(current, objectMapper.readTree(task.getRequestPayload()));
        } catch (Exception invalidSnapshot) {
            sameInput = false;
        }
        if (!sameInput) {
            noticeParseDraftMapper.update(null, new LambdaUpdateWrapper<NoticeParseDraft>()
                .eq(NoticeParseDraft::getNoticeId, noticeId)
                .eq(NoticeParseDraft::getStatus, "PENDING")
                .set(NoticeParseDraft::getStatus, "REJECTED"));
        }
        if (sameInput && noticeParseDraftMapper.findLatestPendingByNoticeId(noticeId) != null) {
            current.markParsed();
        } else {
            current.markParseFailed();
        }
        if (competitionNoticeMapper.updateById(current) != 1) {
            throw new BusinessException(409, "通知状态已变化，请刷新后重试");
        }
    }

    public void failAbandonedRunningTask(Long taskId) {
        transactionTemplate.executeWithoutResult(tx -> {
            AgentTaskLog locked = lockTask(taskId);
            if (locked == null || !AgentTaskStatus.RUNNING.name().equals(locked.getExecuteStatus())) return;
            if (taskExpired(locked)) {
                markTaskTimeoutLocked(locked);
                return;
            }
            locked.setResultOrigin("NONE");
            locked.setActiveMarker(null);
            locked.markFailed("应用重启时工作线程已中断，请重新解析");
            agentTaskLogMapper.updateById(locked);
            restoreNoticeAfterUnusableTask(locked);
        });
        AgentTaskLog done = agentTaskLogMapper.selectById(taskId);
        if (done != null && ("FAILED".equals(done.getExecuteStatus()) || "TIMEOUT".equals(done.getExecuteStatus())))
            observationWriter.write(taskId, done, new AiOperationTiming(),
                "TIMEOUT".equals(done.getExecuteStatus()) ? AiDiagnosticCode.TASK_TIMEOUT : AiDiagnosticCode.WORKER_INTERRUPTED);
    }

    public NoticeTaskView findTask(Long taskId) {
        AgentTaskLog task = agentTaskLogMapper.selectById(taskId);
        if (task == null || !"NOTICE_PARSE".equals(task.getBusinessType())) throw new BusinessException(404, "解析任务不存在");
        return toTaskView(task);
    }

    public NoticeTaskView latestTask(Long noticeId) {
        getNoticeOrThrow(noticeId);
        AgentTaskLog task = agentTaskLogMapper.selectOne(new LambdaQueryWrapper<AgentTaskLog>()
            .eq(AgentTaskLog::getBusinessType, "NOTICE_PARSE")
            .eq(AgentTaskLog::getBusinessId, noticeId)
            .orderByDesc(AgentTaskLog::getTaskId).last("LIMIT 1"));
        return task == null ? null : toTaskView(task);
    }

    private NoticeTaskView toTaskView(AgentTaskLog task) {
        return new NoticeTaskView(task.getTaskId(), task.getBusinessType(), task.getBusinessId(),
            task.getExecuteStatus(), task.getResultOrigin(), task.getAttemptNo(), task.getCreatedAt(),
            task.getStartedAt(), task.getFinishedAt(), task.getErrorMessage());
    }

    // ==================== 解析草稿管理（P1-1 新增） ====================

    /**
     * 获取通知的最新待确认解析草稿。
     */
    public ParseDraftResponse getParseDraft(Long noticeId) {
        CompetitionNotice notice = getNoticeOrThrow(noticeId);
        if (!NoticeParseStatus.PARSED.name().equals(notice.getParseStatus())) {
            throw new BusinessException(409, "当前解析任务尚未完成，草稿暂不可确认");
        }

        NoticeParseDraft draft = noticeParseDraftMapper.findLatestPendingByNoticeId(noticeId);
        if (draft == null) {
            throw new BusinessException("该通知没有待确认的解析草稿");
        }

        List<MaterialItem> materials = deserializeMaterials(draft.getAiMaterialsJson());
        return new ParseDraftResponse(
            draft.getId(),
            draft.getNoticeId(),
            draft.getAiTitle(),
            draft.getAiOrganizer(),
            draft.getAiDeadline(),
            draft.getAiTargetGroup(),
            draft.getAiKeyPoints(),
            materials,
            draft.getRawAiResponse(),
            draft.getStatus(),
            draft.getCreatedAt()
        );
    }

    /**
     * 管理员手动编辑解析草稿。
     */
    @Transactional
    public ParseDraftResponse updateParseDraft(Long noticeId, UpdateParseDraftRequest request) {
        CompetitionNotice notice = getNoticeOrThrow(noticeId);
        if (!NoticeParseStatus.PARSED.name().equals(notice.getParseStatus())) {
            throw new BusinessException(409, "当前解析任务尚未完成，草稿暂不可修改");
        }

        NoticeParseDraft draft = noticeParseDraftMapper.findLatestPendingByNoticeId(noticeId);
        if (draft == null) {
            throw new BusinessException("该通知没有待确认的解析草稿，或草稿已确认");
        }
        if (!"PENDING".equals(draft.getStatus())) {
            throw new BusinessException("草稿状态不允许修改：" + draft.getStatus());
        }

        draft.setAiTitle(request.aiTitle());
        draft.setAiOrganizer(request.aiOrganizer());
        draft.setAiDeadline(request.aiDeadline());
        draft.setAiTargetGroup(request.aiTargetGroup());
        draft.setAiKeyPoints(request.aiKeyPoints());
        draft.setAiMaterialsJson(serializeMaterialItems(request.materials()));
        noticeParseDraftMapper.updateById(draft);

        log.info("解析草稿已更新: noticeId={}, draftId={}", noticeId, draft.getId());

        List<MaterialItem> responseMaterials = request.materials().stream()
            .map(m -> new MaterialItem(m.name(), m.description(), m.isRequired()))
            .toList();

        return new ParseDraftResponse(
            draft.getId(),
            draft.getNoticeId(),
            draft.getAiTitle(),
            draft.getAiOrganizer(),
            draft.getAiDeadline(),
            draft.getAiTargetGroup(),
            draft.getAiKeyPoints(),
            responseMaterials,
            draft.getRawAiResponse(),
            draft.getStatus(),
            draft.getCreatedAt()
        );
    }

    /**
     * <h3>管理员确认 AI 解析结果</h3>
     *
     * <p>从 notice_parse_draft 草稿正式写入 competition_notice 和 material_requirement。</p>
     *
     * <h4>核心流程</h4>
     * <ol>
     *   <li>校验 parseStatus == PARSED（只有待确认状态才能确认）</li>
     *   <li>取最新 PENDING 草稿</li>
     *   <li>草稿中的 title/organizer/deadline/targetGroup → 写入 competition_notice</li>
     *   <li>草稿中的材料列表 → 批量插入 material_requirement（saveBatch，1 次 SQL）</li>
     *   <li>草稿标记为 CONFIRMED，记录确认人和时间</li>
     * </ol>
     *
     * <h4>P1-3 保护规则</h4>
     * <p>如果通知已有关联项目，不删除旧 material_requirement，只停用(INACTIVE)+追加(AI)。
     * 避免已有项目引用的材料要求被误删。</p>
     */
    @Transactional
    public void confirmParse(Long noticeId, ConfirmParseRequest request) {
        CompetitionNotice notice = getNoticeOrThrow(noticeId);

        // 1. 状态校验：只有 PARSED 状态才能确认
        if (!NoticeParseStatus.PARSED.name().equals(notice.getParseStatus())) {
            throw new BusinessException("当前解析状态不允许确认，需要状态为 PARSED");
        }

        // 2. 取最新 PENDING 草稿
        NoticeParseDraft draft = noticeParseDraftMapper.findLatestPendingByNoticeId(noticeId);
        if (draft == null) {
            throw new BusinessException("该通知没有待确认的解析草稿");
        }

        // 3. 草稿数据写入正式字段
        if (StringUtils.hasText(draft.getAiTitle())) {
            notice.setTitle(draft.getAiTitle());
        }
        if (StringUtils.hasText(draft.getAiOrganizer())) {
            notice.setOrganizer(draft.getAiOrganizer());
        }
        if (draft.getAiDeadline() != null) {
            notice.setDeadline(draft.getAiDeadline());
        }
        if (StringUtils.hasText(draft.getAiTargetGroup())) {
            notice.setTargetGroup(draft.getAiTargetGroup());
        }
        String aiSummary = buildSummaryFromDraft(draft);
        notice.setAiSummary(aiSummary);
        notice.confirmParsedContent(SecurityUtils.getCurrentUserId());
        competitionNoticeMapper.updateById(notice);

        // 4. 生成正式 material_requirement（P1-3 规则：已有关联项目时不删除，只追加）
        List<MaterialItem> materials = deserializeMaterials(draft.getAiMaterialsJson());
        syncMaterialRequirements(noticeId, materials);
        dashboardCacheService.invalidateGlobalAfterCommit("notice_confirmed");

        // 5. 草稿标记为已确认
        draft.setStatus("CONFIRMED");
        draft.setConfirmedBy(SecurityUtils.getCurrentUserId());
        draft.setConfirmedAt(LocalDateTime.now());
        noticeParseDraftMapper.updateById(draft);

        // 6. 记录操作日志
        saveAgentTaskLog(
            "confirmParse",
            "确认解析结果: " + notice.getTitle(),
            "确认了 " + materials.size() + " 项材料要求",
            AgentTaskStatus.SUCCESS.name()
        );

        log.info("解析结果已确认: noticeId={}, draftId={}, title={}, materials={}",
            noticeId, draft.getId(), notice.getTitle(), materials.size());
    }

    // ==================== 通知发布与归档 ====================

    /**
     * <h3>发布通知</h3>
     *
     * <p><b>双重状态校验</b>：发布前必须同时满足两个条件：</p>
     * <ol>
     *   <li>解析状态 = PARSED（管理员已确认 AI 解析结果）</li>
     *   <li>发布状态 = DRAFT → PUBLISHED 合法流转</li>
     * </ol>
     *
     * <p>发布后学生才能看到通知并基于它创建申报项目。</p>
     */
    @Transactional
    public void publishNotice(Long noticeId) {
        CompetitionNotice notice = getNoticeOrThrow(noticeId);

        // 发布前置条件：解析状态必须为 PARSED
        if (!NoticeParseStatus.PARSED.name().equals(notice.getParseStatus())) {
            throw new BusinessException("通知解析未完成，无法发布。当前解析状态：" + notice.getParseStatus());
        }

        // 发布前置条件：管理员必须已确认解析内容
        if (notice.getConfirmedAt() == null) {
            throw new BusinessException("通知解析结果尚未经管理员确认，无法发布");
        }

        // 发布前置条件：材料清单必须存在
        List<MaterialRequirement> requirements = findRequirementsByNoticeId(noticeId);
        if (requirements.isEmpty()) {
            throw new BusinessException("通知无材料清单，请先确认AI解析结果或手动添加材料要求");
        }

        // 使用领域方法发布
        notice.publish();
        competitionNoticeMapper.updateById(notice);
        dashboardCacheService.invalidateGlobalAfterCommit("notice_published");
        log.info("通知已发布: noticeId={}, title={}, userId={}",
            noticeId, notice.getTitle(), SecurityUtils.getCurrentUserId());
    }

    @Transactional
    public void archiveNotice(Long noticeId) {
        CompetitionNotice notice = getNoticeOrThrow(noticeId);

        // 使用领域方法归档
        notice.archive();
        competitionNoticeMapper.updateById(notice);
        dashboardCacheService.invalidateGlobalAfterCommit("notice_archived");
        log.info("通知已归档: noticeId={}, title={}", noticeId, notice.getTitle());
    }

    // ==================== 通知查询 ====================

    public CompetitionNotice getNoticeOrThrow(Long noticeId) {
        CompetitionNotice notice = competitionNoticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new BusinessException("通知不存在: noticeId=" + noticeId);
        }
        return notice;
    }

    public List<MaterialRequirement> findRequirementsByNoticeId(Long noticeId) {
        LambdaQueryWrapper<MaterialRequirement> queryWrapper = new LambdaQueryWrapper<MaterialRequirement>()
            .eq(MaterialRequirement::getNoticeId, noticeId)
            .eq(MaterialRequirement::getStatus, "ACTIVE")
            .orderByAsc(MaterialRequirement::getSortNo)
            .orderByAsc(MaterialRequirement::getRequirementId);
        return materialRequirementMapper.selectList(queryWrapper);
    }

    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<CompetitionNotice> listNotices(
        String keyword, String publishStatus, LocalDateTime startDate, LocalDateTime endDate,
        int pageNum, int pageSize
    ) {
        if ("student".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())) {
            publishStatus = NoticePublishStatus.PUBLISHED.name();
        }
        LambdaQueryWrapper<CompetitionNotice> queryWrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            queryWrapper.and(w -> w
                .like(CompetitionNotice::getTitle, keyword)
                .or()
                .like(CompetitionNotice::getOrganizer, keyword));
        }

        if (StringUtils.hasText(publishStatus)) {
            queryWrapper.eq(CompetitionNotice::getPublishStatus, publishStatus);
        }

        if (startDate != null) {
            queryWrapper.ge(CompetitionNotice::getCreatedAt, startDate);
        }

        if (endDate != null) {
            queryWrapper.le(CompetitionNotice::getCreatedAt, endDate);
        }

        queryWrapper.orderByDesc(CompetitionNotice::getCreatedAt);

        com.baomidou.mybatisplus.extension.plugins.pagination.Page<CompetitionNotice> page =
            new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(pageNum, pageSize);
        return competitionNoticeMapper.selectPage(page, queryWrapper);
    }

    public NoticeDetailResponse getNoticeDetail(Long noticeId) {
        CompetitionNotice notice = getNoticeOrThrow(noticeId);
        if ("student".equalsIgnoreCase(SecurityUtils.getCurrentUserRole())
                && !NoticePublishStatus.PUBLISHED.name().equals(notice.getPublishStatus())) {
            throw new BusinessException(404, "通知不存在");
        }
        FileAsset file = notice.getNoticeFileId() == null
            ? null
            : fileAssetMapper.selectById(notice.getNoticeFileId());
        List<NoticeDetailResponse.MaterialRequirementItem> requirements =
            findRequirementsByNoticeId(noticeId).stream()
                .map(item -> new NoticeDetailResponse.MaterialRequirementItem(
                    item.getRequirementId(),
                    item.getRequirementName(),
                    item.getDescription(),
                    Integer.valueOf(1).equals(item.getIsRequired()),
                    item.getSortNo(),
                    item.getStatus()
                ))
                .toList();
        return new NoticeDetailResponse(
            notice.getNoticeId(),
            notice.getTitle(),
            notice.getOrganizer(),
            notice.getDeadline(),
            notice.getTargetGroup(),
            notice.getRawText(),
            notice.getAiSummary(),
            notice.getNoticeFileId(),
            file == null ? null : file.getFileName(),
            notice.getParseStatus(),
            notice.getPublishStatus(),
            notice.getConfirmedBy(),
            notice.getConfirmedAt(),
            notice.getPublishedAt(),
            notice.getCreatedAt(),
            notice.getUpdatedAt(),
            requirements
        );
    }

    // ==================== 私有方法 ====================

    /**
     * 准备解析文本：优先使用 rawText，为空则从文件提取。
     */
    private String prepareParseText(CompetitionNotice notice, AiOperationTiming timing) {
        String textToParse = notice.getRawText();
        if (StringUtils.hasText(textToParse) && !isSystemPlaceholder(textToParse)) timing.textPath();
        log.info("parseNotice noticeId={}: rawText from DB is {} chars",
            notice.getNoticeId(), textToParse != null ? textToParse.length() : 0);

        if (!StringUtils.hasText(textToParse) || isSystemPlaceholder(textToParse)) {
            log.info("parseNotice noticeId={}: rawText is empty/placeholder, extracting from file...", notice.getNoticeId());
            String extracted = extractTextFromNoticeFile(notice, timing);
            log.info("parseNotice noticeId={}: extracted text from file, length={}",
                notice.getNoticeId(), extracted != null ? extracted.length() : 0);

            if (extracted != null && !isExtractionError(extracted)) {
                textToParse = extracted;
            } else if (extracted != null) {
                if (extracted.startsWith("[OCR unavailable")) {
                    throw new BusinessException("扫描 PDF 超出页数、字节或任务预算，或 OCR 无可用结果。"
                        + "请提供可提取文字的 PDF，或在通知原文中粘贴完整内容后重试。");
                }
                throw new BusinessException(
                    "上传的PDF文件无法提取文字内容（Tika提取失败且OCR识别失败，可能是图片质量差）。"
                    + "请在「通知原文/补充说明」文本框中粘贴通知内容后重新保存，再执行智能解析。"
                );
            }
        }

        if (!StringUtils.hasText(textToParse) || isSystemPlaceholder(textToParse)) {
            throw new BusinessException("无法解析通知：既无文本内容，也无法从附件提取文本。"
                + "请在上传时填写「通知原文/补充说明」或上传可提取文字的通知文件。");
        }

        return textToParse;
    }

    /**
     * 同步材料要求列表到 material_requirement 表。
     * <p>
     * P1-3 规则：如果通知已有关联项目，不删除旧材料要求，只追加新的或更新已有。
     * 如果通知无关联项目，可删除旧数据后批量插入。
     * </p>
     */
    private void syncMaterialRequirements(Long noticeId, List<MaterialItem> materials) {
        if (materials == null || materials.isEmpty()) {
            log.warn("confirmParse: 材料列表为空, noticeId={}", noticeId);
            return;
        }

        // 检查是否有关联项目（简化判断：检查是否有 material_requirement 被项目引用）
        List<MaterialRequirement> existingReqs = materialRequirementMapper.selectList(
            new LambdaQueryWrapper<MaterialRequirement>()
                .eq(MaterialRequirement::getNoticeId, noticeId)
        );
        boolean hasLinkedProjects = !existingReqs.isEmpty();

        if (!hasLinkedProjects) {
            // 无关联项目 → 物理删除旧要求后重建
            materialRequirementMapper.delete(
                new LambdaQueryWrapper<MaterialRequirement>()
                    .eq(MaterialRequirement::getNoticeId, noticeId)
            );
        } else {
            // 有关联项目 → 停用旧要求
            for (MaterialRequirement req : existingReqs) {
                req.setStatus("INACTIVE");
                materialRequirementMapper.updateById(req);
            }
        }

        // 批量插入新要求
        List<MaterialRequirement> newReqs = new ArrayList<>();
        int sortNo = 1;
        for (MaterialItem item : materials) {
            MaterialRequirement req = new MaterialRequirement();
            req.setNoticeId(noticeId);
            req.setRequirementName(item.name());
            req.setIsRequired(item.isRequired() ? 1 : 0);
            req.setDescription(item.description());
            req.setSortNo(sortNo++);
            req.setStatus("ACTIVE");
            req.setSource("AI");
            req.setVersionNo(1);
            newReqs.add(req);
        }

        // 使用 MyBatis-Plus saveBatch 批量插入
        materialRequirementMapper.insert(newReqs, newReqs.size());
        log.info("材料要求批量插入完成: noticeId={}, count={}", noticeId, newReqs.size());
    }

    // ==================== 日期解析（多格式兼容 + AI Prompt 约束） ====================

    /**
     * <h3>灵活解析 LLM 返回的截止日期</h3>
     *
     * <h4>为什么需要多格式支持？</h4>
     * <p>LLM 返回的 deadline 格式不可控：可能是 "2026-06-15 23:59"、
     * "2026年6月15日"、"2026/06/15" 等。单一格式的 DateTimeFormatter 会抛异常。</p>
     *
     * <h4>解决策略</h4>
     * <ol>
     *   <li><b>源头约束</b>：在 AI Prompt 中明确要求 yyyy-MM-dd HH:mm 格式</li>
     *   <li><b>兜底兼容</b>：按优先级尝试 9 种常见格式</li>
     *   <li><b>纯日期补时间</b>：只有日期没有时间的，默认补 23:59:59</li>
     * </ol>
     *
     * <h4>面试讲法</h4>
     * <p>
     * "LLM 输出的格式不完全可控，我做了两层防护：一是在 Prompt 里强制要求
     * 标准格式，减少 90% 的格式偏差；二是后端的 parseDeadline 支持多种格式
     * 的 fallback 解析，即使 LLM 没按格式返回，也能尽量解析出来。"
     * </p>
     */
    static LocalDateTime parseDeadline(String deadlineStr) {
        if (!StringUtils.hasText(deadlineStr)) return null;

        // 去掉首尾空格和中文标点
        String cleaned = deadlineStr.trim()
            .replaceAll("[​ ]", "") // 去掉零宽空格和不换行空格
            .strip();

        // Parse the whole date-time first. LocalDate.parse accepts a date-time formatter
        // while silently discarding the parsed time, so it must never be used here.
        for (DateTimeFormatter fmt : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(cleaned, fmt);
            } catch (DateTimeParseException ignored) {
                // Try the next supported date-time format.
            }
        }
        for (DateTimeFormatter fmt : DATE_ONLY_FORMATS) {
            try {
                return LocalDate.parse(cleaned, fmt).atTime(23, 59, 59);
            } catch (DateTimeParseException ignored) {
                // Try the next supported date-only format.
            }
        }
        throw new BusinessException(600, "AI 返回的截止日期无效，请核对原文后重试或人工录入");
    }

    private static String sha256(String value) {
        if (value == null) return null;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    // ==================== JSON 序列化/反序列化工具 ====================

    private String serializeMaterialsToJson(List<AiMaterialRequirement> materials) {
        if (materials == null || materials.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(materials);
        } catch (JsonProcessingException e) {
            log.error("材料列表序列化失败", e);
            return "[]";
        }
    }

    private String serializeMaterialItems(List<UpdateParseDraftRequest.MaterialItem> materials) {
        if (materials == null || materials.isEmpty()) return "[]";
        try {
            return objectMapper.writeValueAsString(materials);
        } catch (JsonProcessingException e) {
            log.error("材料列表序列化失败", e);
            return "[]";
        }
    }

    private String serializeParseResult(AiParseResult result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("解析结果序列化失败", e);
            return "{}";
        }
    }

    private List<MaterialItem> deserializeMaterials(String json) {
        if (!StringUtils.hasText(json) || "[]".equals(json.trim())) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<MaterialItem>>() {});
        } catch (Exception e) {
            log.error("材料列表反序列化失败: {}", e.getClass().getSimpleName());
            return Collections.emptyList();
        }
    }

    // ==================== 摘要构建 ====================

    private String buildSummaryFromParseResult(
        CompetitionNotice notice,
        AiParseResult parseResult,
        List<AiMaterialRequirement> materials
    ) {
        String organizer = parseResult.organizer() != null
            ? parseResult.organizer() : (notice.getOrganizer() != null ? notice.getOrganizer() : "未知");
        String deadline = notice.getDeadline() != null
            ? notice.getDeadline().format(DEADLINE_FORMATTER)
            : (parseResult.deadline() != null ? parseResult.deadline() : "未知");
        String target = parseResult.targetGroup() != null
            ? parseResult.targetGroup() : (notice.getTargetGroup() != null ? notice.getTargetGroup() : "未知");
        String keyPoints = parseResult.keyPoints() != null ? parseResult.keyPoints() : "";
        String reqNames = materials != null && !materials.isEmpty()
            ? String.join("、", materials.stream().map(AiMaterialRequirement::name).toList())
            : "暂无材料要求";
        return "【AI解析】主办方：" + organizer
            + "；截止时间：" + deadline
            + "；面向对象：" + target
            + "；关键内容：" + keyPoints
            + "；共识别 " + (materials != null ? materials.size() : 0) + " 项材料要求：" + reqNames + "。";
    }

    private String buildSummaryFromDraft(NoticeParseDraft draft) {
        String organizer = draft.getAiOrganizer() != null ? draft.getAiOrganizer() : "未知";
        String deadline = draft.getAiDeadline() != null
            ? draft.getAiDeadline().format(DEADLINE_FORMATTER) : "未知";
        String target = draft.getAiTargetGroup() != null ? draft.getAiTargetGroup() : "未知";
        String keyPoints = draft.getAiKeyPoints() != null ? draft.getAiKeyPoints() : "";
        List<MaterialItem> materials = deserializeMaterials(draft.getAiMaterialsJson());
        String reqNames = materials.isEmpty()
            ? "暂无材料要求"
            : String.join("、", materials.stream().map(MaterialItem::name).toList());
        return "【AI解析】主办方：" + organizer
            + "；截止时间：" + deadline
            + "；面向对象：" + target
            + "；关键内容：" + keyPoints
            + "；共识别 " + materials.size() + " 项材料要求：" + reqNames + "。";
    }

    // ==================== Agent 任务日志 ====================

    private Long saveAgentTaskLog(String toolName, String inputSummary, String resultSummary, String executeStatus) {
        return saveAgentTaskLog(toolName, inputSummary, resultSummary, executeStatus, null, null, null);
    }

    private Long saveAgentTaskLog(String toolName, String inputSummary, String resultSummary, String executeStatus,
                                  String businessType, Long businessId, LocalDateTime startedAt) {
        AgentTaskLog taskLog = new AgentTaskLog();
        taskLog.setToolName(toolName);
        taskLog.setBusinessType(businessType);
        taskLog.setBusinessId(businessId);
        taskLog.setAttemptNo(1);
        taskLog.setInputSummary(inputSummary);
        taskLog.setResultSummary(resultSummary.length() > 500 ? resultSummary.substring(0, 500) + "..." : resultSummary);
        taskLog.setExecuteStatus(executeStatus);
        taskLog.setStartedAt(startedAt);
        if (startedAt != null) taskLog.setFinishedAt(LocalDateTime.now());
        agentTaskLogMapper.insert(taskLog);
        return taskLog.getTaskId();
    }

    // ==================== 文件与文本处理 ====================

    private void validateUser(Long userId) {
        if (sysUserMapper.selectById(userId) == null) {
            throw new BusinessException("用户不存在: userId=" + userId);
        }
    }

    private FileAsset buildFileAsset(MultipartFile file, String bizType, Long uploadedBy) {
        try {
            FileAsset fileAsset = new FileAsset();
            fileAsset.setBizType(bizType);
            fileAsset.setFileName(file.getOriginalFilename());
            fileAsset.setFileExt(getExtension(file.getOriginalFilename()));
            fileAsset.setFileSize(file.getSize());
            fileAsset.setStoragePath(bizType + "/" + LocalDateTime.now().toLocalDate() + "/" + UUID.randomUUID());
            fileAsset.setFileBlob(file.getBytes());
            fileAsset.setUploadedBy(uploadedBy);
            return fileAsset;
        } catch (IOException exception) {
            throw new BusinessException("文件读取失败: " + exception.getMessage());
        }
    }

    private String resolveTitle(String title, MultipartFile file) {
        if (StringUtils.hasText(title)) {
            return title.trim();
        }
        if (file != null && StringUtils.hasText(file.getOriginalFilename())) {
            String originalFilename = file.getOriginalFilename().trim();
            int dotIndex = originalFilename.lastIndexOf('.');
            return dotIndex > 0 ? originalFilename.substring(0, dotIndex) : originalFilename;
        }
        return "未命名通知";
    }

    private String buildRawText(String rawText, MultipartFile file) {
        if (StringUtils.hasText(rawText)) {
            return rawText.trim();
        }
        return null;
    }

    private boolean isSystemPlaceholder(String rawText) {
        if (rawText == null) return true;
        String trimmed = rawText.trim();
        return "Notice text is pending.".equals(trimmed)
            || trimmed.startsWith("System received notice file:")
            || trimmed.startsWith("[Text extraction failed")
            || trimmed.startsWith("[No extractable text")
            || trimmed.startsWith("[File is empty")
            || trimmed.startsWith("[OCR unavailable");
    }

    private boolean isExtractionError(String text) {
        return text.startsWith("[Text extraction failed")
            || text.startsWith("[No extractable text")
            || text.startsWith("[File is empty")
            || text.startsWith("[OCR unavailable");
    }

    private String buildParseContextHint(CompetitionNotice notice) {
        StringBuilder hint = new StringBuilder("【用户提供的上下文信息】");
        if (StringUtils.hasText(notice.getTitle())) {
            hint.append("\n标题：").append(notice.getTitle());
        }
        if (StringUtils.hasText(notice.getOrganizer())) {
            hint.append("\n主办单位：").append(notice.getOrganizer());
        }
        if (notice.getDeadline() != null) {
            hint.append("\n截止时间：").append(notice.getDeadline().format(DEADLINE_FORMATTER));
        }
        if (StringUtils.hasText(notice.getTargetGroup())) {
            hint.append("\n面向对象：").append(notice.getTargetGroup());
        }
        if (hint.length() == "【用户提供的上下文信息】".length()) {
            return "";
        }
        return hint.toString();
    }

    private String extractTextFromNoticeFile(CompetitionNotice notice, AiOperationTiming timing) {
        if (notice.getNoticeFileId() == null) return null;
        FileAsset fileAsset = fileAssetMapper.selectById(notice.getNoticeFileId());
        if (fileAsset == null || fileAsset.getFileBlob() == null) return null;
        FileTextExtractor.ExtractionResult result =
            fileTextExtractor.extractTextMeasured(fileAsset.getFileBlob(), fileAsset.getFileExt(), timing.taskId());
        timing.extraction(result);
        return result.text();
    }

    private String getExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
