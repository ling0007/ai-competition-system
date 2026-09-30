package com.eliza.aicompetition.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 统一获取当前登录用户的工具类。
 * <p>
 * 从 Spring Security 的 {@link SecurityContextHolder} 中读取
 * {@link Authentication#getPrincipal()}（即 {@link LoginUser}），
 * 提供静态快捷方法供 Controller / Service 使用。
 * </p>
 *
 * <p>
 * <b>使用示例</b>
 * <pre>{@code
 * // Controller 中
 * LoginUser currentUser = SecurityUtils.getCurrentUser();
 * Long userId = SecurityUtils.getCurrentUserId();
 *
 * // Service 中
 * SysUser operator = SecurityUtils.getCurrentUser();
 * log.info("用户 {} 执行了操作", operator.getUsername());
 * }</pre>
 * </p>
 *
 * <p>
 * 相比之前每个 Controller 各自调用 {@code jwtUtil.parseToken()} + {@code sysUserMapper.selectById()}，
 * 统一使用本类的优势：
 * <ul>
 *   <li>零数据库查询 —— 用户信息已在 {@link JwtAuthenticationFilter} 中查好并缓存到 SecurityContext</li>
 *   <li>一处修改、全局生效 —— 修改 Principal 结构只需改 LoginUser</li>
 *   <li>线程安全 —— SecurityContextHolder 默认使用 ThreadLocal 存储，天然隔离</li>
 * </ul>
 * </p>
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // 工具类，禁止实例化
    }

    /**
     * 获取当前登录用户完整信息。
     *
     * @return 当前登录的 LoginUser
     * @throws SecurityException 如果当前请求未登录
     */
    public static LoginUser getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SecurityException("未登录或登录已过期");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof LoginUser)) {
            throw new SecurityException("当前认证类型不是 LoginUser，可能是配置错误");
        }
        return (LoginUser) principal;
    }

    /**
     * 获取当前登录用户 ID。
     */
    public static Long getCurrentUserId() {
        return getCurrentUser().getUserId();
    }

    /**
     * 获取当前登录用户名。
     */
    public static String getCurrentUsername() {
        return getCurrentUser().getUsername();
    }

    /**
     * 获取当前登录用户角色（如 "student", "teacher", "admin"）。
     */
    public static String getCurrentUserRole() {
        return getCurrentUser().getRole();
    }

    /**
     * 判断当前请求是否已登录。
     * <p>
     * 用于允许匿名访问的接口中，需要根据是否登录做不同处理的场景。
     */
    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof LoginUser;
    }
}
