import { ROLES } from '@/config/roles.js'

export default [
  { path: '/student/dashboard', redirect: '/student/projects', meta: { roles: [ROLES.STUDENT] } },
  { path: '/student/notices', name: 'student-notices', component: () => import('@/views/student/NoticeListView.vue'), meta: { roles: [ROLES.STUDENT], menuKey: 'noticeList' } },
  { path: '/student/notices/:noticeId', name: 'student-notice-detail', component: () => import('@/views/student/NoticeDetailView.vue'), meta: { roles: [ROLES.STUDENT], menuKey: 'noticeList' } },
  { path: '/student/projects', name: 'student-projects', component: () => import('@/views/student/ProjectListView.vue'), meta: { roles: [ROLES.STUDENT], menuKey: 'project' } },
  { path: '/student/projects/new', name: 'student-project-create', component: () => import('@/views/student/ProjectCreateView.vue'), meta: { roles: [ROLES.STUDENT], menuKey: 'project' } },
  { path: '/student/projects/:projectId', name: 'student-project-detail', component: () => import('@/views/student/ProjectWorkspaceView.vue'), meta: { roles: [ROLES.STUDENT], menuKey: 'project' } },
  { path: '/student/materials', redirect: '/student/projects', meta: { roles: [ROLES.STUDENT] } },
]
