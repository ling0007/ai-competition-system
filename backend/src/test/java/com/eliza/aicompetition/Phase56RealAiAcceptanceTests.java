package com.eliza.aicompetition;

import com.eliza.aicompetition.security.LoginUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Paid, opt-in same-model smoke on a dedicated MySQL schema and real Redis. */
@EnabledIfSystemProperty(named = "phase56.real.ai", matches = "true")
@SpringBootTest(properties = "notice.ai.recovery-scan-ms=600000")
@AutoConfigureMockMvc
class Phase56RealAiAcceptanceTests extends IsolatedDatabaseSpringTest {
    private static final long ADMIN = 996101L;
    private static final long STUDENT = 996102L;
    private static final long NOTICE_FILE = 996103L;
    private static final long FIRST_NOTICE = 996104L;
    private static final long SECOND_NOTICE = 996105L;
    private static final long MATERIAL_NOTICE = 996106L;
    private static final long PROJECT = 996107L;
    private static final long FIRST_REQUIREMENT = 996108L;
    private static final long SECOND_REQUIREMENT = 996109L;

    @Autowired private JdbcTemplate db;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @BeforeEach
    void seed() throws Exception {
        assertTrue(System.getenv("DASHSCOPE_API_KEY") != null
            && !System.getenv("DASHSCOPE_API_KEY").isBlank(), "Opt-in real AI requires a key");
        byte[] notice = Files.readAllBytes(Path.of("../docs/testdata/manual-ai-notice.txt"));
        db.update("INSERT INTO sys_user(user_id,username,password,real_name,role) VALUES "
                + "(?,?,?,?,?),(?,?,?,?,?)", ADMIN, "phase56-admin", "{noop}pw", "Admin", "admin",
            STUDENT, "phase56-student", "{noop}pw", "Student", "student");
        db.update("INSERT INTO file_asset(file_id,biz_type,file_name,file_ext,file_size,file_blob,uploaded_by) "
                + "VALUES (?,?,?,?,?,?,?)", NOTICE_FILE, "notice", "manual-ai-notice.txt", "txt",
            notice.length, notice, ADMIN);
        for (long id : List.of(FIRST_NOTICE, SECOND_NOTICE)) {
            db.update("INSERT INTO competition_notice(notice_id,title,notice_file_id,created_by,"
                    + "parse_status,publish_status,version) VALUES (?,?,?,?,?,?,0)",
                id, "Phase56 same-input notice", NOTICE_FILE, ADMIN, "DRAFT", "DRAFT");
        }
        db.update("INSERT INTO competition_notice(notice_id,title,created_by,parse_status,publish_status) "
                + "VALUES (?,?,?,?,?)", MATERIAL_NOTICE, "Phase56 material notice", ADMIN,
            "PARSED", "PUBLISHED");
        db.update("INSERT INTO competition_project(project_id,notice_id,leader_id,project_name,status) "
                + "VALUES (?,?,?,?,?)", PROJECT, MATERIAL_NOTICE, STUDENT,
            "Phase56 material project", "DRAFT");
        db.update("INSERT INTO project_member(project_id,user_id,member_role) VALUES (?,?,?)",
            PROJECT, STUDENT, "leader");
        db.update("INSERT INTO material_requirement(requirement_id,notice_id,requirement_name,is_required,status) "
                + "VALUES (?,?,?,?,?),(?,?,?,?,?)", FIRST_REQUIREMENT, MATERIAL_NOTICE,
            "项目申报书", 1, "ACTIVE", SECOND_REQUIREMENT, MATERIAL_NOTICE,
            "指导教师意见表", 1, "ACTIVE");
    }

    @AfterEach
    void cleanup() {
        List<Long> uploadedFiles = db.queryForList("SELECT file_id FROM project_material "
            + "WHERE project_id=? AND file_id IS NOT NULL", Long.class, PROJECT);
        db.update("DELETE FROM notify_message WHERE project_id=?", PROJECT);
        db.update("DELETE FROM review_record WHERE project_id=?", PROJECT);
        db.update("DELETE FROM project_ai_check WHERE project_id=?", PROJECT);
        db.update("DELETE FROM agent_task_log WHERE business_type='MATERIAL_CHECK' AND business_id=?", PROJECT);
        db.update("DELETE FROM agent_task_log WHERE business_type='NOTICE_PARSE' AND business_id IN (?,?)",
            FIRST_NOTICE, SECOND_NOTICE);
        db.update("DELETE FROM notice_parse_draft WHERE notice_id IN (?,?)", FIRST_NOTICE, SECOND_NOTICE);
        db.update("DELETE FROM project_material WHERE project_id=?", PROJECT);
        for (Long fileId : uploadedFiles) db.update("DELETE FROM file_asset WHERE file_id=?", fileId);
        db.update("DELETE FROM material_requirement WHERE notice_id=?", MATERIAL_NOTICE);
        db.update("DELETE FROM project_member WHERE project_id=?", PROJECT);
        db.update("DELETE FROM competition_project WHERE project_id=?", PROJECT);
        db.update("DELETE FROM competition_notice WHERE notice_id IN (?,?,?)",
            FIRST_NOTICE, SECOND_NOTICE, MATERIAL_NOTICE);
        db.update("DELETE FROM file_asset WHERE file_id=?", NOTICE_FILE);
        db.update("DELETE FROM sys_user WHERE user_id IN (?,?)", ADMIN, STUDENT);
    }

    @Test
    void sameInputNoticeAndUploadedMaterialReachRealModelResults() throws Exception {
        for (long noticeId : List.of(FIRST_NOTICE, SECOND_NOTICE)) {
            long start = System.nanoTime();
            var accepted = mvc.perform(post("/notice/parse/{id}", noticeId)
                    .with(authentication(auth(ADMIN, "admin"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
            long acceptedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            long taskId = json.readTree(accepted.getResponse().getContentAsString())
                .path("data").path("taskId").asLong();
            assertTrue(acceptedMs < 3000, "Request thread must not wait for DashScope");
            long visibleMs = pollTerminal("/notice/parse-tasks/" + taskId, taskId, auth(ADMIN, "admin"));
            assertModelTask(taskId);
            Timestamp deadline = db.queryForObject("SELECT ai_deadline FROM notice_parse_draft "
                + "WHERE notice_id=? AND status='PENDING'", Timestamp.class, noticeId);
            assertNotNull(deadline);
            assertEquals(LocalDateTime.of(2027, 6, 30, 18, 0), deadline.toLocalDateTime());
            mvc.perform(get("/notice/{id}/parse-draft", noticeId)
                    .with(authentication(auth(ADMIN, "admin"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
            assertEquals("DRAFT", db.queryForObject("SELECT publish_status FROM competition_notice "
                + "WHERE notice_id=?", String.class, noticeId));
            assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM material_requirement WHERE notice_id=?",
                Long.class, noticeId));
            System.out.println("phase56_notice taskId=" + taskId + " acceptedMs=" + acceptedMs
                + " pollVisibleMs=" + visibleMs + " queueWaitMs=" + queueWait(taskId)
                + " taskTotalMs=" + taskTotal(taskId));
        }

        upload(FIRST_REQUIREMENT, "manual-project-proposal.txt");
        upload(SECOND_REQUIREMENT, "manual-advisor-opinion.txt");
        long start = System.nanoTime();
        var accepted = mvc.perform(post("/agent/check-material/{id}", PROJECT)
                .with(authentication(auth(STUDENT, "student"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn();
        long acceptedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        long taskId = json.readTree(accepted.getResponse().getContentAsString())
            .path("data").path("taskId").asLong();
        assertTrue(acceptedMs < 3000);
        long visibleMs = pollTerminal("/agent/material-tasks/" + taskId, taskId, auth(STUDENT, "student"));
        assertModelTask(taskId);
        String snapshot = db.queryForObject("SELECT request_payload FROM agent_task_log WHERE task_id=?",
            String.class, taskId);
        var parsed = json.readTree(snapshot);
        assertEquals(2, parsed.path("v").asInt());
        assertEquals(2, parsed.path("requirements").size());
        assertEquals(2, parsed.path("materialVersionIds").size());
        assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM project_ai_check WHERE agent_task_id=? "
            + "AND material_snapshot=?", Long.class, taskId, snapshot));
        mvc.perform(get("/agent/projects/{id}/checks", PROJECT)
                .with(authentication(auth(STUDENT, "student"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].stale").value(false));
        assertEquals("DRAFT", db.queryForObject("SELECT status FROM competition_project WHERE project_id=?",
            String.class, PROJECT));
        assertEquals(0L, db.queryForObject("SELECT COUNT(*) FROM review_record WHERE project_id=? "
            + "AND review_type <> 'ai'", Long.class, PROJECT));
        String result = db.queryForObject("SELECT result FROM project_ai_check WHERE agent_task_id=?",
            String.class, taskId);
        long messages = db.queryForObject("SELECT COUNT(*) FROM notify_message WHERE project_id=?",
            Long.class, PROJECT);
        assertEquals("WARNING".equals(result) ? 1L : 0L, messages);
        System.out.println("phase56_material taskId=" + taskId + " acceptedMs=" + acceptedMs
            + " pollVisibleMs=" + visibleMs + " queueWaitMs=" + queueWait(taskId)
            + " taskTotalMs=" + taskTotal(taskId) + " result=" + result);
    }

    private void upload(long requirementId, String filename) throws Exception {
        byte[] content = Files.readAllBytes(Path.of("../docs/testdata/" + filename));
        mvc.perform(multipart("/material/upload")
                .file(new MockMultipartFile("file", filename, "text/plain", content))
                .param("projectId", Long.toString(PROJECT))
                .param("requirementId", Long.toString(requirementId))
                .with(authentication(auth(STUDENT, "student"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
    }

    private long pollTerminal(String path, long taskId,
            UsernamePasswordAuthenticationToken auth) throws Exception {
        long start = System.nanoTime();
        for (int i = 0; i < 300; i++) {
            var response = mvc.perform(get(path).with(authentication(auth)))
                .andExpect(status().isOk()).andReturn();
            String status = json.readTree(response.getResponse().getContentAsString())
                .path("data").path("status").asText();
            if (List.of("SUCCESS", "FAILED", "TIMEOUT").contains(status)) {
                assertEquals("SUCCESS", status, "taskId=" + taskId);
                return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            }
            Thread.sleep(100);
        }
        fail("Task did not reach a terminal state: taskId=" + taskId);
        return -1;
    }

    private void assertModelTask(long taskId) {
        var row = db.queryForMap("SELECT execute_status,result_origin,active_marker,started_at,finished_at "
            + "FROM agent_task_log WHERE task_id=?", taskId);
        assertEquals("SUCCESS", row.get("execute_status"));
        assertEquals("MODEL", row.get("result_origin"));
        assertNull(row.get("active_marker"));
        assertNotNull(row.get("started_at"));
        assertNotNull(row.get("finished_at"));
    }

    private long queueWait(long taskId) {
        return db.queryForObject("SELECT TIMESTAMPDIFF(MICROSECOND,created_at,started_at) "
            + "FROM agent_task_log WHERE task_id=?", Long.class, taskId) / 1000;
    }

    private long taskTotal(long taskId) {
        return db.queryForObject("SELECT TIMESTAMPDIFF(MICROSECOND,created_at,finished_at) "
            + "FROM agent_task_log WHERE task_id=?", Long.class, taskId) / 1000;
    }

    private static UsernamePasswordAuthenticationToken auth(long id, String role) {
        var user = new LoginUser(id, "phase56-" + role, role, role);
        return new UsernamePasswordAuthenticationToken(user, null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
    }
}
