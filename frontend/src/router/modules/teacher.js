import { ROLES } from '@/config/roles.js'

const workspace = () => import('@/views/common/WorkspaceView.vue')
const queue = () => import('@/views/teacher/ReviewQueueView.vue')
const projectReview = () => import('@/views/teacher/ProjectReviewView.vue')
const history = () => import('@/views/teacher/ReviewHistoryView.vue')

export default [
  { path: '/teacher/dashboard', name: 'teacher-dashboard', component: queue, meta: { roles: [ROLES.TEACHER], menuKey: 'overview' } },
  { path: '/teacher/reviews/pending', name: 'teacher-pending-reviews', component: queue, meta: { roles: [ROLES.TEACHER], menuKey: 'material' } },
  { path: '/teacher/notices', name: 'teacher-notices', component: workspace, meta: { roles: [ROLES.TEACHER], menuKey: 'noticeList', pageKey: 'teacher-projects' } },
  { path: '/teacher/projects', redirect: '/teacher/notices' },
  { path: '/teacher/reviews/history', name: 'teacher-review-history', component: history, meta: { roles: [ROLES.TEACHER], menuKey: 'logs' } },
  { path: '/teacher/projects/:projectId/review', name: 'teacher-project-review', component: projectReview, meta: { roles: [ROLES.TEACHER], menuKey: 'material' } },
]
