package com.eliza.aicompetition;

import com.eliza.aicompetition.common.PageResult;
import com.eliza.aicompetition.dto.project.ProjectDetailResponse;
import com.eliza.aicompetition.dto.notice.NoticeDetailResponse;
import com.eliza.aicompetition.dto.material.MaterialUploadResponse;
import com.eliza.aicompetition.entity.CompetitionNotice;
import com.eliza.aicompetition.entity.FileAsset;
import com.eliza.aicompetition.entity.ProjectMaterial;
import com.eliza.aicompetition.exception.BusinessException;
import com.eliza.aicompetition.mapper.CompetitionNoticeMapper;
import com.eliza.aicompetition.mapper.FileAssetMapper;
import com.eliza.aicompetition.mapper.ProjectMaterialMapper;
import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.mapper.AgentTaskLogMapper;
import com.eliza.aicompetition.mapper.MaterialReviewMapper;
import com.eliza.aicompetition.mapper.NotifyMessageMapper;
import com.eliza.aicompetition.mapper.ProjectMemberMapper;
import com.eliza.aicompetition.mapper.ReviewRecordMapper;
import com.eliza.aicompetition.mapper.CompetitionProjectMapper;
import com.eliza.aicompetition.mapper.NoticeParseDraftMapper;
import com.eliza.aicompetition.mapper.MaterialRequirementMapper;
import com.eliza.aicompetition.mapper.ProjectAiCheckMapper;
import com.eliza.aicompetition.security.JwtAccessDeniedHandler;
import com.eliza.aicompetition.security.JwtAuthenticationEntryPoint;
import com.eliza.aicompetition.util.JwtUtil;
import com.eliza.aicompetition.security.LoginUser;
import com.eliza.aicompetition.service.AdminService;
import com.eliza.aicompetition.service.DashboardCacheService;
import com.eliza.aicompetition.service.AgentService;
import com.eliza.aicompetition.service.MaterialService;
import com.eliza.aicompetition.service.NoticeService;
import com.eliza.aicompetition.service.NotifyService;
import com.eliza.aicompetition.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract tests for the resource authorization boundary.
 * The requests pass through Spring Security's filter chain and controllers;
 * resource decisions are represented by the service boundary used by controllers.
 */
@WebMvcTest(controllers = {
    com.eliza.aicompetition.controller.AdminController.class,
    com.eliza.aicompetition.controller.ProjectController.class,
    com.eliza.aicompetition.controller.FileController.class,
    com.eliza.aicompetition.controller.MaterialController.class,
    com.eliza.aicompetition.controller.AgentController.class,
    com.eliza.aicompetition.controller.NotifyController.class,
    com.eliza.aicompetition.controller.NoticeController.class
})
@Import({com.eliza.aicompetition.config.SecurityConfig.class,
    JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
@EnableWebSecurity
class ResourceAuthorizationHttpTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private JwtUtil jwtUtil;
    @MockitoBean private SysUserMapper sysUserMapper;
    @MockitoBean private AdminService adminService;
    @MockitoBean private DashboardCacheService dashboardCacheService;
    @MockitoBean private ProjectService projectService;
    @MockitoBean private FileAssetMapper fileAssetMapper;
    @MockitoBean private ProjectMaterialMapper projectMaterialMapper;
    @MockitoBean private CompetitionNoticeMapper competitionNoticeMapper;
    @MockitoBean private CompetitionProjectMapper competitionProjectMapper;
    @MockitoBean private ProjectMemberMapper projectMemberMapper;
    @MockitoBean private ReviewRecordMapper reviewRecordMapper;
    @MockitoBean private NotifyMessageMapper notifyMessageMapper;
    @MockitoBean private AgentTaskLogMapper agentTaskLogMapper;
    @MockitoBean private MaterialReviewMapper materialReviewMapper;
    @MockitoBean private NoticeParseDraftMapper noticeParseDraftMapper;
    @MockitoBean private MaterialRequirementMapper materialRequirementMapper;
    @MockitoBean private ProjectAiCheckMapper projectAiCheckMapper;
    @MockitoBean private MaterialService materialService;
    @MockitoBean private AgentService agentService;
    @MockitoBean private NotifyService notifyService;
    @MockitoBean private NoticeService noticeService;
    @MockitoBean private com.eliza.aicompetition.service.NoticeAiTaskDispatcher noticeAiTaskDispatcher;

    @BeforeEach
    void resetResourceDecisions() {
        when(projectService.getProjectDetail(anyLong()))
            .thenThrow(new BusinessException(403, "无权访问该项目"));
        when(materialService.listMaterials(any(), any(), any(Integer.class), any(Integer.class)))
            .thenThrow(new BusinessException(403, "无权访问该项目"));
        when(agentService.listTaskLogs(any(), any(), any(Integer.class), any(Integer.class)))
            .thenThrow(new BusinessException(403, "无权查看该项目的AI任务日志"));
        when(noticeService.getNoticeDetail(anyLong()))
            .thenThrow(new BusinessException(404, "通知不存在"));
    }

    @Test
    void anonymousBusinessRequestReturns401() throws Exception {
        mockMvc.perform(get("/project/detail/1"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotAccessAdminEndpoint() throws Exception {
        mockMvc.perform(get("/admin/users").with(authentication(student())))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotReadAnotherProjectMaterials() throws Exception {
        mockMvc.perform(get("/material/list")
                .param("projectId", "99")
                .with(authentication(student())))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotDownloadAnotherProjectsFile() throws Exception {
        FileAsset file = new FileAsset();
        file.setFileId(7L);
        file.setFileBlob(new byte[]{1});
        file.setFileName("secret.pdf");
        file.setFileExt("pdf");
        ProjectMaterial material = new ProjectMaterial();
        material.setProjectId(99L);
        material.setFileId(7L);
        when(fileAssetMapper.selectById(7L)).thenReturn(file);
        when(projectMaterialMapper.selectOne(any())).thenReturn(material);
        org.mockito.Mockito.doThrow(new BusinessException(403, "无权访问该项目"))
            .when(projectService).checkProjectReadAccess(99L);

        mockMvc.perform(get("/file/7/download").with(authentication(student())))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotViewAnotherProjectsAiLogs() throws Exception {
        mockMvc.perform(get("/agent/task-logs")
                .param("projectId", "99")
                .with(authentication(student())))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotMarkAnotherUsersMessageRead() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(403, "无权修改该消息"))
            .when(notifyService).markRead(8L);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .put("/notify/8/read").with(authentication(student())))
            .andExpect(status().isForbidden());
    }

    @Test
    void studentCannotViewDraftNotice() throws Exception {
        mockMvc.perform(get("/notice/12").with(authentication(student())))
            .andExpect(status().isNotFound());
    }

    @Test
    void studentCannotUploadNotice() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/notice/upload")
                .file(new MockMultipartFile("file", "notice.txt", "text/plain", "notice".getBytes()))
                .with(authentication(student())))
            .andExpect(status().isForbidden());
    }

    @Test
    void teacherCanReadNoticeLifecycleResource() throws Exception {
        doReturn(org.mockito.Mockito.mock(NoticeDetailResponse.class))
            .when(noticeService).getNoticeDetail(12L);

        mockMvc.perform(get("/notice/12").with(authentication(teacher())))
            .andExpect(status().isOk());
    }

    @Test
    void studentCannotCreateProjectFromUnpublishedNotice() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(409, "通知尚未发布或已归档，无法创建项目"))
            .when(projectService).createProject(any());

        mockMvc.perform(post("/project/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noticeId\":12,\"projectName\":\"demo\"}")
                .with(authentication(student())))
            .andExpect(status().isConflict());
    }

    @Test
    void teacherCannotCreateProject() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(403, "只有学生或管理员可以创建项目"))
            .when(projectService).createProject(any());

        mockMvc.perform(post("/project/create")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"noticeId\":12,\"projectName\":\"demo\"}")
                .with(authentication(teacher())))
            .andExpect(status().isForbidden());
    }

    @Test
    void memberCanReadOwnProjectAndMaterials() throws Exception {
        doReturn(ProjectDetailResponse.builder().projectId(1L).build())
            .when(projectService).getProjectDetail(1L);
        doReturn(new PageResult<>(List.of(), 0, 0, 1, 10))
            .when(materialService).listMaterials(any(), any(), any(Integer.class), any(Integer.class));
        com.eliza.aicompetition.entity.CompetitionProject project =
            new com.eliza.aicompetition.entity.CompetitionProject();
        project.setProjectId(1L);
        project.setNoticeId(12L);
        project.setLeaderId(3L);
        project.setStatus("DRAFT");
        project.setCompletionRate(java.math.BigDecimal.ZERO);
        CompetitionNotice notice = new CompetitionNotice();
        notice.setNoticeId(12L);
        notice.setPublishStatus("PUBLISHED");
        com.eliza.aicompetition.entity.SysUser leader = new com.eliza.aicompetition.entity.SysUser();
        leader.setUserId(3L);
        leader.setRealName("学生");
        when(projectMemberMapper.exists(any())).thenReturn(true);
        when(competitionProjectMapper.selectById(1L)).thenReturn(project);
        when(competitionProjectMapper.updateById(any(com.eliza.aicompetition.entity.CompetitionProject.class))).thenReturn(1);
        when(projectMaterialMapper.findLatestDetailsByProjectId(1L)).thenReturn(List.of());
        when(competitionNoticeMapper.selectById(12L)).thenReturn(notice);
        when(sysUserMapper.selectById(3L)).thenReturn(leader);
        when(reviewRecordMapper.findReviewViewsByProjectId(1L)).thenReturn(List.of());
        when(projectMaterialMapper.findMaterialsPage(any(), any(), any()))
            .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10));

        mockMvc.perform(get("/project/detail/1").with(authentication(student())))
            .andExpect(status().isOk());
        mockMvc.perform(get("/material/list")
                .param("projectId", "1")
                .with(authentication(student())))
            .andExpect(status().isOk());
    }

    @Test
    void memberCanUploadOwnMaterial() throws Exception {
        doReturn(org.mockito.Mockito.mock(MaterialUploadResponse.class))
            .when(materialService).uploadMaterial(anyLong(), anyLong(), anyLong(), any(), any());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/material/upload")
                .file(new MockMultipartFile("file", "material.txt", "text/plain", "ok".getBytes()))
                .param("projectId", "1")
                .param("requirementId", "2")
                .with(authentication(student())))
            .andExpect(status().isOk());
    }

    @Test
    void teacherCannotUploadMaterial() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(403, "只有学生或管理员可以上传材料"))
            .when(materialService).uploadMaterial(anyLong(), anyLong(), anyLong(), any(), any());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .multipart("/material/upload")
                .file(new MockMultipartFile("file", "material.txt", "text/plain", "ok".getBytes()))
                .param("projectId", "1")
                .param("requirementId", "2")
                .with(authentication(teacher())))
            .andExpect(status().isForbidden());
    }

    @Test
    void adminCanReadNoticeManagementResource() throws Exception {
        CompetitionNotice notice = new CompetitionNotice();
        notice.setNoticeId(12L);
        notice.setPublishStatus("DRAFT");
        doReturn(org.mockito.Mockito.mock(NoticeDetailResponse.class))
            .when(noticeService).getNoticeDetail(12L);
        when(competitionNoticeMapper.selectById(12L)).thenReturn(notice);

        mockMvc.perform(get("/notice/12").with(authentication(admin())))
            .andExpect(status().isOk());
    }

    private static Authentication student() {
        LoginUser user = new LoginUser(3L, "student", "学生", "student");
        return new UsernamePasswordAuthenticationToken(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    private static Authentication admin() {
        LoginUser user = new LoginUser(1L, "admin", "管理员", "admin");
        return new UsernamePasswordAuthenticationToken(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
    }

    private static Authentication teacher() {
        LoginUser user = new LoginUser(2L, "teacher", "教师", "teacher");
        return new UsernamePasswordAuthenticationToken(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
    }
}
