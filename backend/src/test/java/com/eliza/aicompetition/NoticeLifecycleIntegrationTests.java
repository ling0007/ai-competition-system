package com.eliza.aicompetition;

import com.eliza.aicompetition.dto.ai.AiParseResult;
import com.eliza.aicompetition.security.LoginUser;
import com.eliza.aicompetition.service.AiService;
import com.eliza.aicompetition.service.RedisService;
import com.eliza.aicompetition.service.NoticeService;
import com.eliza.aicompetition.service.NoticeAiTaskDispatcher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通知运营闭环的真实数据库验收。AI 与 Redis 属于外部依赖，在测试中替换；
 * Controller、Service、事务、Mapper 和 MySQL schema 均走真实调用链并在用例结束后回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class NoticeLifecycleIntegrationTests extends IsolatedDatabaseSpringTest {

    private static final long ADMIN_ID = 8101L;
    private static final long TEACHER_ID = 8102L;
    private static final long STUDENT_ID = 8103L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiService aiService;

    @MockitoBean
    private RedisService redisService;
    @MockitoBean private NoticeAiTaskDispatcher taskDispatcher;
    @Autowired private NoticeService noticeService;

    @BeforeEach
    void seedUsersAndExternalResults() {
        jdbcTemplate.update("INSERT INTO sys_user(user_id, username, password, real_name, role) VALUES "
                + "(?,?,?,?,?), (?,?,?,?,?), (?,?,?,?,?)",
            ADMIN_ID, "notice-lifecycle-admin", "{noop}pw", "通知管理员", "admin",
            TEACHER_ID, "notice-lifecycle-teacher", "{noop}pw", "通知教师", "teacher",
            STUDENT_ID, "notice-lifecycle-student", "{noop}pw", "通知学生", "student");

        when(aiService.parseNoticeMeasured(anyString())).thenReturn(new AiService.CallResult<>(new AiParseResult(
            "AI 解析后的竞赛通知",
            "创新创业学院",
            "2030-06-30 18:00",
            "全校学生团队",
            "请按期提交完整材料",
            List.of(new AiParseResult.AiMaterialRequirement("项目申报书", "按模板填写", true))
        ), 12, true, false));
    }

    @Test
    void adminCompletesNoticeLifecycleWhileReadVisibilityTracksPublishState() throws Exception {
        Authentication admin = auth(ADMIN_ID, "notice-lifecycle-admin", "admin", "ROLE_ADMIN");
        Authentication teacher = auth(TEACHER_ID, "notice-lifecycle-teacher", "teacher", "ROLE_TEACHER");
        Authentication student = auth(STUDENT_ID, "notice-lifecycle-student", "student", "ROLE_STUDENT");

        MvcResult uploadResult = mockMvc.perform(multipart("/notice/upload")
                .param("title", "待解析竞赛通知")
                .param("organizer", "测试主办方")
                .param("deadline", "2030-05-31T18:00:00")
                .param("targetGroup", "本科生团队")
                .param("rawText", "需提交项目申报书，截止时间以通知为准。")
                .with(authentication(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.noticeId").isNumber())
            .andReturn();

        JsonNode uploadBody = objectMapper.readTree(uploadResult.getResponse().getContentAsString());
        long noticeId = uploadBody.path("data").path("noticeId").asLong();

        mockMvc.perform(get("/notice/{id}", noticeId).with(authentication(student)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/notice/{id}", noticeId).with(authentication(teacher)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.publishStatus").value("DRAFT"));

        parseAndRun(noticeId, admin);
        assertEquals(1L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM agent_task_log WHERE business_type='NOTICE_PARSE' AND business_id=? "
                + "AND attempt_no=1 AND execute_status='SUCCESS' AND result_origin='MODEL' "
                + "AND active_marker IS NULL AND started_at IS NOT NULL AND finished_at IS NOT NULL",
            Long.class, noticeId));
        assertEquals(64, jdbcTemplate.queryForObject(
            "SELECT CHAR_LENGTH(JSON_UNQUOTE(JSON_EXTRACT(request_payload,'$.rawTextSha256'))) "
                + "FROM agent_task_log WHERE business_type='NOTICE_PARSE' AND business_id=?",
            Integer.class, noticeId));
        mockMvc.perform(get("/notice/{id}/parse-draft", noticeId).with(authentication(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andExpect(jsonPath("$.data.aiDeadline").value("2030-06-30T18:00:00"));

        String updatedDraft = """
            {
              "aiTitle":"管理员核对后的竞赛通知",
              "aiOrganizer":"创新创业学院",
              "aiDeadline":"2030-06-30T18:00:00",
              "aiTargetGroup":"全校学生团队",
              "aiKeyPoints":"人工已核对",
              "materials":[{"name":"项目申报书","description":"按模板填写并签字","isRequired":true}]
            }
            """;
        mockMvc.perform(put("/notice/{id}/parse-draft", noticeId)
                .contentType(APPLICATION_JSON)
                .content(updatedDraft)
                .with(authentication(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.aiTitle").value("管理员核对后的竞赛通知"));

        mockMvc.perform(post("/notice/{id}/confirm", noticeId)
                .contentType(APPLICATION_JSON)
                .content("{}")
                .with(authentication(admin)))
            .andExpect(status().isOk());
        mockMvc.perform(post("/notice/{id}/publish", noticeId).with(authentication(admin)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/notice/{id}", noticeId).with(authentication(student)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.title").value("管理员核对后的竞赛通知"))
            .andExpect(jsonPath("$.data.publishStatus").value("PUBLISHED"))
            .andExpect(jsonPath("$.data.materialRequirements[0].name").value("项目申报书"));

        mockMvc.perform(post("/notice/{id}/archive", noticeId).with(authentication(admin)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/notice/{id}", noticeId).with(authentication(student)))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/notice/{id}", noticeId).with(authentication(teacher)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.publishStatus").value("ARCHIVED"));
    }

    @Test
    void degradedParseCreatesUsableDraftButNotModelSuccess() throws Exception {
        when(aiService.parseNoticeMeasured(anyString())).thenReturn(new AiService.CallResult<>(
            new AiParseResult(null, null, null, null, "AI 暂不可用，请人工处理", List.of()),
            0, false, true));
        Authentication admin = auth(ADMIN_ID, "notice-lifecycle-admin", "admin", "ROLE_ADMIN");
        MvcResult created = mockMvc.perform(multipart("/notice/upload")
                .param("title", "降级通知")
                .param("rawText", "测试通知原文")
                .with(authentication(admin)))
            .andExpect(status().isOk()).andReturn();
        long noticeId = objectMapper.readTree(created.getResponse().getContentAsString())
            .path("data").path("noticeId").asLong();

        parseAndRun(noticeId, admin);
        mockMvc.perform(get("/notice/{id}/parse-draft", noticeId).with(authentication(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"));
        assertEquals("SUCCESS/FALLBACK", jdbcTemplate.queryForObject(
            "SELECT CONCAT(execute_status, '/', result_origin) FROM agent_task_log "
                + "WHERE business_type='NOTICE_PARSE' AND business_id=?",
            String.class, noticeId));
    }

    @Test
    void invalidAiDeadlineLeavesFailedTaskAndNoDraft() throws Exception {
        when(aiService.parseNoticeMeasured(anyString())).thenReturn(new AiService.CallResult<>(
            new AiParseResult("通知", "主办方", "2030-02-30 18:00", "学生", "摘要", List.of()),
            1, true, false));
        Authentication admin = auth(ADMIN_ID, "notice-lifecycle-admin", "admin", "ROLE_ADMIN");
        MvcResult created = mockMvc.perform(multipart("/notice/upload")
                .param("title", "无效日期通知")
                .param("rawText", "测试通知原文")
                .with(authentication(admin)))
            .andExpect(status().isOk()).andReturn();
        long noticeId = objectMapper.readTree(created.getResponse().getContentAsString())
            .path("data").path("noticeId").asLong();

        parseAndRun(noticeId, admin);
        assertEquals("FAILED", jdbcTemplate.queryForObject(
            "SELECT parse_status FROM competition_notice WHERE notice_id=?", String.class, noticeId));
        assertEquals("FAILED/NONE", jdbcTemplate.queryForObject(
            "SELECT CONCAT(execute_status,'/',result_origin) FROM agent_task_log "
                + "WHERE business_type='NOTICE_PARSE' AND business_id=?", String.class, noticeId));
        assertEquals(0L, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notice_parse_draft WHERE notice_id=?", Long.class, noticeId));
    }

    @Test
    void adminCanReplaceDraftAttachmentButNotParsedAttachment() throws Exception {
        Authentication admin = auth(ADMIN_ID, "notice-lifecycle-admin", "admin", "ROLE_ADMIN");
        Authentication student = auth(STUDENT_ID, "notice-lifecycle-student", "student", "ROLE_STUDENT");
        MvcResult created = mockMvc.perform(multipart("/notice/upload")
                .param("title", "待替换通知")
                .param("rawText", "旧手填原文")
                .with(authentication(admin)))
            .andExpect(status().isOk()).andReturn();
        long noticeId = objectMapper.readTree(created.getResponse().getContentAsString())
            .path("data").path("noticeId").asLong();
        MockMultipartFile replacement = new MockMultipartFile("file", "new-notice.txt",
            "text/plain", "new notice text".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/notice/{id}/attachment", noticeId)
                .file(replacement).with(authentication(student)))
            .andExpect(status().isForbidden());
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/notice/{id}/attachment", noticeId)
                .file(replacement).with(authentication(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.fileId").isNumber());
        mockMvc.perform(get("/notice/{id}", noticeId).with(authentication(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.parseStatus").value("DRAFT"))
            .andExpect(jsonPath("$.data.rawText").doesNotExist())
            .andExpect(jsonPath("$.data.fileName").value("new-notice.txt"));
        parseAndRun(noticeId, admin);
        mockMvc.perform(multipart(org.springframework.http.HttpMethod.PUT, "/notice/{id}/attachment", noticeId)
                .file(replacement).with(authentication(admin)))
            .andExpect(status().isConflict());
    }

    private void parseAndRun(long noticeId, Authentication admin) throws Exception {
        MvcResult accepted = mockMvc.perform(post("/notice/parse/{id}", noticeId).with(authentication(admin)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING"))
            .andReturn();
        long taskId = objectMapper.readTree(accepted.getResponse().getContentAsString())
            .path("data").path("taskId").asLong();
        noticeService.executeParseTask(taskId);
    }

    private static Authentication auth(Long id, String username, String role, String authority) {
        LoginUser user = new LoginUser(id, username, username, role);
        return new UsernamePasswordAuthenticationToken(user, null,
            List.of(new SimpleGrantedAuthority(authority)));
    }
}
