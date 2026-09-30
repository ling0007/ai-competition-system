package com.eliza.aicompetition;

import com.eliza.aicompetition.security.LoginUser;
import com.eliza.aicompetition.service.RedisService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit opt-in; uses a real paid DashScope key and the isolated MySQL schema. */
@EnabledIfSystemProperty(named = "phase52.real.ocr", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
class Phase52RealOcrBaselineTests extends IsolatedDatabaseSpringTest {
    private static final long ADMIN = 998001L;
    private static final long FILE = 998002L;
    private static final long FIRST_NOTICE = 998010L;
    private static final String PDF = "../docs/testdata/关于举办第十九届全国大学生软件创新大赛的参赛通知.pdf";

    @Autowired private JdbcTemplate db;
    @Autowired private MockMvc mvc;
    @MockitoBean private RedisService redisService;

    @BeforeEach
    void seed() throws Exception {
        if (System.getenv("DASHSCOPE_API_KEY") == null || System.getenv("DASHSCOPE_API_KEY").isBlank()) {
            throw new IllegalStateException("DASHSCOPE_API_KEY must be provided for opt-in OCR baseline");
        }
        byte[] pdf = Files.readAllBytes(Path.of(PDF));
        db.update("INSERT INTO sys_user(user_id,username,password,real_name,role) VALUES (?,?,?,?,?)",
            ADMIN, "phase52-ocr-admin", "{noop}pw", "Phase52 OCR", "admin");
        db.update("INSERT INTO file_asset(file_id,biz_type,file_name,file_ext,file_size,file_blob,uploaded_by) "
            + "VALUES (?,?,?,?,?,?,?)", FILE, "notice", "ocr-baseline.pdf", "pdf", pdf.length, pdf, ADMIN);
        for (int i = 0; i < 3; i++) {
            db.update("INSERT INTO competition_notice(notice_id,title,notice_file_id,created_by,"
                + "parse_status,publish_status) VALUES (?,?,?,?,?,?)",
                FIRST_NOTICE + i, "Phase52 OCR baseline", FILE, ADMIN, "DRAFT", "DRAFT");
        }
    }

    @AfterEach
    void cleanUp() {
        db.update("DELETE FROM agent_task_log WHERE business_type='NOTICE_PARSE' AND business_id BETWEEN ? AND ?",
            FIRST_NOTICE, FIRST_NOTICE + 2);
        db.update("DELETE FROM notice_parse_draft WHERE notice_id BETWEEN ? AND ?",
            FIRST_NOTICE, FIRST_NOTICE + 2);
        db.update("DELETE FROM competition_notice WHERE notice_id BETWEEN ? AND ?",
            FIRST_NOTICE, FIRST_NOTICE + 2);
        db.update("DELETE FROM file_asset WHERE file_id=?", FILE);
        db.update("DELETE FROM sys_user WHERE user_id=?", ADMIN);
    }

    @Test
    void sameScannedPdfProducesThreeRealCurrentModelSamples() throws Exception {
        var user = new LoginUser(ADMIN, "phase52-ocr-admin", "Phase52 OCR", "admin");
        var auth = new UsernamePasswordAuthenticationToken(user, null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        for (int i = 0; i < 3; i++) {
            long noticeId = FIRST_NOTICE + i;
            mvc.perform(post("/notice/parse/{id}", noticeId).with(authentication(auth)))
                .andExpect(status().isOk());
            for (int attempt = 0; attempt < 360; attempt++) {
                String state = db.queryForObject("SELECT execute_status FROM agent_task_log "
                    + "WHERE business_type='NOTICE_PARSE' AND business_id=?", String.class, noticeId);
                if ("SUCCESS".equals(state) || "FAILED".equals(state) || "TIMEOUT".equals(state)) break;
                Thread.sleep(1000);
            }
            assertEquals("SUCCESS/MODEL", db.queryForObject(
                "SELECT CONCAT(execute_status,'/',result_origin) FROM agent_task_log "
                    + "WHERE business_type='NOTICE_PARSE' AND business_id=?",
                String.class, noticeId));
        }
    }
}
