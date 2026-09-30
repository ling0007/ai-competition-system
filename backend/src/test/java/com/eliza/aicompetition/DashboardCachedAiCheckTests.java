package com.eliza.aicompetition;

import com.eliza.aicompetition.controller.DashboardController;
import com.eliza.aicompetition.dto.agent.ProjectAiCheckView;
import com.eliza.aicompetition.mapper.CompetitionNoticeMapper;
import com.eliza.aicompetition.mapper.CompetitionProjectMapper;
import com.eliza.aicompetition.mapper.FileAssetMapper;
import com.eliza.aicompetition.mapper.ProjectMemberMapper;
import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.entity.CompetitionProject;
import com.eliza.aicompetition.dto.project.ProjectProgressResponse;
import com.eliza.aicompetition.security.LoginUser;
import com.eliza.aicompetition.service.AgentService;
import com.eliza.aicompetition.service.NoticeService;
import com.eliza.aicompetition.service.ProjectService;
import com.eliza.aicompetition.service.DashboardCacheService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

class DashboardCachedAiCheckTests {
    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missLoadsGlobalDataOnceAndHitSkipsGlobalQueries() {
        var user = new LoginUser(42L, "student", "Student", "student");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        DashboardCacheService cache = mock(DashboardCacheService.class);
        var cached = Map.<String, Object>of("noticeOptions", List.of(), "userOptions", List.of());
        when(cache.getGlobal(42L, "student")).thenReturn(null, cached);
        SysUserMapper users = mock(SysUserMapper.class);
        when(users.selectList(null)).thenReturn(List.of());
        CompetitionNoticeMapper notices = mock(CompetitionNoticeMapper.class);
        ProjectMemberMapper members = mock(ProjectMemberMapper.class);
        when(members.findProjectIdsByUserId(42L, null)).thenReturn(List.of());
        var controller = new DashboardController(notices, mock(CompetitionProjectMapper.class),
            mock(FileAssetMapper.class), users, members, mock(NoticeService.class),
            mock(ProjectService.class), cache, mock(AgentService.class));

        controller.bootstrap();
        controller.bootstrap();

        verify(cache).cacheGlobal(eq(42L), eq("student"), eq(0L), any());
        verify(users, times(1)).selectList(null);
        verify(notices, times(1)).selectOne(any());
        verify(members, times(2)).findProjectIdsByUserId(42L, null);
    }

    @Test
    void cachedAiResultIsRemovedWhenMaterialSnapshotHasBecomeStale() {
        var user = new LoginUser(42L, "student", "Student", "student");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        DashboardCacheService cache = mock(DashboardCacheService.class);
        AgentService agent = mock(AgentService.class);
        Map<String, Object> cached = new HashMap<>();
        cached.put("noticeOptions", List.of());
        cached.put("userOptions", List.of());
        when(cache.getGlobal(42L, "student")).thenReturn(cached);
        ProjectMemberMapper members = mock(ProjectMemberMapper.class);
        when(members.findProjectIdsByUserId(42L, null)).thenReturn(List.of(77L));
        CompetitionProjectMapper projects = mock(CompetitionProjectMapper.class);
        CompetitionProject project = new CompetitionProject();
        project.setProjectId(77L);
        project.setProjectName("Project");
        when(projects.selectById(77L)).thenReturn(project);
        ProjectService projectService = mock(ProjectService.class);
        when(projectService.refreshProjectProgress(77L)).thenReturn(
            ProjectProgressResponse.builder().missingMaterials(List.of()).build());
        when(agent.listProjectChecks(77L)).thenReturn(List.of(new ProjectAiCheckView(
            1L, 77L, "PASSED", "旧结果", List.of(10L), true, null)));
        CompetitionNoticeMapper notices = mock(CompetitionNoticeMapper.class);
        SysUserMapper users = mock(SysUserMapper.class);
        var controller = new DashboardController(
            notices, projects,
            mock(FileAssetMapper.class), users,
            members, mock(NoticeService.class),
            projectService, cache, agent);

        assertNull(controller.bootstrap().data().get("aiCheck"));
        verifyNoInteractions(notices, users);
    }

    @Test
    void removedMemberDoesNotReceiveCachedProjectData() {
        var user = new LoginUser(42L, "student", "Student", "student");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            user, null, List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))));
        DashboardCacheService cache = mock(DashboardCacheService.class);
        Map<String, Object> cached = new HashMap<>();
        cached.put("noticeOptions", List.of());
        cached.put("userOptions", List.of());
        cached.put("projectDetail", Map.of("projectId", 77L));
        when(cache.getGlobal(42L, "student")).thenReturn(cached);
        var controller = new DashboardController(
            mock(CompetitionNoticeMapper.class), mock(CompetitionProjectMapper.class),
            mock(FileAssetMapper.class), mock(SysUserMapper.class),
            mock(ProjectMemberMapper.class), mock(NoticeService.class),
            mock(ProjectService.class), cache, mock(AgentService.class));
        assertNull(controller.bootstrap().data().get("projectDetail"));
    }
}
