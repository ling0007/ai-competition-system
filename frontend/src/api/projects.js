import { apiClient, useMockApi } from '@/api/client'
import * as mockService from '@/mock/competitionService'
import { getAuthState } from '@/state/auth.js'
import { toIsoLocalDateTime } from '@/utils/format'

export function listMyProjects(params = {}) {
  if (useMockApi) {
    return mockService.getMyProjects({ ...params, userId: getAuthState().user?.userId })
  }
  return apiClient.get('/project/my-projects', { params }).then((response) => response.data)
}

export function getProject(projectId) {
  if (useMockApi) return mockService.getProjectDetail(projectId)
  return apiClient.get(`/project/detail/${projectId}`).then((response) => response.data)
}

export function getProjectProgress(projectId) {
  if (useMockApi) return mockService.getProjectProgress(projectId)
  return apiClient.get(`/project/progress/${projectId}`).then((response) => response.data)
}

export function createStudentProject(payload) {
  const body = {
    ...payload,
    leaderId: getAuthState().user?.userId,
    deadline: payload.deadline ? toIsoLocalDateTime(payload.deadline) : null,
  }
  if (useMockApi) return mockService.createProject(body)
  return apiClient.post('/project/create', body).then((response) => response.data)
}

export function addProjectMember(projectId, payload) {
  if (useMockApi) return mockService.addProjectMember(projectId, payload)
  return apiClient.post(`/project/${projectId}/members`, payload).then((response) => response.data)
}

export function removeProjectMember(projectId, memberId) {
  if (useMockApi) return mockService.removeProjectMember(projectId, memberId)
  return apiClient.delete(`/project/${projectId}/members/${memberId}`).then((response) => response.data)
}

export function submitStudentProject(projectId, status) {
  if (status === 'REVISION_REQUIRED') {
    if (useMockApi) return mockService.resubmitProject(projectId)
    return apiClient.post(`/project/${projectId}/resubmit`).then((response) => response.data)
  }
  if (useMockApi) return mockService.submitProject(projectId)
  return apiClient.post(`/project/${projectId}/submit`).then((response) => response.data)
}

export function listProjectAiChecks(projectId) {
  if (useMockApi) return mockService.getProjectAiChecks(projectId)
  return apiClient.get(`/agent/projects/${projectId}/checks`).then((response) => response.data)
}

export function runProjectAiCheck(projectId) {
  if (useMockApi) return mockService.runMaterialCheck(projectId)
  return apiClient.post(`/agent/check-material/${projectId}`).then((response) => response.data)
}

export function getMaterialTask(taskId) {
  return apiClient.get(`/agent/material-tasks/${taskId}`).then((response) => response.data)
}

export function getLatestMaterialTask(projectId) {
  return apiClient.get(`/agent/projects/${projectId}/material-task/latest`).then((response) => response.data)
}

export function searchUserOptions(role, keyword = '') {
  if (useMockApi) return mockService.searchUserOptions({ role, keyword })
  return apiClient.get('/user/options', { params: { role, keyword } }).then((response) => response.data)
}
