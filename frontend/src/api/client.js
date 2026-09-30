import axios from 'axios'
import router from '@/router/index.js'
import { logout } from '@/state/auth.js'
import { resolveApiErrorMessage } from '@/api/errors.js'

export const useMockApi = import.meta.env.DEV && import.meta.env.VITE_USE_MOCK === 'true'

// 生产环境 VITE_API_BASE_URL 为空字符串时使用相对路径（同域部署）
// 开发环境未设置时默认使用 localhost:8080
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL != null
  ? import.meta.env.VITE_API_BASE_URL
  : (import.meta.env.PROD ? '' : 'http://localhost:8080')

export const apiClient = axios.create({
  baseURL: apiBaseUrl || undefined,
  timeout: 60000,
})

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('auth_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    // Components own the one visible message for a failed operation; the interceptor
    // normalizes all HTTP/network errors and owns the 401 authentication transition.
    error.userMessage = resolveApiErrorMessage(error)
    if (error.response?.status === 401) {
      logout()
      if (router.currentRoute.value.name !== 'login') {
        router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
      }
    }
    return Promise.reject(error)
  }
)
