import { createRouter, createWebHistory } from 'vue-router'
import { getAuthState, restoreFromToken } from '@/state/auth.js'
import { getRoleHome, normalizeRole, ROLES } from '@/config/roles.js'
import studentRoutes from '@/router/modules/student.js'
import teacherRoutes from '@/router/modules/teacher.js'
import adminRoutes from '@/router/modules/admin.js'

const routes = [
  { path: '/', redirect: () => getRoleHome(getAuthState().user?.role) },
  { path: '/login', name: 'login', component: () => import('@/views/common/LoginView.vue'), meta: { public: true, guestOnly: true } },
  { path: '/register', name: 'register', component: () => import('@/views/common/RegisterView.vue'), meta: { public: true, guestOnly: true } },
  { path: '/403', name: 'forbidden', component: () => import('@/views/error/ForbiddenView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('@/layouts/AppLayout.vue'),
    children: [
      ...studentRoutes,
      ...teacherRoutes,
      ...adminRoutes,
      {
        path: '/messages',
        name: 'messages',
        component: () => import('@/views/common/WorkspaceView.vue'),
        meta: { roles: [ROLES.STUDENT], menuKey: 'messages', pageKey: 'messages' },
      },
    ],
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/error/NotFoundView.vue'), meta: { public: true } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach((to) => {
  const auth = getAuthState()
  if (!auth.user && auth.token) restoreFromToken()

  if (to.meta.guestOnly && auth.user) return getRoleHome(auth.user.role)
  if (to.meta.public) return true

  if (!auth.token || !auth.user) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  const requiredRoles = [...new Set(to.matched.flatMap((record) => record.meta.roles ?? []))]
  if (requiredRoles.length > 0 && !requiredRoles.includes(normalizeRole(auth.user.role))) {
    return { name: 'forbidden' }
  }

  return true
})

export default router
