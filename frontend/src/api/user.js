import { apiClient, useMockApi } from '@/api/client'
import * as mockUserService from '@/mock/userService'

// ===== Profile APIs =====

export function getUserProfile() {
  if (useMockApi) {
    return mockUserService.getUserProfile()
  }
  return apiClient.get('/user/profile').then((response) => response.data)
}

export function updateProfile(payload) {
  if (useMockApi) {
    return mockUserService.updateProfile(payload)
  }
  return apiClient.put('/user/profile', payload).then((response) => response.data)
}

export function changePassword(payload) {
  if (useMockApi) {
    return mockUserService.changePassword(payload)
  }
  return apiClient.put('/user/change-password', payload).then((response) => response.data)
}

// ===== Admin APIs =====

export function getAdminUserList(keyword) {
  if (useMockApi) {
    return mockUserService.getAdminUserList(keyword)
  }
  const params = keyword ? { keyword } : {}
  return apiClient.get('/admin/users', { params }).then((response) => response.data)
}

export function updateUserRole(userId, role) {
  if (useMockApi) {
    return mockUserService.updateUserRole(userId, role)
  }
  return apiClient.put(`/admin/users/${userId}/role`, { role }).then((response) => response.data)
}

export function createUser(payload) {
  if (useMockApi) {
    return mockUserService.createUser(payload)
  }
  return apiClient.post('/admin/users', payload).then((response) => response.data)
}

export function deleteUser(userId) {
  if (useMockApi) {
    return mockUserService.deleteUser(userId)
  }
  return apiClient.delete(`/admin/users/${userId}`).then((response) => response.data)
}
