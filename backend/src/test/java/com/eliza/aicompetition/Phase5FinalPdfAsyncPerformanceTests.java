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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Paid opt-in measurement: exact files and model used by the synchronous PDF baseline. */
@EnabledIfSystemProperty(named = "phase5.final.pdf.performance", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
class Phase5FinalPdfAsyncPerformanceTests extends IsolatedDatabaseSpringTest {
    private static final long ADMIN = 996201L;
    private static final long TEXT_FILE = 996202L;
    private static final long SCANNED_FILE = 996203L;
    private static final long TEXT_NOTICE = 996210L;
    private static final long SCANNED_NOTICE = 996220L;
    private static final String TEXT_PATH = "../docs/testdata/文字版pdfCIMC华东二赛区.pdf";
    private static final String SCANNED_PATH = "../docs/testdata/关于举办第十九届全国大学生软件创新大赛的参赛通知.pdf";

    @Autowired private JdbcTemplate db;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @BeforeEach
    void seed() throws Exception {
        assertTrue(System.getenv("DASHSCOPE_API_KEY") != null
            && !System.getenv("DASHSCOPE_API_KEY").isBlank(), "Real model key required for opt-in measurement");
        db.update("INSERT INTO sys_user(user_id,username,password,real_name,role) VALUES (?,?,?,?,?)",
            ADMIN, "phase5-final-pdf-admin", "{noop}" + UUID.randomUUID(), "PDF benchmark", "admin");
        insertFile(TEXT_FILE, TEXT_PATH, "text-notice.pdf");
        insertFile(SCANNED_FILE, SCANNED_PATH, "scanned-notice.pdf");
        for (int i = 0; i < 3; i++) {
            insertNotice(TEXT_NOTICE + i, TEXT_FILE);
            insertNotice(SCANNED_NOTICE + i, SCANNED_FILE);
        }
    }

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM notice_parse_draft WHERE notice_id BETWEEN ? AND ? "
            + "OR notice_id BETWEEN ? AND ?", TEXT_NOTICE, TEXT_NOTICE + 2,
            SCANNED_NOTICE, SCANNED_NOTICE + 2);
        db.update("DELETE FROM agent_task_log WHERE business_type='NOTICE_PARSE' "
            + "AND (business_id BETWEEN ? AND ? OR business_id BETWEEN ? AND ?)",
            TEXT_NOTICE, TEXT_NOTICE + 2, SCANNED_NOTICE, SCANNED_NOTICE + 2);
        db.update("DELETE FROM competition_notice WHERE notice_id BETWEEN ? AND ? "
            + "OR notice_id BETWEEN ? AND ?", TEXT_NOTICE, TEXT_NOTICE + 2,
            SCANNED_NOTICE, SCANNED_NOTICE + 2);
        db.update("DELETE FROM file_asset WHERE file_id IN (?,?)", TEXT_FILE, SCANNED_FILE);
        db.update("DELETE FROM sys_user WHERE user_id=?", ADMIN);
    }

    @Test
    void sameTextPdfProducesThreeAsyncModelSamples() throws Exception {
        for (int i = 0; i < 3; i++) measure("TEXT_PDF", TEXT_NOTICE + i);
    }

    @Test
    void sameScannedPdfProducesThreeAsyncModelSamples() throws Exception {
        for (int i = 0; i < 3; i++) measure("SCANNED_PDF_3_PAGES", SCANNED_NOTICE + i);
    }

    private void measure(String kind, long noticeId) throws Exception {
        var user = new LoginUser(ADMIN, "phase5-final-pdf-admin", "PDF benchmark", "admin");
        var auth = new UsernamePasswordAuthenticationToken(user, null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        System.out.println("phase5_pdf_sample_begin kind=" + kind + " noticeId=" + noticeId);
        long start = System.nanoTime();
        var accepted = mvc.perform(post("/notice/parse/{id}", noticeId)
                .with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn();
        long acceptedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        long taskId = json.readTree(accepted.getResponse().getContentAsString())
            .path("data").path("taskId").asLong();
        assertTrue(acceptedMs < 3000, "Accept must not await PDF extraction or model");
        String status = "PENDING";
        for (int attempt = 0; attempt < 1500; attempt++) {
            status = db.queryForObject("SELECT execute_status FROM agent_task_log WHERE task_id=?",
                String.class, taskId);
            if (List.of("SUCCESS", "FAILED", "TIMEOUT").contains(status)) break;
            Thread.sleep(200);
        }
        assertEquals("SUCCESS", status, "taskId=" + taskId);
        var task = db.queryForMap("SELECT result_origin,active_marker,created_at,started_at,finished_at "
            + "FROM agent_task_log WHERE task_id=?", taskId);
        assertEquals("MODEL", task.get("result_origin"));
        assertEquals(null, task.get("active_marker"));
        assertNotNull(task.get("started_at"));
        assertNotNull(task.get("finished_at"));
        assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM notice_parse_draft "
            + "WHERE notice_id=? AND status='PENDING'", Long.class, noticeId));
        assertEquals("DRAFT", db.queryForObject("SELECT publish_status FROM competition_notice "
            + "WHERE notice_id=?", String.class, noticeId));
        long queueWaitMs = db.queryForObject("SELECT TIMESTAMPDIFF(MICROSECOND,created_at,started_at) "
            + "FROM agent_task_log WHERE task_id=?", Long.class, taskId) / 1000;
        long taskTotalMs = db.queryForObject("SELECT TIMESTAMPDIFF(MICROSECOND,created_at,finished_at) "
            + "FROM agent_task_log WHERE task_id=?", Long.class, taskId) / 1000;
        System.out.println("phase5_pdf_sample_end kind=" + kind + " noticeId=" + noticeId
            + " taskId=" + taskId + " acceptedMs=" + acceptedMs + " queueWaitMs=" + queueWaitMs
            + " taskTotalMs=" + taskTotalMs + " resultOrigin=MODEL");
    }

    private void insertFile(long fileId, String path, String name) throws Exception {
        byte[] pdf = Files.readAllBytes(Path.of(path));
        db.update("INSERT INTO file_asset(file_id,biz_type,file_name,file_ext,file_size,file_blob,uploaded_by) "
                + "VALUES (?,?,?,?,?,?,?)", fileId, "notice", name, "pdf", pdf.length, pdf, ADMIN);
    }

    private void insertNotice(long noticeId, long fileId) {
        db.update("INSERT INTO competition_notice(notice_id,title,notice_file_id,created_by,"
                + "parse_status,publish_status,version) VALUES (?,?,?,?,?,?,0)",
            noticeId, "Phase 5 same-input PDF measurement", fileId, ADMIN, "DRAFT", "DRAFT");
    }
}
