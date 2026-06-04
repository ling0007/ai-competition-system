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

export function getDashboardBootstrap() {
  if (useMockApi) {
    return mockService.getDashboardBootstrap()
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

export { useMockApi }
