import { apiClient, useMockApi } from '@/api/client'
import * as mockService from '@/mock/competitionService'
import { getAuthState } from '@/state/auth'

export function listMyReviewHistory(params = {}) {
  if (useMockApi) return mockService.getMyReviewHistory({ ...params, userId: getAuthState().user?.userId })
  return apiClient.get('/project/reviews/my-history', { params }).then((response) => response.data)
}
