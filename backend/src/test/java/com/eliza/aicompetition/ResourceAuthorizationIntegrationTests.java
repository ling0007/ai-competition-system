package com.eliza.aicompetition;

import com.eliza.aicompetition.security.LoginUser;
import com.eliza.aicompetition.service.RedisLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Real MySQL resource authorization tests. The fixture uses actual users,
 * notices, project membership, requirements, materials and task logs; only
 * the authentication object is supplied by the test request.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ResourceAuthorizationIntegrationTests extends IsolatedDatabaseSpringTest {

    private static final long MEMBER_ID = 1001L;
    private static final long OTHER_STUDENT_ID = 1002L;
    private static final long TEACHER_ID = 1003L;
    private static final long ADMIN_ID = 1004L;
    private static final long OTHER_TEACHER_ID = 1005L;
    private static final long PUBLISHED_NOTICE_ID = 2001L;
    private static final long DRAFT_NOTICE_ID = 2002L;
    private static final long PROJECT_ID = 3001L;
    private static final long REQUIREMENT_ID = 4001L;
    private static final long SECOND_REQUIREMENT_ID = 4002L;
    private static final long FILE_ID = 5001L;
    private static final long MATERIAL_ID = 6001L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private RedisLockService redisLockService;

    @BeforeEach
    void seedRealAuthorizationFixture() {
        when(redisLockService.acquire(anyString(), any())).thenReturn(new RedisLockService.Lease(true, "test"));
        jdbcTemplate.update("INSERT INTO sys_user(user_id, username, password, real_name, role) VALUES "
            + "(?,?,?,?,?), (?,?,?,?,?), (?,?,?,?,?), (?,?,?,?,?), (?,?,?,?,?)",
            MEMBER_ID, "integration-member", "{noop}pw", "成员学生", "student",
            OTHER_STUDENT_ID, "integration-other", "{noop}pw", "其他学生", "student",
            TEACHER_ID, "integration-teacher", "{noop}pw", "审核教师", "teacher",
            ADMIN_ID, "integration-admin", "{noop}pw", "管理员", "admin",
            OTHER_TEACHER_ID, "integration-other-teacher", "{noop}pw", "未分配教师", "teacher");

        jdbcTemplate.update("INSERT INTO competition_notice(notice_id,title,organizer,deadline,created_by,notice_type,parse_status,publish_status) "
            + "VALUES (?,?,?,?,?,?,?,?),(?,?,?,?,?,?,?,?)",
            PUBLISHED_NOTICE_ID, "已发布集成通知", "测试主办方", "2030-01-01 00:00:00", MEMBER_ID,
            "COMPETITION", "PARSED", "PUBLISHED",
            DRAFT_NOTICE_ID, "草稿集成通知", "测试主办方", "2030-01-01 00:00:00", ADMIN_ID,
            "COMPETITION", "DRAFT", "DRAFT");

        jdbcTemplate.update("INSERT INTO competition_project(project_id,notice_id,leader_id,project_name,team_name,status,deadline,completion_rate) "
            + "VALUES (?,?,?,?,?,?,?,?)",
            PROJECT_ID, PUBLISHED_NOTICE_ID, MEMBER_ID, "集成测试项目", "集成测试队", "UNDER_REVIEW",
            "2030-01-01 00:00:00", 100.00);
        jdbcTemplate.update("INSERT INTO project_member(project_id,user_id,member_role) VALUES (?,?,?),(?,?,?),(?,?,?)",
            PROJECT_ID, MEMBER_ID, "leader",
            PROJECT_ID, TEACHER_ID, "advisor",
            PROJECT_ID, OTHER_TEACHER_ID, "member");

        jdbcTemplate.update("INSERT INTO material_requirement(requirement_id,notice_id,requirement_name,is_required,description,sort_no,status,source,version_no) "
            + "VALUES (?,?,?,?,?,?,?,?,?),(?,?,?,?,?,?,?,?,?)",
            REQUIREMENT_ID, PUBLISHED_NOTICE_ID, "身份证明", 1, "测试必交材料", 1, "ACTIVE", "MANUAL", 1,
            SECOND_REQUIREMENT_ID, PUBLISHED_NOTICE_ID, "补充材料", 0, "测试可选材料", 2, "ACTIVE", "MANUAL", 1);

        jdbcTemplate.update("INSERT INTO file_asset(file_id,biz_type,file_name,file_ext,file_size,file_blob,uploaded_by) VALUES (?,?,?,?,?,?,?)",
            FILE_ID, "material", "integration.pdf", "pdf", 2L, new byte[]{1, 2}, MEMBER_ID);
        jdbcTemplate.update("INSERT INTO project_material(material_id,project_id,requirement_id,file_id,file_hash,current_version_id,submit_status,version_no,submitted_at) "
            + "VALUES (?,?,?,?,?,?,?,?,?)",
            MATERIAL_ID, PROJECT_ID, REQUIREMENT_ID, FILE_ID, "integration-hash", MATERIAL_ID, "submitted", 1,
            "2026-01-01 00:00:00");
        jdbcTemplate.update("INSERT INTO agent_task_log(task_id,project_id,tool_name,input_summary,result_summary,execute_status) VALUES (?,?,?,?,?,?)",
            7001L, PROJECT_ID, "integration-check", "fixture", "ok", "SUCCESS");
    }

    @Test
    void memberCanReadOwnProjectMaterialsButCannotUploadDuringReview() throws Exception {
        mockMvc.perform(get("/project/detail/{id}", PROJECT_ID)
                .with(authentication(student(MEMBER_ID, "integration-member"))))
            .andExpect(status().isOk());
        mockMvc.perform(get("/material/list")
                .param("projectId", String.valueOf(PROJECT_ID))
                .with(authentication(student(MEMBER_ID, "integration-member"))))
            .andExpect(status().isOk());
        mockMvc.perform(get("/file/{id}/download", FILE_ID)
                .with(authentication(student(MEMBER_ID, "integration-member"))))
            .andExpect(status().isOk());
        mockMvc.perform(multipart("/material/upload")
                .file(new MockMultipartFile("file", "new-material.txt", "text/plain", "new".getBytes()))
                .param("projectId", String.valueOf(PROJECT_ID))
                .param("requirementId", String.valueOf(SECOND_REQUIREMENT_ID))
                .with(authentication(student(MEMBER_ID, "integration-member"))))
            .andExpect(status().isConflict());
    }

    @Test
    void nonMemberCannotReadProjectMaterialsOrDownloadFile() throws Exception {
        mockMvc.perform(get("/material/list")
                .param("projectId", String.valueOf(PROJECT_ID))
                .with(authentication(student(OTHER_STUDENT_ID, "integration-other"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/file/{id}/download", FILE_ID)
                .with(authentication(student(OTHER_STUDENT_ID, "integration-other"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/material/projects/{projectId}/requirements/{requirementId}/versions",
                PROJECT_ID, REQUIREMENT_ID)
                .with(authentication(student(OTHER_STUDENT_ID, "integration-other"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/agent/projects/{projectId}/checks", PROJECT_ID)
                .with(authentication(student(OTHER_STUDENT_ID, "integration-other"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void assignedAdvisorCanReadUnderReviewResourcesButCannotCreateOrUpload() throws Exception {
        mockMvc.perform(get("/material/list")
                .param("projectId", String.valueOf(PROJECT_ID))
                .with(authentication(teacher(TEACHER_ID, "integration-teacher"))))
            .andExpect(status().isOk());
        mockMvc.perform(get("/agent/task-logs")
                .param("projectId", String.valueOf(PROJECT_ID))
                .with(authentication(teacher(TEACHER_ID, "integration-teacher"))))
            .andExpect(status().isOk());
        mockMvc.perform(post("/project/create")
                .contentType(APPLICATION_JSON)
                .content("{\"noticeId\":2001,\"projectName\":\"illegal-teacher-project\"}")
                .with(authentication(teacher(TEACHER_ID, "integration-teacher"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(multipart("/material/upload")
                .file(new MockMultipartFile("file", "teacher.txt", "text/plain", "x".getBytes()))
                .param("projectId", String.valueOf(PROJECT_ID))
                .param("requirementId", String.valueOf(SECOND_REQUIREMENT_ID))
                .with(authentication(teacher(TEACHER_ID, "integration-teacher"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void unassignedTeacherCannotReadOrReviewProjectEvenWhenListedAsOrdinaryMember() throws Exception {
        Authentication unassigned = teacher(OTHER_TEACHER_ID, "integration-other-teacher");

        mockMvc.perform(get("/project/detail/{id}", PROJECT_ID).with(authentication(unassigned)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/project/{id}/review-status", PROJECT_ID).with(authentication(unassigned)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/file/{id}/download", FILE_ID).with(authentication(unassigned)))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/material/review")
                .contentType(APPLICATION_JSON)
                .content("{\"projectId\":" + PROJECT_ID + ",\"materialId\":" + MATERIAL_ID
                    + ",\"reviewStatus\":\"approved\"}")
                .with(authentication(unassigned)))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/project/{id}/approve", PROJECT_ID).with(authentication(unassigned)))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/project/{id}/request-revision", PROJECT_ID).with(authentication(unassigned)))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/project/my-projects").with(authentication(unassigned)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.total").value(0));
    }

    @Test
    void assignedAdvisorCanReviewCurrentMaterialAndApproveProject() throws Exception {
        Authentication advisor = teacher(TEACHER_ID, "integration-teacher");
        mockMvc.perform(get("/project/my-projects").param("status", "UNDER_REVIEW")
                .param("deadlineBefore", "2030-01-01T23:59:59").with(authentication(advisor)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mockMvc.perform(get("/project/my-projects").param("deadlineBefore", "2029-12-31T23:59:59")
                .with(authentication(advisor)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(post("/material/review")
                .contentType(APPLICATION_JSON)
                .content("{\"projectId\":" + PROJECT_ID + ",\"materialId\":" + MATERIAL_ID
                    + ",\"reviewStatus\":\"approved\"}")
                .with(authentication(advisor)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/project/reviews/my-history").with(authentication(advisor)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].versionNo").value(1))
            .andExpect(jsonPath("$.data.records[0].requirementName").value("身份证明"));
        mockMvc.perform(get("/project/reviews/my-history")
                .with(authentication(teacher(OTHER_TEACHER_ID, "integration-other-teacher"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mockMvc.perform(get("/project/reviews/my-history")
                .with(authentication(student(MEMBER_ID, "integration-member"))))
            .andExpect(status().isForbidden());
        mockMvc.perform(put("/project/{id}/approve", PROJECT_ID).with(authentication(advisor)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/project/reviews/my-history").with(authentication(advisor)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.records[?(@.reviewType == 'project')].decision")
                .value(org.hamcrest.Matchers.hasItem("APPROVED")));
        mockMvc.perform(get("/project/detail/{id}", PROJECT_ID).with(authentication(advisor)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewRecords.length()").value(2));
    }

    @Test
    void projectRevisionRequiresExplicitReasonAndWritesTimeline() throws Exception {
        Authentication advisor = teacher(TEACHER_ID, "integration-teacher");
        mockMvc.perform(put("/project/{id}/request-revision", PROJECT_ID).with(authentication(advisor)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/project/{id}/request-revision", PROJECT_ID).param("reason", "  ")
                .with(authentication(advisor)))
            .andExpect(status().isBadRequest());
        mockMvc.perform(put("/project/{id}/request-revision", PROJECT_ID).param("reason", "请补充来源")
                .with(authentication(advisor)))
            .andExpect(status().isOk());
        mockMvc.perform(get("/project/detail/{id}", PROJECT_ID).with(authentication(advisor)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.reviewRecords[0].reviewComment").value("请补充来源"));
    }

    @Test
    void serverRejectsApprovalBeforeRequiredCurrentVersionIsReviewed() throws Exception {
        mockMvc.perform(put("/project/{id}/approve", PROJECT_ID)
                .with(authentication(teacher(TEACHER_ID, "integration-teacher"))))
            .andExpect(status().isConflict());
    }

    @Test
    void adminRetainsProjectReviewAccess() throws Exception {
        mockMvc.perform(get("/project/detail/{id}", PROJECT_ID).with(authentication(admin())))
            .andExpect(status().isOk());
        mockMvc.perform(get("/file/{id}/download", FILE_ID).with(authentication(admin())))
            .andExpect(status().isOk());
        mockMvc.perform(put("/project/{id}/request-revision", PROJECT_ID).param("reason", "请补充说明").with(authentication(admin())))
            .andExpect(status().isOk());
        mockMvc.perform(get("/project/reviews/my-history").with(authentication(admin())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
            .andExpect(jsonPath("$.data.records[0].reviewType").value("project"));
    }

    @Test
    void nonTeacherCannotBeAssignedAsAdvisor() throws Exception {
        mockMvc.perform(post("/project/create")
                .contentType(APPLICATION_JSON)
                .content("{\"noticeId\":" + PUBLISHED_NOTICE_ID
                    + ",\"projectName\":\"invalid-advisor-project\",\"advisorId\":" + OTHER_STUDENT_ID + "}")
                .with(authentication(student(MEMBER_ID, "integration-member"))))
            .andExpect(status().isBadRequest());

        jdbcTemplate.update("UPDATE competition_project SET status='DRAFT' WHERE project_id=?", PROJECT_ID);
        mockMvc.perform(post("/project/{id}/members", PROJECT_ID)
                .contentType(APPLICATION_JSON)
                .content("{\"userId\":" + OTHER_STUDENT_ID + ",\"memberRole\":\"advisor\"}")
                .with(authentication(student(MEMBER_ID, "integration-member"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    void studentCannotSeeDraftNotice() throws Exception {
        mockMvc.perform(get("/notice/{id}", DRAFT_NOTICE_ID)
                .with(authentication(student(MEMBER_ID, "integration-member"))))
            .andExpect(status().isNotFound());
        mockMvc.perform(get("/notice/{id}", DRAFT_NOTICE_ID)
                .with(authentication(admin())))
            .andExpect(status().isOk());
        mockMvc.perform(get("/notice/list")
                .with(authentication(student(MEMBER_ID, "integration-member"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.records", hasSize(1)))
            .andExpect(jsonPath("$.data.records[0].noticeId").value(PUBLISHED_NOTICE_ID));
    }

    private static Authentication student(Long id, String username) {
        LoginUser user = new LoginUser(id, username, "学生", "student");
        return new UsernamePasswordAuthenticationToken(user, null,
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    private static Authentication teacher(Long id, String username) {
        LoginUser user = new LoginUser(id, username, "教师", "teacher");
        return new UsernamePasswordAuthenticationToken(user, null,
            List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
    }

    private static Authentication admin() {
        LoginUser user = new LoginUser(ADMIN_ID, "integration-admin", "管理员", "admin");
        return new UsernamePasswordAuthenticationToken(user, null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }
}
