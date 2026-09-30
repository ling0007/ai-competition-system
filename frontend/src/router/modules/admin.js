import { ROLES } from '@/config/roles.js'

const workspace = () => import('@/views/common/WorkspaceView.vue')

export default [
  { path: '/admin/dashboard', redirect: '/admin/users', meta: { roles: [ROLES.ADMIN] } },
  { path: '/admin/notices', name: 'admin-notices', component: () => import('@/views/admin/NoticeListView.vue'), meta: { roles: [ROLES.ADMIN], menuKey: 'notices' } },
  { path: '/admin/notices/new', name: 'admin-notice-create', component: () => import('@/views/admin/NoticeCreateView.vue'), meta: { roles: [ROLES.ADMIN], menuKey: 'notices' } },
  { path: '/admin/notices/:noticeId', name: 'admin-notice-detail', component: () => import('@/views/admin/NoticeDetailView.vue'), meta: { roles: [ROLES.ADMIN], menuKey: 'notices' } },
  { path: '/admin/projects', name: 'admin-projects', component: workspace, meta: { roles: [ROLES.ADMIN], menuKey: 'projects', pageKey: 'admin-projects' } },
  { path: '/admin/projects/:projectId', name: 'admin-project-detail', component: workspace, meta: { roles: [ROLES.ADMIN], menuKey: 'projects', pageKey: 'admin-project-detail' } },
  { path: '/admin/users', name: 'admin-users', component: workspace, meta: { roles: [ROLES.ADMIN], menuKey: 'users', pageKey: 'admin-users' } },
  { path: '/admin/ai-tasks', name: 'admin-ai-tasks', component: workspace, meta: { roles: [ROLES.ADMIN], menuKey: 'aiTasks', pageKey: 'admin-ai-tasks' } },
]
