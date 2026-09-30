package com.eliza.aicompetition.security;

import com.eliza.aicompetition.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 认证入口点 —— 当未登录用户访问需要认证的资源时触发。
 * <p>
 * <b>401 和 403 的区别</b>
 * </p>
 * <ul>
 *   <li><b>401 Unauthorized</b>："我不知道你是谁"。
 *       没有提供 token、token 无效、token 过期、伪造 token。
 *       由本类 JwtAuthenticationEntryPoint 处理。</li>
 *   <li><b>403 Forbidden</b>："我知道你是谁，但你没权限做这件事"。
 *       已登录但角色不足（如学生访问 /admin/**）。
 *       由 {@link JwtAccessDeniedHandler} 处理。</li>
 * </ul>
 *
 * <p>
 * <b>为什么返回 JSON 而不是 HTML？</b>
 * Spring Security 默认的 AuthenticationEntryPoint 返回 HTML 登录页面。
 * 本系统是前后端分离架构，前端期望 JSON 响应来展示错误提示。
 * 因此必须自定义返回 JSON 格式的 401 响应。
 * </p>
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule()) // 必须注册，否则 LocalDateTime 无法序列化
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 输出 ISO 字符串而非数组

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException)
            throws IOException {

        log.warn("未登录访问: {} {}, reason: {}",
                request.getMethod(), request.getRequestURI(), authException.getMessage());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);           // HTTP 401
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        ApiResponse<Void> apiResponse = ApiResponse.fail(401, "未登录或登录已过期");
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        response.getWriter().flush();
        // 调用 flush() 确保响应被提交（committed），
        // 防止 Tomcat 的 ErrorReportValve 因为检测到 401 状态码而
        // 内部转发到 /error 覆盖我们的 JSON 响应。
    }
}
