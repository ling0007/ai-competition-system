import { apiClient, useMockApi } from '@/api/client'
import * as mockService from '@/mock/competitionService'
import { toIsoLocalDateTime } from '@/utils/format'

/**
 * 构建通知上传的 FormData。
 * 注意：不再发送 createdBy 字段，操作人身份由后端从 JWT 中获取。
 */
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

  // P0-2: 不再发送 createdBy，后端从 JWT 获取当前用户
  return formData
}

/**
 * 构建材料上传的 FormData。
 * 注意：不再发送 uploadedBy 字段，操作人身份由后端从 JWT 中获取。
 */
function buildMaterialFormData(payload) {
  const formData = new FormData()
  formData.append('projectId', payload.projectId)
  formData.append('requirementId', payload.requirementId)
  // P0-2: 不再发送 uploadedBy，后端从 JWT 获取当前用户
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

export function replaceNoticeAttachment(noticeId, file) {
  if (useMockApi) {
    return mockService.replaceNoticeAttachment(noticeId, file)
  }

  const formData = new FormData()
  formData.append('file', file)
  return apiClient.put(`/notice/${noticeId}/attachment`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then((response) => response.data)
}

export function parseNotice(noticeId) {
  if (useMockApi) {
    return mockService.parseNotice(noticeId)
  }

  return apiClient.post(`/notice/parse/${noticeId}`).then((response) => response.data)
}

export function getNoticeParseTask(taskId) {
  if (useMockApi) return mockService.getNoticeParseTask(taskId)
  return apiClient.get(`/notice/parse-tasks/${taskId}`).then((response) => response.data)
}

export function getLatestNoticeParseTask(noticeId) {
  if (useMockApi) return mockService.getLatestNoticeParseTask(noticeId)
  return apiClient.get(`/notice/${noticeId}/parse-task/latest`).then((response) => response.data)
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
  const response = await apiClient.get(`/file/${fileId}/download`, { responseType: 'blob' })
  return response.data
}

export function getFileContent(fileId) {
  // 保留兼容性，内部使用 downloadFileBlob
  return downloadFileBlob(fileId)
}

export function getMyProjects(params = {}) {
  if (useMockApi) {
    return mockService.getMyProjects(params)
  }

  return apiClient.get('/project/my-projects', { params }).then((response) => response.data)
}

export function getAllProjects(params = {}) {
  if (useMockApi) {
    return mockService.getAllProjects(params)
  }

  return apiClient.get('/project/list', { params }).then((response) => response.data)
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

// ===== 项目最终审核决定 =====

export function approveProject(projectId) {
  if (useMockApi) {
    return mockService.approveProject(projectId)
  }

  return apiClient.put(`/project/${projectId}/approve`).then((response) => response.data)
}

export function requestRevision(projectId, reason) {
  if (useMockApi) {
    return mockService.requestRevision(projectId, reason)
  }

  return apiClient.put(`/project/${projectId}/request-revision`, null, {
    params: { reason },
  }).then((response) => response.data)
}

export function submitProject(projectId) {
  if (useMockApi) {
    return mockService.submitProject(projectId)
  }

  return apiClient.post(`/project/${projectId}/submit`).then((response) => response.data)
}

export function resubmitProject(projectId) {
  if (useMockApi) {
    return mockService.resubmitProject(projectId)
  }

  return apiClient.post(`/project/${projectId}/resubmit`).then((response) => response.data)
}

// ===== P1-1: 解析草稿管理 =====

export function getParseDraft(noticeId) {
  if (useMockApi) {
    return mockService.getParseDraft(noticeId)
  }

  return apiClient.get(`/notice/${noticeId}/parse-draft`).then((response) => response.data)
}

export function updateParseDraft(noticeId, payload) {
  if (useMockApi) {
    return mockService.updateParseDraft(noticeId, payload)
  }

  return apiClient.put(`/notice/${noticeId}/parse-draft`, payload).then((response) => response.data)
}

export function confirmParse(noticeId) {
  if (useMockApi) {
    return mockService.confirmParse(noticeId)
  }

  return apiClient.post(`/notice/${noticeId}/confirm`, {}).then((response) => response.data)
}

// ===== 通知管理 =====

export function publishNotice(noticeId) {
  if (useMockApi) {
    return mockService.publishNotice(noticeId)
  }

  return apiClient.post(`/notice/${noticeId}/publish`).then((response) => response.data)
}

export function archiveNotice(noticeId) {
  if (useMockApi) {
    return mockService.archiveNotice(noticeId)
  }

  return apiClient.post(`/notice/${noticeId}/archive`).then((response) => response.data)
}

export function listNotices(params = {}) {
  if (useMockApi) {
    return mockService.listNotices(params)
  }

  return apiClient.get('/notice/list', { params }).then((response) => response.data)
}

export function getNoticeDetail(noticeId) {
  if (useMockApi) {
    return mockService.getNoticeDetail(noticeId)
  }

  return apiClient.get(`/notice/${noticeId}`).then((response) => response.data)
}

export { useMockApi }

// ===== 材料列表（P1-4） =====

export function listMaterials(params = {}) {
  if (useMockApi) {
    return mockService.listMaterials(params)
  }

  return apiClient.get('/material/list', { params }).then((response) => response.data)
}

// ===== 审计日志 =====

export function getAgentTaskLogs(params = {}) {
  if (useMockApi) {
    return mockService.getAgentTaskLogs(params)
  }

  return apiClient.get('/agent/task-logs', { params }).then((response) => response.data)
}

// ===== 消息中心 =====

export function getNotifyMessages(userId, params = {}) {
  const queryParams = { receiverId: userId, ...params }
  if (useMockApi) {
    return mockService.getNotifyMessages(userId, queryParams)
  }

  return apiClient.get('/notify/messages', { params: queryParams }).then((response) => response.data)
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
