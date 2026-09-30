package com.eliza.aicompetition.config;

import com.eliza.aicompetition.mapper.SysUserMapper;
import com.eliza.aicompetition.security.JwtAccessDeniedHandler;
import com.eliza.aicompetition.security.JwtAuthenticationEntryPoint;
import com.eliza.aicompetition.security.JwtAuthenticationFilter;
import com.eliza.aicompetition.util.JwtUtil;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 安全配置。
 *
 * <p>
 * <b>为什么 Session 设置为 STATELESS？</b>
 * </p>
 * <p>
 * JWT（JSON Web Token）是无状态认证机制：每次请求携带 token，服务端不存储会话。
 * SessionCreationPolicy.STATELESS 告诉 Spring Security：
 * <ul>
 *   <li>不要创建 HttpSession（节省服务器内存）</li>
 *   <li>不要从 Session 中读取 SecurityContext（每次请求由 JWT Filter 重新构建）</li>
 *   <li>不要使用 CSRF 保护（CSRF 依赖 Session，无 Session → 无 CSRF 风险）</li>
 * </ul>
 * 这也是前后端分离架构的标准配置。
 * </p>
 *
 * <p>
 * <b>ROLE_ 前缀与 hasRole / hasAuthority 的关系</b>
 * </p>
 * <ul>
 *   <li>{@code hasRole("ADMIN")} —— Spring Security 自动给参数加 ROLE_ 前缀，实际匹配 ROLE_ADMIN</li>
 *   <li>{@code hasAuthority("ROLE_ADMIN")} —— 精确匹配，不做前缀处理</li>
 *   <li>本项目中，LoginUser 构造时已添加 ROLE_ 前缀（ROLE_STUDENT / ROLE_TEACHER / ROLE_ADMIN），
 *       因此 SecurityConfig 中使用 hasRole() 即可</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtUtil jwtUtil;
    private final SysUserMapper sysUserMapper;
    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtUtil jwtUtil,
                          SysUserMapper sysUserMapper,
                          JwtAuthenticationEntryPoint authenticationEntryPoint,
                          JwtAccessDeniedHandler accessDeniedHandler) {
        this.jwtUtil = jwtUtil;
        this.sysUserMapper = sysUserMapper;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT 认证过滤器 Bean。
     * 作为独立 Bean 注册（而非直接在 filterChain 中 new），
     * 避免被 Spring 自动加入 FilterChainProxy 导致重复执行。
     */
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtUtil, sysUserMapper);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 关闭 CSRF —— 前后端分离 + 无状态 JWT，不存在 CSRF 攻击面
            .csrf(csrf -> csrf.disable())

            // 开启 CORS —— 使用 WebMvcConfig 中配置的跨域规则
            .cors(cors -> {})

            /*
             * 无状态 Session 策略。
             * 每次请求独立认证，服务端不维护 Session。
             */
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ========== 权限规则 ==========
            .authorizeHttpRequests(auth -> auth
                    /*
                     * 白名单：不需要登录即可访问。
                     * - /auth/login   : 登录
                     * - /auth/register: 注册
                     * - /health       : 健康检查（Docker / K8s 探针）
                     * - Knife4j/Swagger 文档路径：开发阶段放行，方便前后端联调
                     */
                    .requestMatchers("/auth/login", "/auth/register", "/health").permitAll()
                    // Docker 镜像内置的 Vue 前端必须在登录前即可加载。
                    .requestMatchers("/", "/index.html", "/favicon.ico", "/favicon.svg", "/assets/**").permitAll()
                    .requestMatchers("/doc.html", "/v3/api-docs/**", "/webjars/**", "/swagger-resources/**").permitAll()
                    /*
                     * /error 必须放行：Spring Boot 的 ErrorMvcAutoConfiguration 在遇到异常状态码时
                     * 会内部转发到 /error，如果 /error 也需要认证，会导致 401/403 JSON 被 Tomcat
                     * 的 HTML 错误页覆盖。
                     */
                    .requestMatchers("/error").permitAll()

                    /*
                     * OPTIONS 预检请求放行：浏览器 CORS 预检不携带 token，必须放行。
                     */
                    .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                    /*
                     * 通知管理接口：仅 ADMIN 角色可操作。
                     * 包括上传通知、触发 AI 解析。
                     */
                    .requestMatchers("/notice/upload", "/notice/parse/**").hasRole("ADMIN")

                    /*
                     * 材料审核接口：仅 TEACHER 或 ADMIN 角色可操作。
                     * hasAnyRole 表示任意一个角色即可（OR 关系）。
                     */
                    .requestMatchers("/material/review", "/material/*/reset-review").hasAnyRole("TEACHER", "ADMIN")

                    /*
                     * 管理员接口：仅 ADMIN 角色可访问。
                     * hasRole("ADMIN") → 实际匹配 authority "ROLE_ADMIN"。
                     */
                    .requestMatchers("/admin/**").hasRole("ADMIN")

                    /*
                     * 其他所有业务接口：必须登录。
                     * 不再使用 anyRequest().permitAll() —— 这是本次改造的核心变更。
                     */
                    .anyRequest().authenticated()
            )

            /*
             * 注册 JWT 认证过滤器。
             * 放在 UsernamePasswordAuthenticationFilter 之前，确保在 Spring Security
             * 的默认认证机制之前先执行 JWT 解析。
             */
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class)

            /*
             * 注册 401 / 403 自定义处理器。
             * 覆盖 Spring Security 默认的 HTML 响应，返回 JSON 格式错误信息。
             */
            .exceptionHandling(exceptions -> exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)   // 401: 未登录
                    .accessDeniedHandler(accessDeniedHandler)             // 403: 无权限
            );

        return http.build();
    }
}
