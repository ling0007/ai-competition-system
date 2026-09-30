package com.eliza.aicompetition;

import com.eliza.aicompetition.common.enums.*;
import com.eliza.aicompetition.exception.InvalidStatusTransitionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 状态机流转测试 —— 验证所有合法和非法流转。
 */
@DisplayName("状态机流转测试")
class StatusStateMachineTests {

    // ==================== NoticeParseStatus ====================

    @Nested
    @DisplayName("NoticeParseStatus 流转")
    class NoticeParseStatusTests {

        @Test
        @DisplayName("DRAFT → PARSING 合法")
        void draftToParsing() {
            assertEquals(NoticeParseStatus.PARSING, NoticeParseStatus.DRAFT.startParsing());
        }

        @Test
        @DisplayName("PARSING → PARSED 合法")
        void parsingToParsed() {
            assertEquals(NoticeParseStatus.PARSED, NoticeParseStatus.PARSING.markParsed());
        }

        @Test
        @DisplayName("PARSING → FAILED 合法")
        void parsingToFailed() {
            assertEquals(NoticeParseStatus.FAILED, NoticeParseStatus.PARSING.markParseFailed());
        }

        @Test
        @DisplayName("FAILED → PARSING 合法（重试）")
        void failedToParsing() {
            assertEquals(NoticeParseStatus.PARSING, NoticeParseStatus.FAILED.startParsing());
        }

        @Test
        @DisplayName("FAILED → PARSED 合法（人工录入）")
        void failedToParsed() {
            assertEquals(NoticeParseStatus.PARSED, NoticeParseStatus.FAILED.completeManualEntry());
        }

        @Test
        @DisplayName("PARSED → PARSING 合法（重新解析）")
        void parsedToParsing() {
            assertEquals(NoticeParseStatus.PARSING, NoticeParseStatus.PARSED.restartParsing());
        }

        @Test
        @DisplayName("DRAFT → PARSED 非法")
        void draftToParsedIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                NoticeParseStatus.DRAFT.validateTransitionTo(NoticeParseStatus.PARSED));
        }

        @Test
        @DisplayName("DRAFT → FAILED 非法")
        void draftToFailedIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                NoticeParseStatus.DRAFT.validateTransitionTo(NoticeParseStatus.FAILED));
        }

        @Test
        @DisplayName("FAILED → DRAFT 非法")
        void failedToDraftIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                NoticeParseStatus.FAILED.validateTransitionTo(NoticeParseStatus.DRAFT));
        }

        @Test
        @DisplayName("PARSED → DRAFT 非法")
        void parsedToDraftIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                NoticeParseStatus.PARSED.validateTransitionTo(NoticeParseStatus.DRAFT));
        }

        @Test
        @DisplayName("相同状态幂等")
        void sameStatusIsIdempotent() {
            assertTrue(NoticeParseStatus.DRAFT.canTransitionTo(NoticeParseStatus.DRAFT));
            assertDoesNotThrow(() -> NoticeParseStatus.DRAFT.validateTransitionTo(NoticeParseStatus.DRAFT));
        }

        @Test
        @DisplayName("target=null 抛异常")
        void nullTargetThrows() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                NoticeParseStatus.DRAFT.validateTransitionTo(null));
        }
    }

    // ==================== NoticePublishStatus ====================

    @Nested
    @DisplayName("NoticePublishStatus 流转")
    class NoticePublishStatusTests {

        @Test
        @DisplayName("DRAFT → PUBLISHED 合法")
        void draftToPublished() {
            assertEquals(NoticePublishStatus.PUBLISHED, NoticePublishStatus.DRAFT.publish());
        }

        @Test
        @DisplayName("PUBLISHED → ARCHIVED 合法")
        void publishedToArchived() {
            assertEquals(NoticePublishStatus.ARCHIVED, NoticePublishStatus.PUBLISHED.archive());
        }

        @Test
        @DisplayName("PUBLISHED → DRAFT 非法")
        void publishedToDraftIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                NoticePublishStatus.PUBLISHED.validateTransitionTo(NoticePublishStatus.DRAFT));
        }

        @Test
        @DisplayName("ARCHIVED → PUBLISHED 非法")
        void archivedToPublishedIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                NoticePublishStatus.ARCHIVED.validateTransitionTo(NoticePublishStatus.PUBLISHED));
        }

        @Test
        @DisplayName("ARCHIVED → DRAFT 非法")
        void archivedToDraftIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                NoticePublishStatus.ARCHIVED.validateTransitionTo(NoticePublishStatus.DRAFT));
        }

        @Test
        @DisplayName("DRAFT → ARCHIVED 非法（跳过发布）")
        void draftToArchivedIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                NoticePublishStatus.DRAFT.validateTransitionTo(NoticePublishStatus.ARCHIVED));
        }
    }

    // ==================== ProjectStatus ====================

    @Nested
    @DisplayName("ProjectStatus 流转")
    class ProjectStatusTests {

        @Test
        @DisplayName("DRAFT → UNDER_REVIEW 合法")
        void draftToUnderReview() {
            assertEquals(ProjectStatus.UNDER_REVIEW, ProjectStatus.DRAFT.submitForReview());
        }

        @Test
        @DisplayName("UNDER_REVIEW → APPROVED 合法")
        void underReviewToApproved() {
            assertEquals(ProjectStatus.APPROVED, ProjectStatus.UNDER_REVIEW.approve());
        }

        @Test
        @DisplayName("UNDER_REVIEW → REVISION_REQUIRED 合法")
        void underReviewToRevisionRequired() {
            assertEquals(ProjectStatus.REVISION_REQUIRED, ProjectStatus.UNDER_REVIEW.requestRevision());
        }

        @Test
        @DisplayName("REVISION_REQUIRED → UNDER_REVIEW 合法")
        void revisionRequiredToUnderReview() {
            assertEquals(ProjectStatus.UNDER_REVIEW, ProjectStatus.REVISION_REQUIRED.resubmit());
        }

        @Test
        @DisplayName("REVISION_REQUIRED 不能调用首次提交动作")
        void revisionRequiredCannotInitialSubmit() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                ProjectStatus.REVISION_REQUIRED.submitForReview());
        }

        @Test
        @DisplayName("DRAFT 不能调用重新提交动作")
        void draftCannotResubmit() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                ProjectStatus.DRAFT.resubmit());
        }

        @Test
        @DisplayName("DRAFT → APPROVED 非法（跳过审核）")
        void draftToApprovedIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                ProjectStatus.DRAFT.validateTransitionTo(ProjectStatus.APPROVED));
        }

        @Test
        @DisplayName("APPROVED → REVISION_REQUIRED 非法（终态不可修改）")
        void approvedToRevisionIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                ProjectStatus.APPROVED.validateTransitionTo(ProjectStatus.REVISION_REQUIRED));
        }

        @Test
        @DisplayName("UNDER_REVIEW → DRAFT 非法")
        void underReviewToDraftIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                ProjectStatus.UNDER_REVIEW.validateTransitionTo(ProjectStatus.DRAFT));
        }

        @Test
        @DisplayName("REVISION_REQUIRED → APPROVED 非法（跳过审核）")
        void revisionRequiredToApprovedIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                ProjectStatus.REVISION_REQUIRED.validateTransitionTo(ProjectStatus.APPROVED));
        }

        @Test
        @DisplayName("REVISION_REQUIRED → DRAFT 非法")
        void revisionRequiredToDraftIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                ProjectStatus.REVISION_REQUIRED.validateTransitionTo(ProjectStatus.DRAFT));
        }

        @Test
        @DisplayName("APPROVED 是终态")
        void approvedIsTerminal() {
            assertFalse(ProjectStatus.APPROVED.canTransitionTo(ProjectStatus.DRAFT));
            assertFalse(ProjectStatus.APPROVED.canTransitionTo(ProjectStatus.UNDER_REVIEW));
            assertFalse(ProjectStatus.APPROVED.canTransitionTo(ProjectStatus.REVISION_REQUIRED));
        }

        @Test
        @DisplayName("旧DB值兼容映射")
        void legacyDbValueMapping() {
            assertEquals(ProjectStatus.DRAFT, ProjectStatus.fromDbValue("draft"));
            assertEquals(ProjectStatus.DRAFT, ProjectStatus.fromDbValue("incomplete"));
            assertEquals(ProjectStatus.DRAFT, ProjectStatus.fromDbValue("ready"));
            assertEquals(ProjectStatus.DRAFT, ProjectStatus.fromDbValue("ai_warning"));
            assertEquals(ProjectStatus.DRAFT, ProjectStatus.fromDbValue("ai_passed"));
            assertEquals(ProjectStatus.UNDER_REVIEW, ProjectStatus.fromDbValue("under_review"));
            assertEquals(ProjectStatus.REVISION_REQUIRED, ProjectStatus.fromDbValue("revision_required"));
            assertEquals(ProjectStatus.APPROVED, ProjectStatus.fromDbValue("approved"));
        }
    }

    // ==================== AgentTaskStatus ====================

    @Nested
    @DisplayName("AgentTaskStatus 流转")
    class AgentTaskStatusTests {

        @Test
        @DisplayName("PENDING → RUNNING 合法")
        void pendingToRunning() {
            assertEquals(AgentTaskStatus.RUNNING, AgentTaskStatus.PENDING.startRunning());
        }

        @Test
        @DisplayName("RUNNING → SUCCESS 合法")
        void runningToSuccess() {
            assertEquals(AgentTaskStatus.SUCCESS, AgentTaskStatus.RUNNING.markSuccess());
        }

        @Test
        @DisplayName("RUNNING → FAILED 合法")
        void runningToFailed() {
            assertEquals(AgentTaskStatus.FAILED, AgentTaskStatus.RUNNING.markFailed());
        }

        @Test
        @DisplayName("RUNNING → TIMEOUT 合法")
        void runningToTimeout() {
            assertEquals(AgentTaskStatus.TIMEOUT, AgentTaskStatus.RUNNING.markTimeout());
        }

        @Test
        @DisplayName("PENDING → SUCCESS 非法")
        void pendingToSuccessIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                AgentTaskStatus.PENDING.validateTransitionTo(AgentTaskStatus.SUCCESS));
        }

        @Test
        @DisplayName("FAILED → PENDING 非法")
        void failedToPendingIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                AgentTaskStatus.FAILED.validateTransitionTo(AgentTaskStatus.PENDING));
        }

        @Test
        @DisplayName("TIMEOUT → RUNNING 非法")
        void timeoutToRunningIllegal() {
            assertThrows(InvalidStatusTransitionException.class, () ->
                AgentTaskStatus.TIMEOUT.validateTransitionTo(AgentTaskStatus.RUNNING));
        }

        @Test
        @DisplayName("SUCCESS/FAILED/TIMEOUT 都是终态")
        void allTerminals() {
            assertTrue(AgentTaskStatus.SUCCESS.isTerminal());
            assertTrue(AgentTaskStatus.FAILED.isTerminal());
            assertTrue(AgentTaskStatus.TIMEOUT.isTerminal());
            assertFalse(AgentTaskStatus.PENDING.isTerminal());
            assertFalse(AgentTaskStatus.RUNNING.isTerminal());
        }
    }

    // ==================== AiCheckResult（不可变） ====================

    @Nested
    @DisplayName("AiCheckResult 映射")
    class AiCheckResultTests {

        @Test
        @DisplayName("fromRawResult 正确映射")
        void fromRawResultMapping() {
            assertEquals(AiCheckResult.PASSED, AiCheckResult.fromRawResult("pass"));
            assertEquals(AiCheckResult.PASSED, AiCheckResult.fromRawResult("passed"));
            assertEquals(AiCheckResult.WARNING, AiCheckResult.fromRawResult("warning"));
            assertEquals(AiCheckResult.WARNING, AiCheckResult.fromRawResult("reject"));
            assertEquals(AiCheckResult.WARNING, AiCheckResult.fromRawResult(null));
            assertEquals(AiCheckResult.WARNING, AiCheckResult.fromRawResult("unknown"));
        }
    }

    // ==================== MaterialReviewDecision（不可变） ====================

    @Nested
    @DisplayName("MaterialReviewDecision 映射")
    class MaterialReviewDecisionTests {

        @Test
        @DisplayName("fromValue 正确映射")
        void fromValueMapping() {
            assertEquals(MaterialReviewDecision.APPROVED, MaterialReviewDecision.fromValue("approved"));
            assertEquals(MaterialReviewDecision.REVISION_REQUIRED, MaterialReviewDecision.fromValue("revision"));
            assertEquals(MaterialReviewDecision.REVISION_REQUIRED, MaterialReviewDecision.fromValue("revision_required"));
        }

        @Test
        @DisplayName("fromValue 非法值抛异常")
        void invalidValueThrows() {
            assertThrows(IllegalArgumentException.class, () ->
                MaterialReviewDecision.fromValue("invalid"));
            assertThrows(IllegalArgumentException.class, () ->
                MaterialReviewDecision.fromValue(null));
        }
    }
}
