import { apiClient, useMockApi } from '@/api/client'
import * as mockService from '@/mock/competitionService'

const MAX_FILE_SIZE = 50 * 1024 * 1024
const ALLOWED_EXTENSIONS = new Set([
  'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'zip', 'png', 'jpg', 'jpeg', 'txt',
])

export function validateMaterialFile(file) {
  if (!file) return '请选择需要上传的文件'
  if (file.size > MAX_FILE_SIZE) return '材料文件不能超过 50MB'
  const extension = file.name.includes('.') ? file.name.split('.').pop().toLowerCase() : ''
  if (!ALLOWED_EXTENSIONS.has(extension)) {
    return '不支持该文件类型，请上传 PDF、Office、TXT、ZIP 或常见图片文件'
  }
  return ''
}

export function uploadProjectMaterial(payload, onProgress) {
  if (useMockApi) return mockService.uploadMaterial(payload)
  const formData = new FormData()
  formData.append('projectId', payload.projectId)
  formData.append('requirementId', payload.requirementId)
  formData.append('remark', payload.remark ?? '')
  formData.append('file', payload.file)
  return apiClient.post('/material/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    onUploadProgress: (event) => {
      if (event.total && onProgress) onProgress(Math.round((event.loaded * 100) / event.total))
    },
  }).then((response) => response.data)
}

export function listMaterialVersions(projectId, requirementId) {
  if (useMockApi) return mockService.getMaterialVersionHistory(projectId, requirementId)
  return apiClient
    .get(`/material/projects/${projectId}/requirements/${requirementId}/versions`)
    .then((response) => response.data)
}
