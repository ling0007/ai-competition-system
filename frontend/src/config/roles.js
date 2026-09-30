/**
 * 前端运行时角色领域值。
 *
 * 后端 JWT、登录响应、路由 meta、菜单和动作权限统一使用小写值；
 * ROLE_STUDENT 等 Spring Security authority 只存在于后端鉴权层。
 */
export const ROLES = Object.freeze({
  STUDENT: 'student',
  TEACHER: 'teacher',
  ADMIN: 'admin',
})

export const ROLE_LABELS = Object.freeze({
  [ROLES.STUDENT]: '学生',
  [ROLES.TEACHER]: '教师',
  [ROLES.ADMIN]: '管理员',
})

const KNOWN_ROLES = new Set(Object.values(ROLES))

/**
 * 将登录响应或 JWT 中的角色归一化为前端领域值。
 * 未知角色返回空字符串，确保路由和权限检查默认拒绝。
 */
export function normalizeRole(role) {
  const normalized = typeof role === 'string' ? role.trim().toLowerCase() : ''
  return KNOWN_ROLES.has(normalized) ? normalized : ''
}

export function getRoleLabel(role) {
  return ROLE_LABELS[normalizeRole(role)] ?? '未知角色'
}

export function getRoleHome(role) {
  return {
    [ROLES.STUDENT]: '/student/dashboard',
    [ROLES.TEACHER]: '/teacher/dashboard',
    [ROLES.ADMIN]: '/admin/users',
  }[normalizeRole(role)] ?? '/login'
}
