package com.eliza.aicompetition;

import com.eliza.aicompetition.dto.ai.AiParseResult;
import com.eliza.aicompetition.dto.notice.NoticeParseTaskResponse;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.security.LoginUser;
import com.eliza.aicompetition.service.AiService;
import com.eliza.aicompetition.service.NoticeAiTaskDispatcher;
import com.eliza.aicompetition.service.NoticeService;
import com.eliza.aicompetition.service.RedisService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "ai.executor.core-size=1", "ai.executor.max-size=1",
    "ai.executor.queue-capacity=1", "notice.ai.recovery-scan-ms=600000"
})
@AutoConfigureMockMvc
class NoticeAsyncIntegrationTests extends IsolatedDatabaseSpringTest {
    private static final AtomicLong IDS = new AtomicLong(999100);
    @Autowired private JdbcTemplate db;
    @Autowired private NoticeService notices;
    @Autowired private NoticeAiTaskDispatcher dispatcher;
    @Autowired private ThreadPoolTaskExecutor noticeAiExecutor;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private AiService ai;
    @MockitoBean private RedisService redis;
    private long noticeId;
    private long adminId;
    private Long sampleFileId;

    @BeforeEach
    void seed() {
        noticeId = IDS.incrementAndGet();
        adminId = IDS.incrementAndGet();
        db.update("INSERT INTO sys_user(user_id,username,password,real_name,role) VALUES (?,?,?,?,?)",
            adminId, "async-admin-" + adminId, "{noop}pw", "Admin", "admin");
        db.update("INSERT INTO competition_notice(notice_id,title,created_by,raw_text,parse_status,publish_status,version) "
                + "VALUES (?,?,?,?,?,?,0)", noticeId, "Async notice", adminId, "通知原文：提交申报书", "DRAFT", "DRAFT");
        when(ai.parseNoticeMeasured(anyString())).thenReturn(model(false));
    }

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM notice_parse_draft WHERE notice_id=?", noticeId);
        db.update("DELETE FROM agent_task_log WHERE business_type='NOTICE_PARSE' AND business_id=?", noticeId);
        db.update("DELETE FROM competition_notice WHERE notice_id=?", noticeId);
        if (sampleFileId != null) db.update("DELETE FROM file_asset WHERE file_id=?", sampleFileId);
        db.update("DELETE FROM sys_user WHERE user_id=?", adminId);
    }

    @Test
    void acceptedTaskIsPendingAndDatabasePreventsDuplicateActiveTask() {
        NoticeParseTaskResponse accepted = notices.acceptParseTask(noticeId, adminId);
        assertEquals("PENDING", accepted.status());
        assertEquals("PENDING", taskStatus(accepted.taskId()));
        assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM agent_task_log WHERE task_id=? "
            + "AND observation_payload IS NULL AND error_category IS NULL AND degradation_reason IS NULL",
            Long.class, accepted.taskId()));
        assertThrows(BusinessException.class, () -> notices.acceptParseTask(noticeId, adminId));
        notices.executeParseTask(accepted.taskId());
        assertEquals("SUCCESS/MODEL", taskResult(accepted.taskId()));
        verify(ai, times(1)).parseNoticeMeasured(anyString());
        assertEquals("TEXT", db.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(observation_payload,'$.processingPaths[0]')) "
            + "FROM agent_task_log WHERE task_id=?", String.class, accepted.taskId()));
    }

    @Test
    void textPdfTaskRecordsFourPagesWithoutVisionOcr() throws Exception {
        sampleFileId = IDS.incrementAndGet();
        byte[] pdf = Files.readAllBytes(Path.of("../docs/testdata/文字版pdfCIMC华东二赛区.pdf"));
        db.update("INSERT INTO file_asset(file_id,biz_type,file_name,file_ext,file_size,file_blob,uploaded_by) "
            + "VALUES (?,?,?,?,?,?,?)", sampleFileId, "notice", "notice.pdf", "pdf", pdf.length, pdf, adminId);
        db.update("UPDATE competition_notice SET raw_text=NULL,notice_file_id=? WHERE notice_id=?", sampleFileId, noticeId);
        long taskId = notices.acceptParseTask(noticeId, adminId).taskId();
        notices.executeParseTask(taskId);
        assertEquals("SUCCESS/MODEL", taskResult(taskId));
        assertEquals("TIKA/4/0/0", db.queryForObject("SELECT CONCAT("
            + "JSON_UNQUOTE(JSON_EXTRACT(observation_payload,'$.processingPaths[0]')),'/',"
            + "JSON_EXTRACT(observation_payload,'$.pdfPages'),'/',"
            + "JSON_EXTRACT(observation_payload,'$.ocrAttemptedPages'),'/',"
            + "JSON_EXTRACT(observation_payload,'$.ocrFailedPages')) "
            + "FROM agent_task_log WHERE task_id=?", String.class, taskId));
        assertNotNull(db.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(observation_payload,'$.promptVersion')) "
            + "FROM agent_task_log WHERE task_id=?", String.class, taskId));
    }

    @Test
    void concurrentAcceptRequestsLeaveExactlyOneActiveNoticeTask() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var calls = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                start.await();
                try { return notices.acceptParseTask(noticeId, adminId); }
                catch (BusinessException rejected) { return rejected; }
            })).toList();
            start.countDown();
            var results = calls.stream().map(call -> {
                try { return call.get(10, TimeUnit.SECONDS); }
                catch (Exception error) { throw new RuntimeException(error); }
            }).toList();
            assertEquals(1L, results.stream().filter(NoticeParseTaskResponse.class::isInstance).count());
            assertEquals(1L, results.stream().filter(BusinessException.class::isInstance).count());
            assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM agent_task_log "
                + "WHERE business_type='NOTICE_PARSE' AND business_id=? AND active_marker=1",
                Long.class, noticeId));
            assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM notice_parse_draft WHERE notice_id=?",
                Long.class, noticeId));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void missingNoticeInputIsRejectedBeforeTaskCreation() {
        db.update("UPDATE competition_notice SET raw_text=NULL WHERE notice_id=?", noticeId);
        assertThrows(BusinessException.class, () -> notices.acceptParseTask(noticeId, adminId));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM agent_task_log WHERE business_type='NOTICE_PARSE' AND business_id=?",
            Long.class, noticeId));
    }

    @Test
    void apiReturnsTaskIdBeforeBlockedModelCompletesAndRestrictsTaskQueries() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(ai.parseNoticeMeasured(anyString())).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return model(false);
        });
        try {
            long start = System.nanoTime();
            var result = mvc.perform(post("/notice/parse/{id}", noticeId).with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.taskId").isNumber()).andReturn();
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000);
            long taskId = json.readTree(result.getResponse().getContentAsString()).path("data").path("taskId").asLong();
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            mvc.perform(get("/notice/parse-tasks/{id}", taskId).with(authentication(adminAuth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"))
                .andExpect(jsonPath("$.data.businessType").value("NOTICE_PARSE"))
                .andExpect(jsonPath("$.data.attemptNo").value(1))
                .andExpect(jsonPath("$.data.requestPayload").doesNotExist())
                .andExpect(jsonPath("$.data.observationPayload").doesNotExist())
                .andExpect(jsonPath("$.data.promptHash").doesNotExist());
            mvc.perform(get("/notice/parse-tasks/{id}", taskId).with(authentication(studentAuth())))
                .andExpect(status().isForbidden());
            mvc.perform(get("/notice/{id}/parse-task/latest", noticeId).with(authentication(adminAuth())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.taskId").value(taskId));
            release.countDown();
            awaitStatus(taskId, "SUCCESS");
        } finally {
            release.countDown();
        }
    }

    @Test
    void duplicateWorkerClaimAndLateResultAfterTimeoutAreFenced() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(ai.parseNoticeMeasured(anyString())).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return model(false);
        });
        long taskId = notices.acceptParseTask(noticeId, adminId).taskId();
        Thread worker = new Thread(() -> notices.executeParseTask(taskId));
        worker.start();
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            notices.executeParseTask(taskId);
            verify(ai, times(1)).parseNoticeMeasured(anyString());
            db.update("UPDATE agent_task_log SET created_at=DATE_SUB(NOW(), INTERVAL 10 MINUTE) WHERE task_id=?", taskId);
            notices.timeoutParseTask(taskId);
            assertEquals("TIMEOUT/NONE", taskResult(taskId));
        } finally {
            release.countDown();
            worker.join(5000);
        }
        assertEquals("TIMEOUT/NONE", taskResult(taskId));
        assertEquals("TASK_TIMEOUT", db.queryForObject("SELECT error_category FROM agent_task_log WHERE task_id=?",
            String.class, taskId));
        assertEquals("true", db.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(observation_payload,'$.workerMeasured')) "
            + "FROM agent_task_log WHERE task_id=?", String.class, taskId));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM notice_parse_draft WHERE notice_id=?", Long.class, noticeId));
    }

    @Test
    void pendingRecoveryRunsOnceAndAbandonedRunningConverges() throws Exception {
        long first = notices.acceptParseTask(noticeId, adminId).taskId();
        dispatcher.scan();
        awaitStatus(first, "SUCCESS");
        long second = notices.acceptParseTask(noticeId, adminId).taskId();
        db.update("UPDATE agent_task_log SET execute_status='RUNNING',started_at=NOW() WHERE task_id=?", second);
        dispatcher.recoverOnStartup();
        assertEquals("FAILED/NONE", taskResult(second));
        assertEquals("PARSED", db.queryForObject("SELECT parse_status FROM competition_notice WHERE notice_id=?", String.class, noticeId));
        assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM notice_parse_draft WHERE notice_id=? AND status='PENDING'", Long.class, noticeId));
    }

    @Test
    void expiredRunningRecoveryTimesOutAndReleasesActiveMarker() {
        long taskId = notices.acceptParseTask(noticeId, adminId).taskId();
        db.update("UPDATE agent_task_log SET execute_status='RUNNING',started_at=DATE_SUB(NOW(), INTERVAL 10 MINUTE),"
            + "created_at=DATE_SUB(NOW(), INTERVAL 10 MINUTE) WHERE task_id=?", taskId);
        dispatcher.recoverOnStartup();
        assertEquals("TIMEOUT/NONE", taskResult(taskId));
        assertEquals(0L, db.queryForObject(
            "SELECT COUNT(*) FROM agent_task_log WHERE task_id=? AND active_marker=1", Long.class, taskId));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM notice_parse_draft WHERE notice_id=?", Long.class, noticeId));
    }

    @Test
    void executorRejectionLeavesPendingForRecoveryWithoutRunningOnCaller() throws Exception {
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        noticeAiExecutor.execute(() -> {
            occupied.countDown();
            try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        assertTrue(occupied.await(3, TimeUnit.SECONDS));
        noticeAiExecutor.execute(() -> {}); // bounded queue is now full
        long taskId = notices.acceptParseTask(noticeId, adminId).taskId();
        try {
            long start = System.nanoTime();
            dispatcher.dispatch(taskId);
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000);
            assertEquals("PENDING", taskStatus(taskId));
        } finally {
            release.countDown();
        }
        for (int i = 0; i < 100 && noticeAiExecutor.getThreadPoolExecutor().getQueue().size() > 0; i++) Thread.sleep(20);
        dispatcher.scan();
        awaitStatus(taskId, "SUCCESS");
    }

    @Test
    void expiredPendingTaskConvergesWithoutCallingModel() {
        long taskId = notices.acceptParseTask(noticeId, adminId).taskId();
        db.update("UPDATE agent_task_log SET created_at=DATE_SUB(NOW(), INTERVAL 10 MINUTE) WHERE task_id=?", taskId);
        dispatcher.scan();
        assertEquals("TIMEOUT/NONE", taskResult(taskId));
        verify(ai, never()).parseNoticeMeasured(anyString());
    }

    @Test
    void changedInputCannotCommitAndOldDraftIsPreservedAsHistory() throws Exception {
        db.update("INSERT INTO notice_parse_draft(notice_id,ai_title,status,created_by) VALUES (?,?,?,?)",
            noticeId, "Old draft", "PENDING", adminId);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(ai.parseNoticeMeasured(anyString())).thenAnswer(call -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return model(false);
        });
        long taskId = notices.acceptParseTask(noticeId, adminId).taskId();
        Thread worker = new Thread(() -> notices.executeParseTask(taskId));
        worker.start();
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            db.update("UPDATE competition_notice SET raw_text='new input',version=version+1 WHERE notice_id=?", noticeId);
        } finally {
            release.countDown();
            worker.join(5000);
        }
        assertEquals("FAILED/NONE", taskResult(taskId));
        assertEquals("INPUT_CHANGED", db.queryForObject("SELECT error_category FROM agent_task_log WHERE task_id=?",
            String.class, taskId));
        assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM notice_parse_draft WHERE notice_id=? AND status='REJECTED'", Long.class, noticeId));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM notice_parse_draft WHERE notice_id=? AND status='PENDING'", Long.class, noticeId));
    }

    @Test
    void fallbackSupersedesOldDraftWithoutDeletingHistory() {
        db.update("INSERT INTO notice_parse_draft(notice_id,ai_title,status,created_by) VALUES (?,?,?,?)",
            noticeId, "Old draft", "PENDING", adminId);
        when(ai.parseNoticeMeasured(anyString())).thenReturn(model(true));
        long taskId = notices.acceptParseTask(noticeId, adminId).taskId();
        assertThrows(BusinessException.class, () -> notices.getParseDraft(noticeId));
        notices.executeParseTask(taskId);
        assertEquals("SUCCESS/FALLBACK", taskResult(taskId));
        assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM notice_parse_draft WHERE notice_id=? AND status='REJECTED'", Long.class, noticeId));
        assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM notice_parse_draft WHERE notice_id=? AND status='PENDING'", Long.class, noticeId));
    }

    private static AiService.CallResult<AiParseResult> model(boolean fallback) {
        return new AiService.CallResult<>(new AiParseResult("Parsed title", "Organizer", "2030-06-30 18:00",
            "Students", "Summary", List.of()), 10, !fallback, fallback);
    }

    private String taskStatus(long id) {
        return db.queryForObject("SELECT execute_status FROM agent_task_log WHERE task_id=?", String.class, id);
    }

    private String taskResult(long id) {
        return db.queryForObject("SELECT CONCAT(execute_status,'/',result_origin) FROM agent_task_log WHERE task_id=?", String.class, id);
    }

    private void awaitStatus(long id, String expected) throws InterruptedException {
        for (int i = 0; i < 100; i++) {
            if (expected.equals(taskStatus(id))) return;
            Thread.sleep(30);
        }
        fail("Task did not reach " + expected + ": " + taskStatus(id));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        return new UsernamePasswordAuthenticationToken(new LoginUser(adminId, "async-admin", "Admin", "admin"), null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private UsernamePasswordAuthenticationToken studentAuth() {
        return new UsernamePasswordAuthenticationToken(new LoginUser(adminId, "student", "Student", "student"), null,
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }
}
