import axios from 'axios'

export const useMockApi = import.meta.env.VITE_USE_MOCK === 'true'

// 生产环境 VITE_API_BASE_URL 为空字符串时使用相对路径（同域部署）
// 开发环境未设置时默认使用 localhost:8080
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL != null
  ? import.meta.env.VITE_API_BASE_URL
  : 'http://localhost:8080'

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
