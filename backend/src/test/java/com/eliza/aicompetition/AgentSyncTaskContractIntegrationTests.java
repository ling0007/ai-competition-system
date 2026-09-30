package com.eliza.aicompetition;

import com.eliza.aicompetition.common.FileTextExtractor;
import com.eliza.aicompetition.dto.ai.AiCheckResponse;
import com.eliza.aicompetition.security.LoginUser;
import com.eliza.aicompetition.service.AgentService;
import com.eliza.aicompetition.service.AiService;
import com.eliza.aicompetition.service.RedisService;
import com.eliza.aicompetition.service.RedisLockService;
import com.eliza.aicompetition.service.MaterialService;
import com.eliza.aicompetition.service.DashboardCacheService;
import com.eliza.aicompetition.controller.AdminController;
import com.eliza.aicompetition.dto.user.UpdateRoleRequest;
import com.eliza.aicompetition.service.NoticeAiTaskDispatcher;
import com.eliza.aicompetition.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "ai.executor.core-size=1", "ai.executor.max-size=1",
    "ai.executor.queue-capacity=1", "notice.ai.recovery-scan-ms=600000"
})
@AutoConfigureMockMvc
class AgentSyncTaskContractIntegrationTests extends IsolatedDatabaseSpringTest {
    private static final long USER = 991001L;
    private static final long NOTICE = 992001L;
    private static final long PROJECT = 993001L;
    private static final long REQUIREMENT = 994001L;
    private static final long FILE = 995001L;
    private static final long MATERIAL = 996001L;

    @Autowired private JdbcTemplate db;
    @Autowired private AgentService agentService;
    @Autowired private MaterialService materialService;
    @Autowired private DashboardCacheService dashboardCacheService;
    @Autowired private AdminController adminController;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private NoticeAiTaskDispatcher dispatcher;
    @Autowired private ThreadPoolTaskExecutor aiExecutor;
    @MockitoBean private RedisService redisService;
    @MockitoBean private RedisLockService redisLockService;
    @MockitoBean private FileTextExtractor fileTextExtractor;
    @MockitoBean private AiService aiService;

    @BeforeEach
    void seed() {
        when(redisLockService.acquire(anyString(), any()))
            .thenReturn(new RedisLockService.Lease(true, "test-owner"));
        db.update("INSERT INTO sys_user(user_id,username,password,real_name,role) VALUES (?,?,?,?,?)",
            USER, "phase52-student", "{noop}pw", "Phase52", "student");
        db.update("INSERT INTO competition_notice(notice_id,title,created_by,parse_status,publish_status) "
            + "VALUES (?,?,?,?,?)", NOTICE, "Phase52", USER, "PARSED", "PUBLISHED");
        db.update("INSERT INTO competition_project(project_id,notice_id,leader_id,project_name,status) "
            + "VALUES (?,?,?,?,?)", PROJECT, NOTICE, USER, "Phase52 project", "DRAFT");
        db.update("INSERT INTO project_member(project_id,user_id,member_role) VALUES (?,?,?)",
            PROJECT, USER, "leader");
        db.update("INSERT INTO material_requirement(requirement_id,notice_id,requirement_name,is_required,status) "
            + "VALUES (?,?,?,?,?)", REQUIREMENT, NOTICE, "材料", 1, "ACTIVE");
        db.update("INSERT INTO file_asset(file_id,biz_type,file_name,file_ext,file_size,file_blob,uploaded_by) "
            + "VALUES (?,?,?,?,?,?,?)", FILE, "material", "material.txt", "txt", 4, "body".getBytes(), USER);
        db.update("INSERT INTO project_material(material_id,project_id,requirement_id,file_id,version_no,"
            + "current_version_id,submit_status,submitted_at) VALUES (?,?,?,?,?,?,?,NOW())",
            MATERIAL, PROJECT, REQUIREMENT, FILE, 1, MATERIAL, "submitted");
        var principal = new LoginUser(USER, "phase52-student", "Phase52", "student");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        when(fileTextExtractor.extractTextMeasured(any(), anyString(), any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                "Tika/OCR must run without an active DB transaction");
            return new FileTextExtractor.ExtractionResult("正文", 1, 1, 0, false);
        });
    }

    @AfterEach
    void cleanUp() {
        SecurityContextHolder.clearContext();
        db.update("DELETE FROM notify_message WHERE project_id=?", PROJECT);
        db.update("DELETE FROM project_ai_check WHERE project_id=?", PROJECT);
        db.update("DELETE FROM review_record WHERE project_id=?", PROJECT);
        db.update("DELETE FROM agent_task_log WHERE business_type='MATERIAL_CHECK' AND business_id=?", PROJECT);
        List<Long> fileIds = db.queryForList(
            "SELECT file_id FROM project_material WHERE project_id=? AND file_id IS NOT NULL",
            Long.class, PROJECT);
        db.update("DELETE FROM project_material WHERE project_id=?", PROJECT);
        for (Long fileId : fileIds) {
            db.update("DELETE FROM file_asset WHERE file_id=?", fileId);
        }
        db.update("DELETE FROM material_requirement WHERE requirement_id=?", REQUIREMENT);
        db.update("DELETE FROM project_member WHERE project_id=?", PROJECT);
        db.update("DELETE FROM competition_project WHERE project_id=?", PROJECT);
        db.update("DELETE FROM competition_notice WHERE notice_id=?", NOTICE);
        db.update("DELETE FROM sys_user WHERE user_id=?", USER);
    }

    @Test
    void uploadReleasesLeaseOnlyAfterCommittedVersionIsVisible() {
        doAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(2L, db.queryForObject(
                "SELECT COUNT(*) FROM project_material WHERE project_id=?", Long.class, PROJECT));
            return null;
        }).when(redisLockService).release(anyString(), any());

        var uploaded = materialService.uploadMaterial(PROJECT, REQUIREMENT, USER, null,
            new MockMultipartFile("file", "new.txt", "text/plain", "new".getBytes()));
        assertEquals(2, uploaded.versionNo());
        verify(redisLockService).release(anyString(), any());
    }

    @Test
    void uploadDefersLeaseReleaseUntilOuterTransactionCompletes() {
        transactionTemplate.executeWithoutResult(tx -> {
            materialService.uploadMaterial(PROJECT, REQUIREMENT, USER, null,
                new MockMultipartFile("file", "new.txt", "text/plain", "new".getBytes()));
            verify(redisLockService, never()).release(anyString(), any());
        });
        verify(redisLockService).release(anyString(), any());
    }

    @Test
    void uploadUsesDatabaseProtectionWhenRedisAcquisitionFails() {
        when(redisLockService.acquire(anyString(), any()))
            .thenReturn(new RedisLockService.Lease(true, null));
        var uploaded = materialService.uploadMaterial(PROJECT, REQUIREMENT, USER, null,
            new MockMultipartFile("file", "new.txt", "text/plain", "new".getBytes()));
        assertEquals(2, uploaded.versionNo());
        assertEquals(2L, db.queryForObject(
            "SELECT COUNT(*) FROM project_material WHERE project_id=?", Long.class, PROJECT));
    }

    @Test
    void concurrentUploadsWithoutRedisSerializeOnDatabaseRows() throws Exception {
        when(redisLockService.acquire(anyString(), any()))
            .thenReturn(new RedisLockService.Lease(true, null));
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var uploads = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                var principal = new LoginUser(USER, "phase52-student", "Phase52", "student");
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
                try {
                    start.await();
                    return materialService.uploadMaterial(PROJECT, REQUIREMENT, USER, null,
                        new MockMultipartFile("file", "new.txt", "text/plain",
                            ("new" + index).getBytes()));
                } finally {
                    SecurityContextHolder.clearContext();
                }
            })).toList();
            start.countDown();
            assertEquals(List.of(2, 3), uploads.stream().map(task -> {
                try { return task.get(10, TimeUnit.SECONDS).versionNo(); }
                catch (Exception e) { throw new RuntimeException(e); }
            }).sorted().toList());
            assertEquals(3L, db.queryForObject(
                "SELECT COUNT(*) FROM project_material WHERE project_id=?", Long.class, PROJECT));
            assertEquals(1L, db.queryForObject(
                "SELECT COUNT(*) FROM project_material WHERE project_id=? AND current_version_id=material_id",
                Long.class, PROJECT));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void dashboardInvalidationRunsAfterCommitAndSkipsRollback() {
        transactionTemplate.executeWithoutResult(tx -> {
            dashboardCacheService.invalidateGlobalAfterCommit("test-commit");
            verify(redisService, never()).increment(anyString());
        });
        verify(redisService).increment("dashboard:bootstrap:global-epoch");
    }

    @Test
    void dashboardRollbackDoesNotAdvanceCacheEpoch() {
        transactionTemplate.executeWithoutResult(tx -> {
            dashboardCacheService.invalidateGlobalAfterCommit("test-rollback");
            tx.setRollbackOnly();
        });
        verify(redisService, never()).increment(anyString());
    }

    @Test
    void committedUserRoleWriteInvalidatesDashboardGlobalCache() {
        when(redisService.increment("dashboard:bootstrap:global-epoch")).thenAnswer(invocation -> {
            assertEquals("teacher", java.util.concurrent.CompletableFuture.supplyAsync(() ->
                db.queryForObject("SELECT role FROM sys_user WHERE user_id=?", String.class, USER)).join());
            return 1L;
        });
        var request = new UpdateRoleRequest();
        request.setRole("teacher");
        adminController.updateRole(USER, request);
        verify(redisService).increment("dashboard:bootstrap:global-epoch");
    }

    @Test
    void failedCacheInvalidationDoesNotRollBackUserRoleWrite() {
        when(redisService.increment("dashboard:bootstrap:global-epoch")).thenReturn(0L);
        var request = new UpdateRoleRequest();
        request.setRole("teacher");
        adminController.updateRole(USER, request);
        assertEquals("teacher", db.queryForObject(
            "SELECT role FROM sys_user WHERE user_id=?", String.class, USER));
        verify(redisService).increment("dashboard:bootstrap:global-epoch");
    }

    @Test
    void modelAndFallbackHaveDifferentDurableOriginsAndNoLongTransaction() {
        when(aiService.checkMaterialMeasured(anyString(), anyString())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),
                "LLM must run without an active DB transaction");
            return new AiService.CallResult<>(new AiCheckResponse("pass", "模型通过"), 2, true, false);
        });
        var first = agentService.checkMaterial(PROJECT);
        assertEquals("PENDING", first.status());
        agentService.executeMaterialTask(first.taskId());

        when(aiService.checkMaterialMeasured(anyString(), anyString())).thenReturn(
            new AiService.CallResult<>(new AiCheckResponse("warning", "服务不可用，人工复核"), 0, false, true));
        var second = agentService.checkMaterial(PROJECT);
        agentService.executeMaterialTask(second.taskId());

        assertEquals(List.of("MODEL", "FALLBACK"), db.queryForList(
            "SELECT result_origin FROM agent_task_log WHERE business_type='MATERIAL_CHECK' "
                + "AND business_id=? ORDER BY task_id", String.class, PROJECT));
        assertEquals(2L, db.queryForObject("SELECT COUNT(*) FROM project_ai_check WHERE project_id=?",
            Long.class, PROJECT));
        assertTrue(db.queryForObject(
            "SELECT material_snapshot FROM project_ai_check WHERE project_id=? ORDER BY id LIMIT 1",
            String.class, PROJECT).contains("\"requirementsSha256\""));
        assertEquals("[996001]", db.queryForObject(
            "SELECT JSON_EXTRACT(request_payload,'$.materialVersionIds') FROM agent_task_log "
                + "WHERE business_id=? ORDER BY task_id LIMIT 1", String.class, PROJECT));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM agent_task_log WHERE business_id=? "
            + "AND (execute_status<>'SUCCESS' OR active_marker IS NOT NULL)", Long.class, PROJECT));
    }

    @Test
    void technicalFailureKeepsTaskButRollsBackBusinessResult() {
        when(aiService.checkMaterialMeasured(anyString(), anyString()))
            .thenThrow(new IllegalStateException("simulated failure"));
        var task = agentService.checkMaterial(PROJECT);
        agentService.executeMaterialTask(task.taskId());
        assertEquals("FAILED/NONE", db.queryForObject(
            "SELECT CONCAT(execute_status,'/',result_origin) FROM agent_task_log WHERE business_id=?",
            String.class, PROJECT));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM project_ai_check WHERE project_id=?",
            Long.class, PROJECT));
    }

    @Test
    void databaseAllowsOneActiveTaskButMultipleTerminalAttempts() {
        String insert = "INSERT INTO agent_task_log(tool_name,business_type,business_id,execute_status,"
            + "result_origin,active_marker) VALUES ('checkMaterialTool','MATERIAL_CHECK',?,'RUNNING','NONE',1)";
        db.update(insert, PROJECT);
        assertThrows(DataIntegrityViolationException.class, () -> db.update(insert, PROJECT));
        db.update("UPDATE agent_task_log SET execute_status='FAILED',active_marker=NULL "
            + "WHERE business_type='MATERIAL_CHECK' AND business_id=?", PROJECT);
        db.update(insert, PROJECT);
        assertEquals(2L, db.queryForObject("SELECT COUNT(*) FROM agent_task_log WHERE business_id=?",
            Long.class, PROJECT));
    }

    @Test
    void pendingIsDurableAndClaimedOnlyOnce() {
        when(aiService.checkMaterialMeasured(anyString(), anyString())).thenReturn(
            new AiService.CallResult<>(new AiCheckResponse("pass", "通过"), 1, true, false));
        var accepted = agentService.checkMaterial(PROJECT);
        assertEquals("PENDING", accepted.status());
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM project_ai_check WHERE project_id=?", Long.class, PROJECT));
        assertThrows(BusinessException.class, () -> agentService.checkMaterial(PROJECT));
        agentService.executeMaterialTask(accepted.taskId());
        agentService.executeMaterialTask(accepted.taskId());
        assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM project_ai_check WHERE project_id=?", Long.class, PROJECT));
    }

    @Test
    void concurrentAcceptRequestsLeaveExactlyOneActiveMaterialTask() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var calls = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                var principal = new LoginUser(USER, "phase52-student", "Phase52", "student");
                SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null,
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
                try {
                    start.await();
                    try { return agentService.checkMaterial(PROJECT); }
                    catch (BusinessException rejected) { return rejected; }
                } finally {
                    SecurityContextHolder.clearContext();
                }
            })).toList();
            start.countDown();
            var results = calls.stream().map(call -> {
                try { return call.get(10, TimeUnit.SECONDS); }
                catch (Exception error) { throw new RuntimeException(error); }
            }).toList();
            assertEquals(1L, results.stream().filter(
                com.eliza.aicompetition.dto.agent.MaterialCheckTaskResponse.class::isInstance).count());
            assertEquals(1L, results.stream().filter(BusinessException.class::isInstance).count());
            assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM agent_task_log "
                + "WHERE business_type='MATERIAL_CHECK' AND business_id=? AND active_marker=1",
                Long.class, PROJECT));
            assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM project_ai_check WHERE project_id=?",
                Long.class, PROJECT));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void uploadedVersionDuringModelCallCannotBecomeCurrentResult() {
        when(aiService.checkMaterialMeasured(anyString(), anyString())).thenAnswer(invocation -> {
            db.update("UPDATE project_material SET current_version_id=NULL WHERE material_id=?", MATERIAL);
            db.update("INSERT INTO project_material(material_id,project_id,requirement_id,file_id,version_no,"
                + "current_version_id,submit_status,submitted_at) VALUES (?,?,?,?,?,?,?,NOW())",
                MATERIAL + 1, PROJECT, REQUIREMENT, FILE, 2, MATERIAL + 1, "submitted");
            return new AiService.CallResult<>(new AiCheckResponse("pass", "旧材料通过"), 1, true, false);
        });
        var accepted = agentService.checkMaterial(PROJECT);
        agentService.executeMaterialTask(accepted.taskId());
        assertEquals("FAILED/NONE", db.queryForObject(
            "SELECT CONCAT(execute_status,'/',result_origin) FROM agent_task_log WHERE task_id=?",
            String.class, accepted.taskId()));
        assertEquals("INPUT_CHANGED", db.queryForObject("SELECT error_category FROM agent_task_log WHERE task_id=?",
            String.class, accepted.taskId()));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM project_ai_check WHERE project_id=?", Long.class, PROJECT));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM notify_message WHERE project_id=?", Long.class, PROJECT));
        db.update("DELETE FROM project_material WHERE material_id=?", MATERIAL + 1);
    }

    @Test
    void activeRequirementChangeDuringModelCallCannotCommit() {
        when(aiService.checkMaterialMeasured(anyString(), anyString())).thenAnswer(invocation -> {
            db.update("UPDATE material_requirement SET status='INACTIVE' WHERE requirement_id=?", REQUIREMENT);
            return new AiService.CallResult<>(new AiCheckResponse("warning", "旧风险"), 1, true, false);
        });
        var accepted = agentService.checkMaterial(PROJECT);
        agentService.executeMaterialTask(accepted.taskId());
        assertEquals("FAILED", db.queryForObject("SELECT execute_status FROM agent_task_log WHERE task_id=?",
            String.class, accepted.taskId()));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM notify_message WHERE project_id=?", Long.class, PROJECT));
    }

    @Test
    void projectStateChangeDuringModelCallCannotCommit() {
        when(aiService.checkMaterialMeasured(anyString(), anyString())).thenAnswer(invocation -> {
            db.update("UPDATE competition_project SET status='UNDER_REVIEW' WHERE project_id=?", PROJECT);
            return new AiService.CallResult<>(new AiCheckResponse("pass", "旧结论"), 1, true, false);
        });
        var accepted = agentService.checkMaterial(PROJECT);
        agentService.executeMaterialTask(accepted.taskId());
        assertEquals("FAILED", db.queryForObject("SELECT execute_status FROM agent_task_log WHERE task_id=?",
            String.class, accepted.taskId()));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM project_ai_check WHERE project_id=?", Long.class, PROJECT));
    }

    @Test
    void timeoutRejectsLateModelResult() {
        when(aiService.checkMaterialMeasured(anyString(), anyString())).thenAnswer(invocation -> {
            db.update("UPDATE agent_task_log SET created_at=DATE_SUB(NOW(), INTERVAL 20 MINUTE) "
                + "WHERE business_type='MATERIAL_CHECK' AND business_id=?", PROJECT);
            Long taskId = db.queryForObject("SELECT task_id FROM agent_task_log WHERE business_type='MATERIAL_CHECK' "
                + "AND business_id=?", Long.class, PROJECT);
            agentService.timeoutMaterialTask(taskId);
            return new AiService.CallResult<>(new AiCheckResponse("warning", "晚到风险"), 1, true, false);
        });
        var accepted = agentService.checkMaterial(PROJECT);
        agentService.executeMaterialTask(accepted.taskId());
        assertEquals("TIMEOUT/NONE", db.queryForObject(
            "SELECT CONCAT(execute_status,'/',result_origin) FROM agent_task_log WHERE task_id=?",
            String.class, accepted.taskId()));
        assertEquals("TASK_TIMEOUT", db.queryForObject("SELECT error_category FROM agent_task_log WHERE task_id=?",
            String.class, accepted.taskId()));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM project_ai_check WHERE project_id=?", Long.class, PROJECT));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM notify_message WHERE project_id=?", Long.class, PROJECT));
    }

    @Test
    void pendingAndRunningRecoveryConverge() {
        var pending = agentService.checkMaterial(PROJECT);
        db.update("UPDATE agent_task_log SET created_at=DATE_SUB(NOW(), INTERVAL 20 MINUTE) WHERE task_id=?",
            pending.taskId());
        agentService.timeoutMaterialTask(pending.taskId());
        assertEquals("TIMEOUT", agentService.findMaterialTask(pending.taskId()).status());

        var abandoned = agentService.checkMaterial(PROJECT);
        db.update("UPDATE agent_task_log SET execute_status='RUNNING',started_at=NOW() WHERE task_id=?",
            abandoned.taskId());
        agentService.failAbandonedMaterialTask(abandoned.taskId());
        assertEquals("FAILED", agentService.findMaterialTask(abandoned.taskId()).status());
        assertEquals(2, agentService.findMaterialTask(abandoned.taskId()).attempt());
    }

    @Test
    void expiredRunningMaterialRecoveryTimesOutAndReleasesActiveMarker() {
        var task = agentService.checkMaterial(PROJECT);
        db.update("UPDATE agent_task_log SET execute_status='RUNNING',started_at=DATE_SUB(NOW(), INTERVAL 20 MINUTE),"
            + "created_at=DATE_SUB(NOW(), INTERVAL 20 MINUTE) WHERE task_id=?", task.taskId());
        agentService.failAbandonedMaterialTask(task.taskId());
        assertEquals("TIMEOUT", agentService.findMaterialTask(task.taskId()).status());
        assertEquals(0L, db.queryForObject(
            "SELECT COUNT(*) FROM agent_task_log WHERE task_id=? AND active_marker=1", Long.class, task.taskId()));
        assertEquals(0L, db.queryForObject(
            "SELECT COUNT(*) FROM project_ai_check WHERE project_id=?", Long.class, PROJECT));
    }

    @Test
    void anotherStudentCannotQueryMaterialTask() {
        var accepted = agentService.checkMaterial(PROJECT);
        var outsider = new LoginUser(USER + 1, "outsider", "Outsider", "student");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            outsider, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        assertThrows(BusinessException.class, () -> agentService.findMaterialTask(accepted.taskId()));
        assertThrows(BusinessException.class, () -> agentService.latestMaterialTask(PROJECT));
    }

    @Test
    void apiAcceptsBeforeBlockedModelFinishes() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(aiService.checkMaterialMeasured(anyString(), anyString())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return new AiService.CallResult<>(new AiCheckResponse("pass", "通过"), 1, true, false);
        });
        var auth = SecurityContextHolder.getContext().getAuthentication();
        long start = System.nanoTime();
        var response = mvc.perform(post("/agent/check-material/{id}", PROJECT).with(authentication(auth)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.taskId").isNumber()).andReturn();
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 3000);
        long taskId = json.readTree(response.getResponse().getContentAsString()).path("data").path("taskId").asLong();
        try {
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertEquals("RUNNING", db.queryForObject(
                "SELECT execute_status FROM agent_task_log WHERE task_id=?", String.class, taskId));
        } finally {
            release.countDown();
        }
        for (int i = 0; i < 100 && "RUNNING".equals(db.queryForObject(
                "SELECT execute_status FROM agent_task_log WHERE task_id=?", String.class, taskId)); i++)
            Thread.sleep(20);
        assertEquals("SUCCESS", db.queryForObject(
            "SELECT execute_status FROM agent_task_log WHERE task_id=?", String.class, taskId));
    }

    @Test
    void rejectedDispatchRemainsPendingUntilScanRecoversIt() throws Exception {
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        aiExecutor.execute(() -> {
            occupied.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        });
        assertTrue(occupied.await(3, TimeUnit.SECONDS));
        aiExecutor.execute(() -> {});
        when(aiService.checkMaterialMeasured(anyString(), anyString())).thenReturn(
            new AiService.CallResult<>(new AiCheckResponse("pass", "通过"), 1, true, false));
        var accepted = agentService.checkMaterial(PROJECT);
        long start = System.nanoTime();
        dispatcher.dispatchMaterial(accepted.taskId());
        assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1000);
        assertEquals("PENDING", db.queryForObject(
            "SELECT execute_status FROM agent_task_log WHERE task_id=?", String.class, accepted.taskId()));
        release.countDown();
        for (int i = 0; i < 100 && !aiExecutor.getThreadPoolExecutor().getQueue().isEmpty(); i++) Thread.sleep(20);
        dispatcher.scan();
        for (int i = 0; i < 100 && !"SUCCESS".equals(db.queryForObject(
                "SELECT execute_status FROM agent_task_log WHERE task_id=?", String.class, accepted.taskId())); i++)
            Thread.sleep(20);
        assertEquals("SUCCESS", db.queryForObject(
            "SELECT execute_status FROM agent_task_log WHERE task_id=?", String.class, accepted.taskId()));
    }
}
