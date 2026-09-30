package com.eliza.aicompetition;

import com.eliza.aicompetition.security.LoginUser;
import com.eliza.aicompetition.service.MaterialService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real connection-refused Redis behavior against a dedicated MySQL schema. */
@EnabledIfSystemProperty(named = "phase56.redis.outage", matches = "true")
@SpringBootTest(properties = {"spring.data.redis.port=6399", "spring.data.redis.timeout=200ms",
    "spring.data.redis.connect-timeout=200ms", "notice.ai.recovery-scan-ms=600000"})
@AutoConfigureMockMvc
class Phase56RedisOutageIntegrationTests extends IsolatedDatabaseSpringTest {
    private static final long USER = 995601L;
    private static final long NOTICE = 995602L;
    private static final long PROJECT = 995603L;
    private static final long REQUIREMENT = 995604L;

    @Autowired private JdbcTemplate db;
    @Autowired private MockMvc mvc;
    @Autowired private MaterialService materials;

    @BeforeEach
    void seed() {
        db.update("INSERT INTO sys_user(user_id,username,password,real_name,role) VALUES (?,?,?,?,?)",
            USER, "phase56-redis-outage", "{noop}pw", "Student", "student");
        db.update("INSERT INTO competition_notice(notice_id,title,created_by,parse_status,publish_status) "
            + "VALUES (?,?,?,?,?)", NOTICE, "Redis outage notice", USER, "PARSED", "PUBLISHED");
        db.update("INSERT INTO competition_project(project_id,notice_id,leader_id,project_name,status) "
            + "VALUES (?,?,?,?,?)", PROJECT, NOTICE, USER, "Redis outage project", "DRAFT");
        db.update("INSERT INTO project_member(project_id,user_id,member_role) VALUES (?,?,?)",
            PROJECT, USER, "leader");
        db.update("INSERT INTO material_requirement(requirement_id,notice_id,requirement_name,is_required,status) "
            + "VALUES (?,?,?,?,?)", REQUIREMENT, NOTICE, "申报书", 1, "ACTIVE");
    }

    @AfterEach
    void cleanup() {
        List<Long> fileIds = db.queryForList("SELECT file_id FROM project_material "
            + "WHERE project_id=? AND file_id IS NOT NULL", Long.class, PROJECT);
        db.update("DELETE FROM project_material WHERE project_id=?", PROJECT);
        for (Long fileId : fileIds) db.update("DELETE FROM file_asset WHERE file_id=?", fileId);
        db.update("DELETE FROM material_requirement WHERE requirement_id=?", REQUIREMENT);
        db.update("DELETE FROM project_member WHERE project_id=?", PROJECT);
        db.update("DELETE FROM competition_project WHERE project_id=?", PROJECT);
        db.update("DELETE FROM competition_notice WHERE notice_id=?", NOTICE);
        db.update("DELETE FROM sys_user WHERE user_id=?", USER);
    }

    @Test
    void dashboardFallsBackAndConcurrentUploadsUseDatabaseGuard() throws Exception {
        var auth = studentAuth();
        mvc.perform(get("/dashboard/bootstrap").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectDetail.projectId").value(PROJECT));

        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var calls = java.util.stream.IntStream.range(0, 2).mapToObj(i -> pool.submit(() -> {
                SecurityContextHolder.getContext().setAuthentication(studentAuth());
                try {
                    start.await();
                    return materials.uploadMaterial(PROJECT, REQUIREMENT, USER, null,
                        new MockMultipartFile("file", "proposal.txt", "text/plain",
                            ("proposal " + i).getBytes()));
                } finally {
                    SecurityContextHolder.clearContext();
                }
            })).toList();
            start.countDown();
            assertEquals(List.of(1, 2), calls.stream().map(call -> {
                try { return call.get(10, TimeUnit.SECONDS).versionNo(); }
                catch (Exception error) { throw new RuntimeException(error); }
            }).sorted().toList());
            assertEquals(2L, db.queryForObject("SELECT COUNT(*) FROM project_material WHERE project_id=?",
                Long.class, PROJECT));
            assertEquals(1L, db.queryForObject("SELECT COUNT(*) FROM project_material WHERE project_id=? "
                + "AND current_version_id=material_id", Long.class, PROJECT));
        } finally {
            pool.shutdownNow();
        }

        db.update("DELETE FROM project_member WHERE project_id=? AND user_id=?", PROJECT, USER);
        mvc.perform(get("/dashboard/bootstrap").with(authentication(auth)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.projectDetail").value(org.hamcrest.Matchers.nullValue()));
        System.out.println("phase56_redis_outage dashboardDbFallback=true uploadVersions=1,2 "
            + "currentVersionCount=1 memberRemovalVisible=true");
    }

    private static UsernamePasswordAuthenticationToken studentAuth() {
        var user = new LoginUser(USER, "phase56-redis-outage", "Student", "student");
        return new UsernamePasswordAuthenticationToken(user, null,
            List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }
}
