/**
 * 角色菜单配置。
 *
 * 菜单项定义从 App.vue 的 studentMenuItems / teacherMenuItems 提取，
 * 新增管理员菜单组。
 *
 * @typedef {{ key: string, label: string, icon: object, route: string, roles: string[], badgeKey?: string }} MenuItem
 *
 * icon 使用 lucide-vue-next 组件引用（与现有 App.vue 一致）。
 * route 字段在 P1 阶段 C（创建页面）时用于 router-link 跳转。
 */

import {
  Bell,
  ClipboardCheck,
  Files,
  Newspaper,
  School,
  ScrollText,
  User,
  Cpu,
} from 'lucide-vue-next'
import { normalizeRole, ROLES } from '@/config/roles.js'

export const menuItems = [
  // ==================== 学生端 ====================
  { key: 'noticeList', label: '申报大厅', icon: Newspaper, route: '/student/notices', roles: [ROLES.STUDENT] },
  { key: 'project', label: '我的项目', icon: Files, route: '/student/projects', roles: [ROLES.STUDENT] },
  { key: 'messages', label: '消息中心', icon: Bell, route: '/messages', roles: [ROLES.STUDENT] },

  // ==================== 教师端 ====================
  { key: 'overview', label: '审核工作台', icon: School, route: '/teacher/dashboard', roles: [ROLES.TEACHER] },
  { key: 'material', label: '审核区', icon: ClipboardCheck, route: '/teacher/reviews/pending', roles: [ROLES.TEACHER] },
  { key: 'noticeList', label: '通知列表', icon: Newspaper, route: '/teacher/notices', roles: [ROLES.TEACHER] },
  { key: 'logs', label: '审核记录', icon: ScrollText, route: '/teacher/reviews/history', roles: [ROLES.TEACHER] },

  // ==================== 管理员端 ====================
  { key: 'users', label: '用户与角色', icon: User, route: '/admin/users', roles: [ROLES.ADMIN] },
  { key: 'notices', label: '通知管理与解析', icon: Newspaper, route: '/admin/notices', roles: [ROLES.ADMIN] },
  { key: 'projects', label: '项目总览与审核', icon: Files, route: '/admin/projects', roles: [ROLES.ADMIN] },
  { key: 'aiTasks', label: 'AI 任务', icon: Cpu, route: '/admin/ai-tasks', roles: [ROLES.ADMIN] },
]

/**
 * 按角色筛选菜单项。
 *
 * @param {string} role - 'student' | 'teacher' | 'admin'
 * @returns {MenuItem[]}
 */
export function getMenuItemsByRole(role) {
  const normalizedRole = normalizeRole(role)
  return menuItems.filter((item) => item.roles.includes(normalizedRole))
}
