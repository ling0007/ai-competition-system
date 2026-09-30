package com.eliza.aicompetition;

import com.eliza.aicompetition.controller.AdminController;
import com.eliza.aicompetition.dto.user.UpdateRoleRequest;
import com.eliza.aicompetition.security.LoginUser;
import com.eliza.aicompetition.service.DashboardCacheService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real MySQL commit + Redis epoch + cache-aside acceptance. */
@EnabledIfSystemProperty(named = "phase56.dashboard.epoch", matches = "true")
@SpringBootTest(properties = "notice.ai.recovery-scan-ms=600000")
@AutoConfigureMockMvc
class Phase56DashboardEpochIntegrationTests extends IsolatedDatabaseSpringTest {
    private static final long ADMIN = 995701L;
    private static final long TARGET = 995702L;

    @Autowired private JdbcTemplate db;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private StringRedisTemplate redis;
    @Autowired private AdminController admins;
    @Autowired private DashboardCacheService cache;
    @Autowired private TransactionTemplate transactions;

    @BeforeEach
    void seed() {
        db.update("INSERT INTO sys_user(user_id,username,password,real_name,role) VALUES "
            + "(?,?,?,?,?),(?,?,?,?,?)", ADMIN, "phase56-cache-admin", "{noop}pw", "Admin", "admin",
            TARGET, "phase56-cache-target", "{noop}pw", "Target", "student");
    }

    @AfterEach
    void cleanup() {
        redis.delete("dashboard:bootstrap:" + ADMIN);
        db.update("DELETE FROM sys_user WHERE user_id IN (?,?)", ADMIN, TARGET);
    }

    @Test
    void committedUserChangeAdvancesEpochAndRollbackDoesNot() throws Exception {
        assertEquals("student", targetRole());
        assertNotNull(redis.opsForValue().get("dashboard:bootstrap:" + ADMIN));
        long before = cache.epoch();
        assertEquals("student", targetRole());

        var request = new UpdateRoleRequest();
        request.setRole("teacher");
        admins.updateRole(TARGET, request);
        assertEquals(before + 1, cache.epoch());
        assertEquals("teacher", targetRole());
        assertEquals(before + 1, json.readTree(redis.opsForValue()
            .get("dashboard:bootstrap:" + ADMIN)).path("_epoch").asLong());

        transactions.executeWithoutResult(tx -> {
            cache.invalidateGlobalAfterCommit("phase56-rollback");
            tx.setRollbackOnly();
        });
        assertEquals(before + 1, cache.epoch());
        System.out.println("phase56_dashboard committedEpochIncrement=1 rollbackIncrement=0 "
            + "nextReadRole=teacher");
    }

    private String targetRole() throws Exception {
        var login = new LoginUser(ADMIN, "phase56-cache-admin", "Admin", "admin");
        var auth = new UsernamePasswordAuthenticationToken(login, null,
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        var response = mvc.perform(get("/dashboard/bootstrap").with(authentication(auth)))
            .andExpect(status().isOk()).andReturn();
        for (var option : json.readTree(response.getResponse().getContentAsString())
                .path("data").path("userOptions")) {
            if (option.path("value").asLong() == TARGET) return option.path("role").asText();
        }
        fail("Target user missing from Dashboard options");
        return null;
    }
}
