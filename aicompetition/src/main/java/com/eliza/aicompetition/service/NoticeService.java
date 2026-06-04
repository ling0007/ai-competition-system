package com.eliza.aicompetition.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eliza.aicompetition.common.FileTextExtractor;
import com.eliza.aicompetition.dto.ai.AiParseResult;
import com.eliza.aicompetition.dto.ai.AiParseResult.AiMaterialRequirement;
import com.eliza.aicompetition.dto.notice.NoticeParseResponse;
import com.eliza.aicompetition.dto.notice.NoticeUploadResponse;
import com.eliza.aicompetition.entity.AgentTaskLog;
import com.eliza.aicompetition.entity.CompetitionNotice;
import com.eliza.aicompetition.entity.FileAsset;
import com.eliza.aicompetition.entity.MaterialRequirement;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.AgentTaskLogMapper;
import com.eliza.aicompetition.mapper.CompetitionNoticeMapper;
import com.eliza.aicompetition.mapper.FileAssetMapper;
import com.eliza.aicompetition.mapper.MaterialRequirementMapper;
import com.eliza.aicompetition.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class NoticeService {

    private static final Logger log = LoggerFactory.getLogger(NoticeService.class);
    private static final DateTimeFormatter DEADLINE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CompetitionNoticeMapper competitionNoticeMapper;
    private final MaterialRequirementMapper materialRequirementMapper;
    private final FileAssetMapper fileAssetMapper;
    private final SysUserMapper sysUserMapper;
    private final AgentTaskLogMapper agentTaskLogMapper;
    private final AiService aiService;
    private final FileTextExtractor fileTextExtractor;

    public NoticeService(
        CompetitionNoticeMapper competitionNoticeMapper,
        MaterialRequirementMapper materialRequirementMapper,
        FileAssetMapper fileAssetMapper,
        SysUserMapper sysUserMapper,
        AgentTaskLogMapper agentTaskLogMapper,
        AiService aiService,
        FileTextExtractor fileTextExtractor
    ) {
        this.competitionNoticeMapper = competitionNoticeMapper;
        this.materialRequirementMapper = materialRequirementMapper;
        this.fileAssetMapper = fileAssetMapper;
        this.sysUserMapper = sysUserMapper;
        this.agentTaskLogMapper = agentTaskLogMapper;
        this.aiService = aiService;
        this.fileTextExtractor = fileTextExtractor;
    }

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
            throw new BusinessException("Please upload a notice file or provide rawText.");
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
        notice.setAiSummary("Pending parse");
        notice.setNoticeFileId(fileId);
        notice.setCreatedBy(createdBy);
        competitionNoticeMapper.insert(notice);

        return new NoticeUploadResponse(notice.getNoticeId(), fileId, notice.getTitle());
    }

    @Transactional
    public NoticeParseResponse parseNotice(Long noticeId) {
        CompetitionNotice notice = getNoticeOrThrow(noticeId);

        // 1. Obtain text to parse: prefer real rawText, fall back to file extraction
        String textToParse = notice.getRawText();
        log.info("parseNotice noticeId={}: rawText from DB is {} chars",
            noticeId, textToParse != null ? textToParse.length() : 0);

        if (!StringUtils.hasText(textToParse) || isSystemPlaceholder(textToParse)) {
            log.info("parseNotice noticeId={}: rawText is empty/placeholder, extracting from file...", noticeId);
            String extracted = extractTextFromNoticeFile(notice);
            log.info("parseNotice noticeId={}: extracted text from file, length={}",
                noticeId, extracted != null ? extracted.length() : 0);

            if (extracted != null && !isExtractionError(extracted)) {
                // File text extracted successfully
                textToParse = extracted;
                if (extracted.length() > 0 && extracted.length() <= 200) {
                    log.info("parseNotice noticeId={}: extracted text preview: {}", noticeId, extracted);
                }
            } else if (extracted != null) {
                // Tika returned an error string — file has no extractable text (e.g. scanned PDF)
                throw new BusinessException(
                    "上传的PDF文件无法提取文字内容（可能是扫描版或图片格式PDF）。"
                    + "请在「通知原文/补充说明」文本框中粘贴通知内容后重新保存，再执行智能解析。"
                );
            }
            // if extracted is null, textToParse remains the original placeholder → will throw below
        }

        if (!StringUtils.hasText(textToParse) || isSystemPlaceholder(textToParse)) {
            throw new BusinessException("无法解析通知：既无文本内容，也无法从附件提取文本。"
                + "请在上传时填写「通知原文/补充说明」或上传可提取文字的通知文件。");
        }

        // 2. Enrich with form-field hints (user may have filled these during upload)
        String contextHint = buildParseContextHint(notice);
        String fullText = contextHint.isEmpty() ? textToParse : contextHint + "\n\n" + textToParse;

        // 3. Call LLM to parse the notice (text truncated to 4000 chars inside AiService)
        AiParseResult parseResult = aiService.parseNotice(fullText);

        // 4. Sync LLM-extracted fields back to the notice entity (only if user didn't manually set them)
        if (!StringUtils.hasText(notice.getOrganizer()) && parseResult.organizer() != null) {
            notice.setOrganizer(parseResult.organizer());
        }
        if (notice.getDeadline() == null && parseResult.deadline() != null) {
            notice.setDeadline(parseDeadline(parseResult.deadline()));
        }
        if (!StringUtils.hasText(notice.getTargetGroup()) && parseResult.targetGroup() != null) {
            notice.setTargetGroup(parseResult.targetGroup());
        }

        // 5. Replace old requirements with AI-extracted ones
        materialRequirementMapper.delete(
            new LambdaQueryWrapper<MaterialRequirement>()
                .eq(MaterialRequirement::getNoticeId, noticeId)
        );

        List<MaterialRequirement> requirements = new ArrayList<>();
        if (parseResult.materials() != null && !parseResult.materials().isEmpty()) {
            int sortNo = 1;
            for (AiMaterialRequirement aiMat : parseResult.materials()) {
                MaterialRequirement req = new MaterialRequirement();
                req.setNoticeId(noticeId);
                req.setRequirementName(aiMat.name());
                req.setIsRequired(aiMat.isRequired() ? 1 : 0);
                req.setDescription(aiMat.description());
                req.setSortNo(sortNo++);
                materialRequirementMapper.insert(req);
                requirements.add(req);
            }
        }

        // 4. Build and save AI summary
        String aiSummary = buildSummaryFromParseResult(notice, parseResult, requirements);
        notice.setAiSummary(aiSummary);
        competitionNoticeMapper.updateById(notice);

        // 5. Log agent task
        AgentTaskLog taskLog = new AgentTaskLog();
        taskLog.setToolName("parseNoticeTool");
        taskLog.setInputSummary("解析通知: " + notice.getTitle() + " (" + textToParse.length() + " 字符)");
        taskLog.setResultSummary(aiSummary.length() > 500 ? aiSummary.substring(0, 500) + "..." : aiSummary);
        taskLog.setExecuteStatus("success");
        agentTaskLogMapper.insert(taskLog);

        // 7. Build response — include all extracted fields for the frontend to display
        List<String> requirementNames = requirements.stream()
            .map(MaterialRequirement::getRequirementName)
            .toList();
        return new NoticeParseResponse(
            noticeId,
            notice.getTitle(),
            notice.getOrganizer(),
            notice.getDeadline(),
            notice.getTargetGroup(),
            aiSummary,
            requirementNames
        );
    }

    public CompetitionNotice getNoticeOrThrow(Long noticeId) {
        CompetitionNotice notice = competitionNoticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new BusinessException("Notice not found: noticeId=" + noticeId);
        }
        return notice;
    }

    public List<MaterialRequirement> findRequirementsByNoticeId(Long noticeId) {
        LambdaQueryWrapper<MaterialRequirement> queryWrapper = new LambdaQueryWrapper<MaterialRequirement>()
            .eq(MaterialRequirement::getNoticeId, noticeId)
            .orderByAsc(MaterialRequirement::getSortNo)
            .orderByAsc(MaterialRequirement::getRequirementId);
        return materialRequirementMapper.selectList(queryWrapper);
    }

    private void validateUser(Long userId) {
        if (sysUserMapper.selectById(userId) == null) {
            throw new BusinessException("User not found: userId=" + userId);
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
            throw new BusinessException("Failed to read uploaded file: " + exception.getMessage());
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
        return "Untitled Notice";
    }

    /**
     * Build rawText for storage. If the user provided text, use it.
     * If only a file was uploaded, store null — the file content will
     * be extracted on demand when parseNotice() is called.
     */
    private String buildRawText(String rawText, MultipartFile file) {
        if (StringUtils.hasText(rawText)) {
            return rawText.trim();
        }
        // Do NOT store a system placeholder — leave rawText null so that
        // parseNotice() knows to extract text from the attached file instead.
        return null;
    }

    /**
     * Parse an LLM-returned deadline string into LocalDateTime.
     * Supports formats like "yyyy-MM-dd HH:mm", "yyyy-MM-dd'T'HH:mm:ss", etc.
     */
    private LocalDateTime parseDeadline(String deadlineStr) {
        if (!StringUtils.hasText(deadlineStr)) return null;
        try {
            return LocalDateTime.parse(deadlineStr, DEADLINE_FORMATTER);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(deadlineStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (Exception e2) {
                log.warn("Failed to parse deadline string: {}", deadlineStr);
                return null;
            }
        }
    }

    /**
     * Detect system-generated placeholder strings that are not real notice content.
     */
    private boolean isSystemPlaceholder(String rawText) {
        if (rawText == null) return true;
        String trimmed = rawText.trim();
        return "Notice text is pending.".equals(trimmed)
            || trimmed.startsWith("System received notice file:")
            || trimmed.startsWith("[Text extraction failed")
            || trimmed.startsWith("[No extractable text")
            || trimmed.startsWith("[File is empty");
    }

    /**
     * Detect Tika extraction error strings that indicate no real text was extracted.
     */
    private boolean isExtractionError(String text) {
        return text.startsWith("[Text extraction failed")
            || text.startsWith("[No extractable text")
            || text.startsWith("[File is empty");
    }

    /**
     * Build a context hint string from the form fields the user filled during upload.
     * This gives the LLM additional clues when the file-extracted text is noisy.
     */
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
            return ""; // no hints available
        }
        return hint.toString();
    }

    /**
     * Extract plain text from the notice's attached file (PDF/DOCX/etc.) via Tika.
     */
    private String extractTextFromNoticeFile(CompetitionNotice notice) {
        if (notice.getNoticeFileId() == null) return null;
        FileAsset fileAsset = fileAssetMapper.selectById(notice.getNoticeFileId());
        if (fileAsset == null || fileAsset.getFileBlob() == null) return null;
        return fileTextExtractor.extractText(fileAsset.getFileBlob(), fileAsset.getFileExt());
    }

    /**
     * Format the AI parse result into a human-readable Chinese summary string
     * for storage in {@code competition_notice.ai_summary}.
     */
    private String buildSummaryFromParseResult(
        CompetitionNotice notice,
        AiParseResult parseResult,
        List<MaterialRequirement> requirements
    ) {
        String organizer = parseResult.organizer() != null
            ? parseResult.organizer() : (notice.getOrganizer() != null ? notice.getOrganizer() : "未知");
        String deadline = notice.getDeadline() != null
            ? notice.getDeadline().format(DEADLINE_FORMATTER)
            : (parseResult.deadline() != null ? parseResult.deadline() : "未知");
        String target = parseResult.targetGroup() != null
            ? parseResult.targetGroup() : (notice.getTargetGroup() != null ? notice.getTargetGroup() : "未知");
        String keyPoints = parseResult.keyPoints() != null ? parseResult.keyPoints() : "";
        String reqNames = requirements.stream()
            .map(MaterialRequirement::getRequirementName)
            .reduce((a, b) -> a + "、" + b)
            .orElse("暂无材料要求");
        return "【AI解析】主办方：" + organizer
            + "；截止时间：" + deadline
            + "；面向对象：" + target
            + "；关键内容：" + keyPoints
            + "；共识别 " + requirements.size() + " 项材料要求：" + reqNames + "。";
    }

    private String getExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            return null;
        }
        return fileName.substring(fileName.lastIndexOf('.') + 1);
    }
}
