import { apiClient, useMockApi } from '@/api/client'
import * as mockService from '@/mock/competitionService'
import { getAuthState } from '@/state/auth'

function currentUserId() { return getAuthState().user?.userId }
function changed() { window.dispatchEvent(new Event('messages-changed')) }

export function listNotifications(params = {}) {
  if (useMockApi) return mockService.getNotifyMessages(currentUserId(), params)
  return apiClient.get('/notify/messages', { params }).then((response) => response.data)
}

export function countUnreadNotifications() {
  if (useMockApi) return mockService.getUnreadCount(currentUserId())
  return apiClient.get('/notify/unread-count').then((response) => response.data)
}

export async function readNotification(msgId) {
  const result = useMockApi
    ? await mockService.markMessageRead(msgId)
    : (await apiClient.put(`/notify/${msgId}/read`)).data
  changed()
  return result
}

export async function readAllNotifications() {
  const result = useMockApi
    ? await mockService.markAllMessagesRead(currentUserId())
    : (await apiClient.put('/notify/read-all')).data
  changed()
  return result
}
