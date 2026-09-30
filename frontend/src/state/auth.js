import { reactive } from 'vue'
import { normalizeRole } from '@/config/roles.js'

/**
 * 认证状态模块 —— 全局 reactive 状态管理，替代 Pinia/Vuex。
 *
 * 当前项目只有三个固定角色，不需要引入重量级状态管理库。
 * 该模块从 App.vue 的 currentUser / handleLogin / handleLogout 逻辑提取而来，
 * 供路由守卫、AppLayout、API 拦截器等模块使用。
 *
 * 与现有 localStorage key 'auth_token' 兼容：
 * client.js 的请求拦截器和 App.vue 的 onMounted 都直接读 localStorage，
 * 切换过程中不会破坏现有认证流程。
 */

const TOKEN_KEY = 'auth_token'

const state = reactive({
  token: localStorage.getItem(TOKEN_KEY) || null,
  user: null, // { userId, username, realName, role, phone }
})

/**
 * 从 JWT payload 解析用户信息。
 * 兼容 base64url 编码的 JWT 和 mock 模式下 btoa(JSON.stringify(payload)) 伪 token。
 */
function decodeTokenPayload(token) {
  try {
    const payload = (token.split('.')[1] || token).replace(/-/g, '+').replace(/_/g, '/')
    const paddedPayload = payload.padEnd(Math.ceil(payload.length / 4) * 4, '=')
    const binary = atob(paddedPayload)
    const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes))
  } catch {
    return null
  }
}

/**
 * 登录：写入 token、用户信息到 reactive state 和 localStorage。
 *
 * @param {{ token: string, userId: number, username: string, realName: string, role: string, phone?: string }} userData
 */
export function login(userData) {
  state.token = userData.token
  state.user = {
    userId: userData.userId,
    username: userData.username,
    realName: userData.realName,
    role: normalizeRole(userData.role),
    phone: userData.phone || '',
  }
  localStorage.setItem(TOKEN_KEY, userData.token)
}

/**
 * 退出登录：清除 reactive state 和 localStorage。
 */
export function logout() {
  state.token = null
  state.user = null
  localStorage.removeItem(TOKEN_KEY)
}

export function updateProfile(profile) {
  if (!state.user) return
  state.user.realName = profile.realName ?? state.user.realName
  state.user.phone = profile.phone ?? state.user.phone
}

/**
 * 从 localStorage 中缓存的 token 恢复用户信息。
 * 用于页面刷新后的状态恢复。
 *
 * @returns {boolean} 是否成功恢复
 */
export function restoreFromToken() {
  const token = localStorage.getItem(TOKEN_KEY)
  if (!token) return false

  const payload = decodeTokenPayload(token)
  const expiresAt = payload?.exp > 1_000_000_000_000 ? payload.exp : payload?.exp * 1000
  if (!payload || !expiresAt || expiresAt <= Date.now() || !normalizeRole(payload.role)) {
    logout()
    return false
  }

  state.token = token
  state.user = {
    userId: Number(payload.sub) || payload.userId,
    username: payload.username,
    role: normalizeRole(payload.role),
    realName: '',
    phone: '',
  }
  return true
}

/**
 * 获取当前认证状态（供路由守卫等模块调用）。
 *
 * @returns {{ token: string|null, user: object|null }}
 */
export function getAuthState() {
  return state
}
