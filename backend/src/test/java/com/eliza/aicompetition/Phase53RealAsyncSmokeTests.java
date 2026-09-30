package com.eliza.aicompetition;

import com.eliza.aicompetition.security.LoginUser;
import com.eliza.aicompetition.service.RedisService;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Paid external smoke, never part of stable default tests. */
@EnabledIfSystemProperty(named = "phase53.real.async", matches = "true")
@SpringBootTest
@AutoConfigureMockMvc
class Phase53RealAsyncSmokeTests extends IsolatedDatabaseSpringTest {
    private static final long ADMIN = 998101L;
    private static final long NOTICE = 998102L;
    @Autowired private JdbcTemplate db;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private RedisService redis;

    @BeforeEach
    void seed() {
        if (System.getenv("DASHSCOPE_API_KEY") == null || System.getenv("DASHSCOPE_API_KEY").isBlank()) {
            throw new IllegalStateException("DASHSCOPE_API_KEY is required for opt-in smoke");
        }
        db.update("INSERT INTO sys_user(user_id,username,password,real_name,role) VALUES (?,?,?,?,?)",
            ADMIN, "phase53-admin", "{noop}pw", "Admin", "admin");
        db.update("INSERT INTO competition_notice(notice_id,title,created_by,raw_text,parse_status,publish_status,version) "
                + "VALUES (?,?,?,?,?,?,0)", NOTICE, "Phase 5.3 async smoke", ADMIN,
            "关于举办软件创新大赛的通知：报名截止时间为2027-06-30 18:00，须提交项目申报书。",
            "DRAFT", "DRAFT");
    }

    @AfterEach
    void cleanup() {
        db.update("DELETE FROM notice_parse_draft WHERE notice_id=?", NOTICE);
        db.update("DELETE FROM agent_task_log WHERE business_type='NOTICE_PARSE' AND business_id=?", NOTICE);
        db.update("DELETE FROM competition_notice WHERE notice_id=?", NOTICE);
        db.update("DELETE FROM sys_user WHERE user_id=?", ADMIN);
    }

    @Test
    void realModelCompletesThroughAsyncApi() throws Exception {
        var login = new LoginUser(ADMIN, "phase53-admin", "Admin", "admin");
        var auth = new UsernamePasswordAuthenticationToken(login, null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        long start = System.nanoTime();
        var accepted = mvc.perform(post("/notice/parse/{id}", NOTICE).with(authentication(auth)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn();
        long requestMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue(requestMs < 3000, "HTTP acceptance must not wait for model IO");
        long taskId = json.readTree(accepted.getResponse().getContentAsString()).path("data").path("taskId").asLong();
        String state = "PENDING";
        for (int i = 0; i < 180; i++) {
            var task = mvc.perform(get("/notice/parse-tasks/{id}", taskId).with(authentication(auth)))
                .andExpect(status().isOk()).andReturn();
            state = json.readTree(task.getResponse().getContentAsString()).path("data").path("status").asText();
            if (List.of("SUCCESS", "FAILED", "TIMEOUT").contains(state)) break;
            Thread.sleep(1000);
        }
        assertEquals("SUCCESS", state);
        assertEquals("MODEL", db.queryForObject("SELECT result_origin FROM agent_task_log WHERE task_id=?", String.class, taskId));
        mvc.perform(get("/notice/{id}/parse-draft", NOTICE).with(authentication(auth)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"));
        System.out.println("phase53_smoke requestMs=" + requestMs + " taskId=" + taskId);
    }
}
