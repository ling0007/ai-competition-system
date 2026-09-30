package com.eliza.aicompetition;

import com.eliza.aicompetition.security.LoginUser;
import com.eliza.aicompetition.service.RedisLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MaterialConsistencyIntegrationTests extends IsolatedDatabaseSpringTest {

    private static final long STUDENT_ID = 9101L;
    private static final long TEACHER_ID = 9102L;
    private static final long NOTICE_ID = 9201L;
    private static final long PROJECT_ID = 9301L;
    private static final long REQUIREMENT_A = 9401L;
    private static final long REQUIREMENT_B = 9402L;
    private static final long REQUIREMENT_C = 9403L;
    private static final long INACTIVE_REQUIREMENT = 9404L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private RedisLockService redisLockService;

    @BeforeEach
    void seedProject() {
        when(redisLockService.acquire(anyString(), any())).thenReturn(new RedisLockService.Lease(true, "test"));
        jdbcTemplate.update(
            "INSERT INTO sys_user(user_id,username,password,real_name,role) VALUES (?,?,?,?,?),(?,?,?,?,?)",
            STUDENT_ID, "material-consistency-student", "{noop}pw", "一致性测试学生", "student",
            TEACHER_ID, "material-consistency-teacher", "{noop}pw", "一致性测试教师", "teacher");
        jdbcTemplate.update(
            "INSERT INTO competition_notice(notice_id,title,organizer,deadline,created_by,notice_type,parse_status,publish_status) "
                + "VALUES (?,?,?,?,?,?,?,?)",
            NOTICE_ID, "材料一致性测试通知", "测试主办方", "2099-12-31 23:59:59", STUDENT_ID,
            "COMPETITION", "PARSED", "PUBLISHED");
        jdbcTemplate.update(
            "INSERT INTO competition_project(project_id,notice_id,leader_id,project_name,status,deadline,completion_rate) "
                + "VALUES (?,?,?,?,?,?,?)",
            PROJECT_ID, NOTICE_ID, STUDENT_ID, "材料一致性测试项目", "DRAFT", "2099-12-31 23:59:59", 0);
        jdbcTemplate.update(
            "INSERT INTO project_member(project_id,user_id,member_role) VALUES (?,?,?),(?,?,?)",
            PROJECT_ID, STUDENT_ID, "leader",
            PROJECT_ID, TEACHER_ID, "advisor");
        jdbcTemplate.update(
            "INSERT INTO material_requirement(requirement_id,notice_id,requirement_name,is_required,sort_no,status,source,version_no) "
                + "VALUES (?,?,?,?,?,?,?,?),(?,?,?,?,?,?,?,?),(?,?,?,?,?,?,?,?),(?,?,?,?,?,?,?,?)",
            REQUIREMENT_A, NOTICE_ID, "材料A", 1, 1, "ACTIVE", "MANUAL", 1,
            REQUIREMENT_B, NOTICE_ID, "材料B", 1, 2, "ACTIVE", "MANUAL", 1,
            REQUIREMENT_C, NOTICE_ID, "材料C", 1, 3, "ACTIVE", "MANUAL", 1,
            INACTIVE_REQUIREMENT, NOTICE_ID, "历史停用材料", 1, 4, "INACTIVE", "MANUAL", 1);
    }

    @Test
    void inactiveRequirementIsExcludedAndCompleteDraftCanSubmit() throws Exception {
        insertVersion(REQUIREMENT_A, 1, true);
        insertVersion(REQUIREMENT_B, 1, true);
        insertVersion(REQUIREMENT_C, 1, true);

        mockMvc.perform(get("/project/detail/{id}", PROJECT_ID)
                .with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.materials", hasSize(3)));

        mockMvc.perform(get("/project/progress/{id}", PROJECT_ID)
                .with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.requiredTotal").value(3))
            .andExpect(jsonPath("$.data.submittedTotal").value(3))
            .andExpect(jsonPath("$.data.completionRate").value(100.0))
            .andExpect(jsonPath("$.data.materialComplete").value(true));

        mockMvc.perform(post("/project/{id}/submit", PROJECT_ID)
                .with(authentication(student())))
            .andExpect(status().isOk());

        assertEquals("UNDER_REVIEW", jdbcTemplate.queryForObject(
            "SELECT status FROM competition_project WHERE project_id=?", String.class, PROJECT_ID));
    }

    @Test
    void projectCreationInitializesOnlyActiveRequirements() throws Exception {
        String projectName = "仅初始化有效要求";

        mockMvc.perform(post("/project/create")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"noticeId\":" + NOTICE_ID + ",\"leaderId\":" + STUDENT_ID
                    + ",\"projectName\":\"" + projectName + "\"}")
                .with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.initializedMaterialCount").value(3));

        Long createdProjectId = jdbcTemplate.queryForObject(
            "SELECT project_id FROM competition_project WHERE project_name=?", Long.class, projectName);
        assertEquals(3, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM project_material WHERE project_id=?", Integer.class, createdProjectId));
        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM project_material pm JOIN material_requirement mr "
                + "ON mr.requirement_id=pm.requirement_id WHERE pm.project_id=? AND mr.status='INACTIVE'",
            Integer.class, createdProjectId));

        mockMvc.perform(multipart("/material/upload")
                .file(new MockMultipartFile("file", "first-version.txt", "text/plain", "first".getBytes()))
                .param("projectId", String.valueOf(createdProjectId))
                .param("requirementId", String.valueOf(REQUIREMENT_A))
                .with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionNo").value(1));
    }

    @Test
    void legacyEmptyV1PlaceholderDoesNotConsumeFirstUploadedVersion() throws Exception {
        jdbcTemplate.update("INSERT INTO project_material(project_id,requirement_id,version_no) VALUES (?,?,1)",
            PROJECT_ID, REQUIREMENT_A);

        performUpload(REQUIREMENT_A)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.versionNo").value(1));

        assertEquals(0, jdbcTemplate.queryForObject(
            "SELECT version_no FROM project_material WHERE project_id=? AND requirement_id=? AND file_id IS NULL",
            Integer.class, PROJECT_ID, REQUIREMENT_A));
    }

    @Test
    void threeVersionsOfOneRequirementCountOnce() throws Exception {
        insertVersion(REQUIREMENT_A, 1, false);
        insertVersion(REQUIREMENT_A, 2, false);
        insertVersion(REQUIREMENT_A, 3, true);
        insertVersion(REQUIREMENT_B, 1, true);

        mockMvc.perform(get("/project/progress/{id}", PROJECT_ID)
                .with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.requiredTotal").value(3))
            .andExpect(jsonPath("$.data.submittedTotal").value(2))
            .andExpect(jsonPath("$.data.missingTotal").value(1))
            .andExpect(jsonPath("$.data.completionRate").value(66.67));

        mockMvc.perform(get("/project/detail/{id}", PROJECT_ID)
                .with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.materials", hasSize(3)))
            .andExpect(jsonPath("$.data.materials[0].versionNo").value(3))
            .andExpect(jsonPath("$.data.materials[0].uploaded").value(true));

        assertEquals("66.67", jdbcTemplate.queryForObject(
            "SELECT CAST(completion_rate AS CHAR) FROM competition_project WHERE project_id=?",
            String.class, PROJECT_ID));
    }

    @Test
    void underReviewMemberCannotUploadMaterial() throws Exception {
        setProjectStatus("UNDER_REVIEW");

        performUpload(REQUIREMENT_A)
            .andExpect(status().isConflict());
    }

    @Test
    void approvedProjectCannotUploadNewVersion() throws Exception {
        setProjectStatus("APPROVED");

        performUpload(REQUIREMENT_A)
            .andExpect(status().isConflict());
    }

    @Test
    void nonDraftProjectCannotModifyMembers() throws Exception {
        setProjectStatus("UNDER_REVIEW");
        mockMvc.perform(post("/project/{id}/members", PROJECT_ID)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + TEACHER_ID + ",\"memberRole\":\"advisor\"}")
                .with(authentication(student())))
            .andExpect(status().isConflict());

        setProjectStatus("APPROVED");
        Long leaderMemberId = jdbcTemplate.queryForObject(
            "SELECT member_id FROM project_member WHERE project_id=? AND user_id=?",
            Long.class, PROJECT_ID, STUDENT_ID);
        mockMvc.perform(delete("/project/{id}/members/{memberId}", PROJECT_ID, leaderMemberId)
                .with(authentication(student())))
            .andExpect(status().isConflict());
    }

    @Test
    void revisionRequiredProjectCanUploadNewVersion() throws Exception {
        setProjectStatus("REVISION_REQUIRED");

        performUpload(REQUIREMENT_A)
            .andExpect(status().isOk());

        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM project_material WHERE project_id=? AND requirement_id=? "
                + "AND current_version_id=material_id AND file_id IS NOT NULL",
            Integer.class, PROJECT_ID, REQUIREMENT_A));
    }

    @Test
    void resubmitAfterDeadlineIsRejected() throws Exception {
        setProjectStatus("REVISION_REQUIRED");
        jdbcTemplate.update(
            "UPDATE competition_project SET deadline='2000-01-01 00:00:00' WHERE project_id=?", PROJECT_ID);

        mockMvc.perform(post("/project/{id}/resubmit", PROJECT_ID)
                .with(authentication(student())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void revisionRequiredCannotUseInitialSubmitEndpoint() throws Exception {
        setProjectStatus("REVISION_REQUIRED");

        mockMvc.perform(post("/project/{id}/submit", PROJECT_ID)
                .with(authentication(student())))
            .andExpect(status().isConflict());
    }

    @Test
    void reviewOfVersionTwoDoesNotApplyToUploadedVersionThree() throws Exception {
        insertVersion(REQUIREMENT_A, 1, false);
        insertVersion(REQUIREMENT_A, 2, true);
        long versionTwoId = REQUIREMENT_A * 10 + 2;
        jdbcTemplate.update(
            "INSERT INTO material_review(material_id,material_version_id,reviewer_id,decision,comment) "
                + "VALUES (?,?,?,?,?)",
            versionTwoId, versionTwoId, TEACHER_ID, "APPROVED", "v2 审核通过");
        setProjectStatus("UNDER_REVIEW");

        mockMvc.perform(get("/project/detail/{id}", PROJECT_ID)
                .with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.materials[0].versionNo").value(2))
            .andExpect(jsonPath("$.data.materials[0].reviewStatus").value("APPROVED"));

        mockMvc.perform(put("/project/{id}/request-revision", PROJECT_ID).param("reason", "请补充材料说明")
                .with(authentication(teacher())))
            .andExpect(status().isOk());
        performUpload(REQUIREMENT_A).andExpect(status().isOk());

        mockMvc.perform(get("/project/detail/{id}", PROJECT_ID)
                .with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.materials[0].versionNo").value(3))
            .andExpect(jsonPath("$.data.materials[0].currentVersionId")
                .value(org.hamcrest.Matchers.not(versionTwoId)))
            .andExpect(jsonPath("$.data.materials[0].reviewStatus").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void materialVersionHistoryKeepsOldReviewSeparateFromCurrentVersion() throws Exception {
        insertVersion(REQUIREMENT_A, 1, false);
        insertVersion(REQUIREMENT_A, 2, true);
        long versionOneId = REQUIREMENT_A * 10 + 1;
        jdbcTemplate.update(
            "INSERT INTO material_review(material_id,material_version_id,reviewer_id,decision,comment) "
                + "VALUES (?,?,?,?,?)",
            versionOneId, versionOneId, TEACHER_ID, "APPROVED", "v1 历史审核通过");

        mockMvc.perform(get("/material/projects/{projectId}/requirements/{requirementId}/versions",
                PROJECT_ID, REQUIREMENT_A).with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].versionNo").value(2))
            .andExpect(jsonPath("$.data[0].currentVersion").value(true))
            .andExpect(jsonPath("$.data[0].reviewStatus").value(org.hamcrest.Matchers.nullValue()))
            .andExpect(jsonPath("$.data[1].versionNo").value(1))
            .andExpect(jsonPath("$.data[1].reviewStatus").value("APPROVED"));
    }

    @Test
    void revisionResubmissionAndApprovalStayVersionBoundInTimeline() throws Exception {
        insertVersion(REQUIREMENT_A, 1, true);
        insertVersion(REQUIREMENT_B, 1, true);
        insertVersion(REQUIREMENT_C, 1, true);
        mockMvc.perform(post("/project/{id}/submit", PROJECT_ID).with(authentication(student())))
            .andExpect(status().isOk());
        long oldVersion = REQUIREMENT_A * 10 + 1;
        review(oldVersion, "REVISION_REQUIRED", "请补充依据");
        mockMvc.perform(put("/project/{id}/request-revision", PROJECT_ID).param("reason", "项目整体需调整")
                .with(authentication(teacher())))
            .andExpect(status().isOk());
        performUpload(REQUIREMENT_A).andExpect(status().isOk());
        mockMvc.perform(get("/project/reviews/my-history").with(authentication(teacher())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
            .andExpect(jsonPath("$.data.records[?(@.reviewType == 'material')].versionNo").value(hasItem(1)));
        mockMvc.perform(post("/project/{id}/resubmit", PROJECT_ID).with(authentication(student())))
            .andExpect(status().isOk());
        Long newVersion = jdbcTemplate.queryForObject(
            "SELECT current_version_id FROM project_material WHERE project_id=? AND requirement_id=? "
                + "AND current_version_id=material_id ORDER BY version_no DESC LIMIT 1",
            Long.class, PROJECT_ID, REQUIREMENT_A);
        review(newVersion, "APPROVED", "新版通过");
        review(REQUIREMENT_B * 10 + 1, "APPROVED", "通过");
        review(REQUIREMENT_C * 10 + 1, "APPROVED", "通过");
        mockMvc.perform(put("/project/{id}/approve", PROJECT_ID).with(authentication(teacher())))
            .andExpect(status().isOk());
        mockMvc.perform(get("/project/detail/{id}", PROJECT_ID).with(authentication(teacher())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.materials[0].versionNo").value(2))
            .andExpect(jsonPath("$.data.materials[0].reviewStatus").value("APPROVED"))
            .andExpect(jsonPath("$.data.reviewRecords", hasSize(8)));
        mockMvc.perform(get("/project/reviews/my-history").with(authentication(teacher())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(6))
            .andExpect(jsonPath("$.data.records[?(@.reviewType == 'project')].decision")
                .value(hasItem("APPROVED")));
    }

    private void review(long materialId, String decision, String comment) throws Exception {
        mockMvc.perform(post("/material/review").contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"projectId\":" + PROJECT_ID + ",\"materialId\":" + materialId
                    + ",\"reviewStatus\":\"" + decision + "\",\"reviewComment\":\"" + comment + "\"}")
                .with(authentication(teacher())))
            .andExpect(status().isOk());
    }

    @Test
    void aiCheckHistoryBecomesStaleAfterMaterialVersionChanges() throws Exception {
        insertVersion(REQUIREMENT_A, 1, true);
        long versionOneId = REQUIREMENT_A * 10 + 1;
        jdbcTemplate.update(
            "INSERT INTO project_ai_check(project_id,material_snapshot,result,issue_summary) VALUES (?,?,?,?)",
            PROJECT_ID, "[" + versionOneId + "]", "PASSED", null);

        mockMvc.perform(get("/agent/projects/{projectId}/checks", PROJECT_ID)
                .with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].stale").value(true));
        mockMvc.perform(get("/dashboard/bootstrap").with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.aiCheck").doesNotExist());

        performUpload(REQUIREMENT_A).andExpect(status().isOk());

        mockMvc.perform(get("/agent/projects/{projectId}/checks", PROJECT_ID)
                .with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].stale").value(true));
        mockMvc.perform(get("/dashboard/bootstrap").with(authentication(student())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.aiCheck").doesNotExist());
    }

    private void insertVersion(long requirementId, int versionNo, boolean current) {
        long materialId = requirementId * 10 + versionNo;
        long fileId = materialId + 100000;
        jdbcTemplate.update(
            "INSERT INTO file_asset(file_id,biz_type,file_name,file_ext,file_size,file_blob,uploaded_by) "
                + "VALUES (?,?,?,?,?,?,?)",
            fileId, "material", "material-" + materialId + ".txt", "txt", 1L, new byte[]{1}, STUDENT_ID);
        jdbcTemplate.update(
            "INSERT INTO project_material(material_id,project_id,requirement_id,file_id,current_version_id,submit_status,version_no,submitted_at) "
                + "VALUES (?,?,?,?,?,?,?,NOW())",
            materialId, PROJECT_ID, requirementId, fileId, current ? materialId : null, "submitted", versionNo);
    }

    private org.springframework.test.web.servlet.ResultActions performUpload(long requirementId) throws Exception {
        return mockMvc.perform(multipart("/material/upload")
            .file(new MockMultipartFile("file", "new-version.txt", "text/plain", "new".getBytes()))
            .param("projectId", String.valueOf(PROJECT_ID))
            .param("requirementId", String.valueOf(requirementId))
            .with(authentication(student())));
    }

    private void setProjectStatus(String status) {
        jdbcTemplate.update("UPDATE competition_project SET status=? WHERE project_id=?", status, PROJECT_ID);
    }

    private static UsernamePasswordAuthenticationToken student() {
        LoginUser user = new LoginUser(STUDENT_ID, "material-consistency-student", "一致性测试学生", "student");
        return new UsernamePasswordAuthenticationToken(
            user, null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    private static UsernamePasswordAuthenticationToken teacher() {
        LoginUser user = new LoginUser(TEACHER_ID, "material-consistency-teacher", "一致性测试教师", "teacher");
        return new UsernamePasswordAuthenticationToken(
            user, null, List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_TEACHER")));
    }
}
