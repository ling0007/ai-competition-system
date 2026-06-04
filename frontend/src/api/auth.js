import { apiClient, useMockApi } from '@/api/client'
import * as mockAuthService from '@/mock/authService'

export function login(payload) {
  if (useMockApi) {
    return mockAuthService.login(payload)
  }
  return apiClient.post('/auth/login', payload).then((response) => response.data)
}

export function register(payload) {
  if (useMockApi) {
    return mockAuthService.register(payload)
  }
  return apiClient.post('/auth/register', payload).then((response) => response.data)
}
