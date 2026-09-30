package com.eliza.aicompetition.security;

import com.eliza.aicompetition.entity.SysUser;
import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器。
 * <p>
 * <b>为什么继承 OncePerRequestFilter？</b>
 * </p>
 * <p>
 * Spring 的 GenericFilterBean / Filter 可能在同一个请求中被多次调用
 * （例如请求转发 forward、include、异步 dispatch 等）。
 * OncePerRequestFilter 保证每个 HTTP 请求只执行一次过滤逻辑，
 * 避免 JWT 解析和数据库查询被重复执行。
 * 这是 Spring Security 官方推荐的 JWT Filter 基类。
 * </p>
 *
 * <p>
 * <b>为什么 JWT Filter 只做"认证"（识别用户是谁），不做"授权"（判断用户能做什么）？</b>
 * </p>
 * <p>
 * 这是 Spring Security 的职责分离设计：
 * <ul>
 *   <li>Filter：从请求中提取身份凭证，构造 Authentication，写入 SecurityContext。
 *       它只回答"这个请求是谁发来的"。</li>
 *   <li>FilterSecurityInterceptor（SecurityConfig 中的 authorizeHttpRequests）：
 *       根据 URL pattern + Authentication.authorities 判断"这个人能否访问这个资源"。</li>
 *   <li>Service 层 / @PreAuthorize：处理更细粒度的业务权限（如"这个人是否是项目成员"）。</li>
 * </ul>
 * 把权限判断混入 Filter 会导致职责不清、难以测试、权限规则分散。
 * </p>
 *
 * <p>
 * <b>Authorization: Bearer token 解析逻辑</b>
 * </p>
 * <ol>
 *   <li>从 HTTP 请求头 Authorization 中读取值</li>
 *   <li>检查是否以 "Bearer " 开头（RFC 6750 标准）</li>
 *   <li>去掉前缀，得到纯 token 字符串</li>
 *   <li>调用 JwtUtil.parseToken() 解析并验证签名+有效期</li>
 *   <li>从 payload 中提取 userId / username / role</li>
 *   <li>查询数据库确认用户存在且未被逻辑删除</li>
 * </ol>
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /**
     * HTTP 请求头中携带 JWT 的标准字段名。
     */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /**
     * Bearer token 前缀（RFC 6750）。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, SysUserMapper sysUserMapper) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 从请求头中提取 token
        String token = extractToken(request);

        if (token != null) {
            try {
                // 2. 解析 JWT，验证签名和有效期
                Long userId = jwtUtil.getUserId(token);
                String username = jwtUtil.getUsername(token);
                String role = jwtUtil.getRole(token);

                // 3. 查询数据库确认用户存在且未被逻辑删除
                //    逻辑删除字段由 MyBatis-Plus @TableLogic 自动过滤，
                //    如果用户被删除，selectById 返回 null
                SysUser user = sysUserMapper.selectById(userId);
                if (user == null) {
                    log.warn("JWT 有效但用户不存在或已删除: userId={}", userId);
                    // 不设置 Authentication，后续由 Spring Security 判定是否需要登录
                    filterChain.doFilter(request, response);
                    return;
                }

                // 4. 构造 LoginUser 作为 Principal
                LoginUser loginUser = new LoginUser(
                        user.getUserId(),
                        user.getUsername(),
                        user.getRealName(),
                        user.getRole()
                );

                /*
                 * 5. 构造 UsernamePasswordAuthenticationToken 并放入 SecurityContext。
                 *
                 * UsernamePasswordAuthenticationToken 的三个参数含义：
                 *  - principal:   当前用户对象（LoginUser），后续通过 getPrincipal() 获取
                 *  - credentials: 密码/凭证，JWT 鉴权模式下不需要，设为 null
                 *  - authorities: 用户拥有的权限列表（ROLE_STUDENT / ROLE_TEACHER / ROLE_ADMIN）
                 *
                 * 为什么要放入 SecurityContextHolder？
                 *  Spring Security 的后续过滤器（FilterSecurityInterceptor）和业务代码
                 *  都通过 SecurityContextHolder.getContext().getAuthentication() 获取当前用户。
                 *  如果不设置，Spring Security 认为"无人登录"，所有需要认证的请求都会被拒绝。
                 *
                 * 使用三个参数的构造器 → 设置 authenticated = true，表示已通过认证。
                 */
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                loginUser,
                                null, // credentials: JWT 模式下不需要密码
                                loginUser.getAuthorities()
                        );

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("JWT 认证成功: userId={}, username={}, role={}", userId, username, role);

            } catch (Exception e) {
                /*
                 * token 解析失败（过期、签名不匹配、格式错误等）。
                 * 同样不在这里直接报错 —— 由 AuthenticationEntryPoint 统一返回 401 JSON。
                 * 这里只清空 SecurityContext 并记录日志。
                 */
                log.warn("JWT 解析失败: {}", e.getMessage());
                SecurityContextHolder.clearContext();
            }
        }
        // 如果没有 token（token == null），不做任何操作。
        // 由 Spring Security 的 FilterSecurityInterceptor 根据 SecurityConfig 中的规则
        // 判断当前 URL 是否需要登录：
        //  - 如果 URL 在 permitAll 白名单中 → 放行
        //  - 否则 → AuthenticationEntryPoint 返回 401

        filterChain.doFilter(request, response);
    }

    /**
     * 从 HTTP 请求头中提取 Bearer token。
     *
     * @return 纯 token 字符串（不含 "Bearer " 前缀），如果没有则返回 null
     */
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) {
            return authHeader.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
