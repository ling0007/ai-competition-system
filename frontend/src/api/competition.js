import { apiClient, useMockApi } from '@/api/client'
import * as mockService from '@/mock/competitionService'
import { toIsoLocalDateTime } from '@/utils/format'

function buildNoticeFormData(payload) {
  const formData = new FormData()

  if (payload.file) {
    formData.append('file', payload.file)
  }

  if (payload.title) {
    formData.append('title', payload.title)
  }

  if (payload.organizer) {
    formData.append('organizer', payload.organizer)
  }

  if (payload.deadline) {
    formData.append('deadline', toIsoLocalDateTime(payload.deadline))
  }

  if (payload.targetGroup) {
    formData.append('targetGroup', payload.targetGroup)
  }

  if (payload.rawText) {
    formData.append('rawText', payload.rawText)
  }

  formData.append('createdBy', payload.createdBy ?? 1)
  return formData
}

function buildMaterialFormData(payload) {
  const formData = new FormData()
  formData.append('projectId', payload.projectId)
  formData.append('requirementId', payload.requirementId)
  formData.append('uploadedBy', payload.uploadedBy ?? 3)
  formData.append('remark', payload.remark ?? '')
  formData.append('file', payload.file)
  return formData
}

export function getDashboardBootstrap(userId) {
  if (useMockApi) {
    return mockService.getDashboardBootstrap(userId)
  }

  return apiClient.get('/dashboard/bootstrap').then((response) => response.data)
}

export function uploadNotice(payload) {
  if (useMockApi) {
    return mockService.uploadNotice(payload)
  }

  return apiClient
    .post('/notice/upload', buildNoticeFormData(payload), {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((response) => response.data)
}

export function parseNotice(noticeId) {
  if (useMockApi) {
    return mockService.parseNotice(noticeId)
  }

  return apiClient.post(`/notice/parse/${noticeId}`).then((response) => response.data)
}

export function createProject(payload) {
  const requestBody = {
    ...payload,
    deadline: payload.deadline ? toIsoLocalDateTime(payload.deadline) : null,
  }

  if (useMockApi) {
    return mockService.createProject(requestBody)
  }

  return apiClient.post('/project/create', requestBody).then((response) => response.data)
}

export function getProjectDetail(projectId) {
  if (useMockApi) {
    return mockService.getProjectDetail(projectId)
  }

  return apiClient.get(`/project/detail/${projectId}`).then((response) => response.data)
}

export function getProjectProgress(projectId) {
  if (useMockApi) {
    return mockService.getProjectProgress(projectId)
  }

  return apiClient.get(`/project/progress/${projectId}`).then((response) => response.data)
}

export function uploadMaterial(payload) {
  if (useMockApi) {
    return mockService.uploadMaterial(payload)
  }

  return apiClient
    .post('/material/upload', buildMaterialFormData(payload), {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    .then((response) => response.data)
}

export function runMaterialCheck(projectId) {
  if (useMockApi) {
    return mockService.runMaterialCheck(projectId)
  }

  return apiClient.post(`/agent/check-material/${projectId}`).then((response) => response.data)
}

export function addProjectMember(projectId, payload) {
  if (useMockApi) {
    return mockService.addProjectMember(projectId, payload)
  }

  return apiClient.post(`/project/${projectId}/members`, payload).then((response) => response.data)
}

export function removeProjectMember(projectId, memberId) {
  if (useMockApi) {
    return mockService.removeProjectMember(projectId, memberId)
  }

  return apiClient.delete(`/project/${projectId}/members/${memberId}`).then((response) => response.data)
}

export async function downloadFileBlob(fileId) {
  if (useMockApi) {
    // Mock: 返回一个简单的文本 blob 模拟文件
    const blob = new Blob(['这是模拟文件内容。实际环境中将下载原始文件。'], { type: 'text/plain' })
    return blob
  }

  const baseURL = import.meta.env.VITE_API_BASE_URL != null
    ? import.meta.env.VITE_API_BASE_URL
    : 'http://localhost:8080'
  const token = localStorage.getItem('auth_token')
  const response = await fetch(`${baseURL}/file/${fileId}/download`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  if (!response.ok) {
    throw new Error('文件下载失败')
  }
  return response.blob()
}

export function getFileContent(fileId) {
  // 保留兼容性，内部使用 downloadFileBlob
  return downloadFileBlob(fileId)
}

export function getMyProjects() {
  if (useMockApi) {
    return mockService.getMyProjects()
  }

  return apiClient.get('/project/my-projects').then((response) => response.data)
}

export function reviewMaterial(payload) {
  if (useMockApi) {
    return mockService.reviewMaterial(payload)
  }

  return apiClient.post('/material/review', payload).then((response) => response.data)
}

export function getProjectReviewStatus(projectId) {
  if (useMockApi) {
    return mockService.getProjectReviewStatus(projectId)
  }

  return apiClient.get(`/project/${projectId}/review-status`).then((response) => response.data)
}

export function resetMaterialReview(materialId) {
  if (useMockApi) {
    return mockService.resetMaterialReview(materialId)
  }

  return apiClient.post(`/material/${materialId}/reset-review`).then((response) => response.data)
}

export { useMockApi }

// ===== 审计日志 =====

export function getAgentTaskLogs(params = {}) {
  if (useMockApi) {
    return mockService.getAgentTaskLogs(params)
  }

  return apiClient.get('/agent/task-logs', { params }).then((response) => response.data)
}

// ===== 消息中心 =====

export function getNotifyMessages(userId, isRead) {
  if (useMockApi) {
    return mockService.getNotifyMessages(userId, isRead)
  }

  return apiClient.get('/notify/messages', {
    params: { receiverId: userId, isRead },
  }).then((response) => response.data)
}

export function getUnreadCount(userId) {
  if (useMockApi) {
    return mockService.getUnreadCount(userId)
  }

  return apiClient.get('/notify/unread-count', {
    params: { receiverId: userId },
  }).then((response) => response.data)
}

export function markMessageRead(msgId) {
  if (useMockApi) {
    return mockService.markMessageRead(msgId)
  }

  return apiClient.put(`/notify/${msgId}/read`).then((response) => response.data)
}

export function markAllMessagesRead(userId) {
  if (useMockApi) {
    return mockService.markAllMessagesRead(userId)
  }

  return apiClient.put('/notify/read-all', null, {
    params: { receiverId: userId },
  }).then((response) => response.data)
}
