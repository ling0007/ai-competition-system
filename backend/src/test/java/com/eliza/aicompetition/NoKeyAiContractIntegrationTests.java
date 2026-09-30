package com.eliza.aicompetition;

import com.eliza.aicompetition.security.LoginUser;
import com.eliza.aicompetition.service.RedisService;
import com.eliza.aicompetition.service.NoticeService;
import com.eliza.aicompetition.service.NoticeAiTaskDispatcher;
import com.eliza.aicompetition.service.AgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "llm.api.key=")
@AutoConfigureMockMvc
@Transactional
class NoKeyAiContractIntegrationTests extends IsolatedDatabaseSpringTest {
    private static final long ADMIN = 997001L;
    private static final long STUDENT = 997002L;
    private static final long NOTICE = 997003L;
    private static final long PROJECT = 997004L;
    private static final long REQUIREMENT = 997005L;
    private static final long FILE = 997006L;
    private static final long MATERIAL = 997007L;

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate db;
    @MockitoBean private RedisService redisService;
    @MockitoBean private NoticeAiTaskDispatcher taskDispatcher;
    @Autowired private NoticeService noticeService;
    @Autowired private AgentService agentService;

    @BeforeEach
    void seed() {
        db.update("INSERT INTO sys_user(user_id,username,password,real_name,role) VALUES "
            + "(?,?,?,?,?),(?,?,?,?,?)", ADMIN, "no-key-admin", "{noop}pw", "Admin", "admin",
            STUDENT, "no-key-student", "{noop}pw", "Student", "student");
        db.update("INSERT INTO competition_notice(notice_id,title,created_by,raw_text,parse_status,publish_status) "
            + "VALUES (?,?,?,?,?,?)", NOTICE, "No-key notice", ADMIN, "通知：请提交材料", "DRAFT", "DRAFT");
        db.update("INSERT INTO competition_project(project_id,notice_id,leader_id,project_name,status) "
            + "VALUES (?,?,?,?,?)", PROJECT, NOTICE, STUDENT, "No-key project", "DRAFT");
        db.update("INSERT INTO project_member(project_id,user_id,member_role) VALUES (?,?,?)",
            PROJECT, STUDENT, "leader");
        db.update("INSERT INTO material_requirement(requirement_id,notice_id,requirement_name,is_required,status) "
            + "VALUES (?,?,?,?,?)", REQUIREMENT, NOTICE, "申报书", 1, "ACTIVE");
        byte[] content = "project material".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        db.update("INSERT INTO file_asset(file_id,biz_type,file_name,file_ext,file_size,file_blob,uploaded_by) "
            + "VALUES (?,?,?,?,?,?,?)", FILE, "material", "proposal.txt", "txt", content.length, content, STUDENT);
        db.update("INSERT INTO project_material(material_id,project_id,requirement_id,file_id,version_no,"
            + "current_version_id,submit_status,submitted_at) VALUES (?,?,?,?,?,?,?,NOW())",
            MATERIAL, PROJECT, REQUIREMENT, FILE, 1, MATERIAL, "submitted");
    }

    @Test
    void noKeyProducesFallbackDraftAndReviewWithQueryableTaskOrigin() throws Exception {
        mvc.perform(post("/notice/parse/{id}", NOTICE).with(authentication(auth(ADMIN, "admin"))))
            .andExpect(status().isOk());
        Long taskId = db.queryForObject("SELECT task_id FROM agent_task_log WHERE business_type='NOTICE_PARSE' AND business_id=?", Long.class, NOTICE);
        noticeService.executeParseTask(taskId);
        mvc.perform(get("/notice/{id}/parse-draft", NOTICE).with(authentication(auth(ADMIN, "admin"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));

        mvc.perform(post("/agent/check-material/{id}", PROJECT).with(authentication(auth(STUDENT, "student"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));
        Long materialTaskId = db.queryForObject("SELECT task_id FROM agent_task_log "
            + "WHERE business_type='MATERIAL_CHECK' AND business_id=?", Long.class, PROJECT);
        agentService.executeMaterialTask(materialTaskId);

        assertEquals(2L, db.queryForObject("SELECT COUNT(*) FROM agent_task_log "
            + "WHERE result_origin='FALLBACK' AND execute_status='SUCCESS'", Long.class));
        assertEquals(2L, db.queryForObject("SELECT COUNT(*) FROM agent_task_log "
            + "WHERE degradation_reason='API_KEY_MISSING' AND error_category IS NULL "
            + "AND JSON_EXTRACT(observation_payload,'$.llmAttempted')=false", Long.class));
        assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM project_ai_check WHERE project_id=? "
            + "AND result='WARNING'", Long.class, PROJECT));
    }

    private static UsernamePasswordAuthenticationToken auth(long id, String role) {
        var user = new LoginUser(id, "no-key-" + role, role, role);
        return new UsernamePasswordAuthenticationToken(user, null,
            List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
    }
}
