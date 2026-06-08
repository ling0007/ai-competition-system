package com.eliza.aicompetition.dto.notice;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Response from AI notice parsing.
 * Includes all LLM-extracted fields so the frontend can update its display.
 */
public record NoticeParseResponse(
    Long noticeId,
    String title,
    String organizer,
    LocalDateTime deadline,
    String targetGroup,
    String aiSummary,
    List<String> materialRequirements
) {}
