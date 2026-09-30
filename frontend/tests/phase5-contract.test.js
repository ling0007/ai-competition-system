import { describe, expect, it } from 'vitest'
import { login, logout } from '../src/state/auth.js'
import * as mock from '../src/mock/competitionService.js'
import { resolveApiErrorMessage } from '../src/api/errors.js'
import { projectMessageTarget } from '../src/utils/messageLinks.js'

function asUser(userId, role) {
  login({ token: `test-${userId}`, userId, username: `test-${userId}`, realName: '测试用户', role })
}

describe('Phase 5 user-facing contracts', () => {
  it('maps server failures and message links without guessing a project route', () => {
    expect(resolveApiErrorMessage({ response: { status: 409, data: { message: '版本已变化' } } })).toBe('版本已变化')
    expect(resolveApiErrorMessage({ response: { status: 403 } })).toBe('无权执行该操作')
    expect(resolveApiErrorMessage({ isAxiosError: true })).toContain('网络连接失败')
    expect(projectMessageTarget('student', 7)).toBe('/student/projects/7')
    expect(projectMessageTarget('teacher', 7)).toBe('/teacher/projects/7/review')
    expect(projectMessageTarget('admin', 7)).toBe('/admin/projects/7')
    expect(projectMessageTarget('teacher', null)).toBeNull()
  })

  it('keeps mock advisor queues isolated and returns HTTP-like failures', async () => {
    asUser(2, 'teacher')
    const own = await mock.getMyProjects({ userId: 2, status: 'UNDER_REVIEW' })
    expect(own.data.total).toBe(0)
    await expect(mock.getProjectDetail(2)).rejects.toMatchObject({ response: { status: 403 } })

    asUser(8, 'teacher')
    const assigned = await mock.getMyProjects({ userId: 8, status: 'UNDER_REVIEW' })
    expect(assigned.data.records.map((item) => item.projectId)).toEqual([2])
    await expect(mock.approveProject(2)).rejects.toMatchObject({ response: { status: 409 } })
    const history = await mock.getMyReviewHistory({ userId: 8 })
    expect(history.data.records.every((record) => record.projectId === 2 || record.projectId === 5)).toBe(true)
    logout()
  })

  it('separates stale AI snapshots from current material completeness', async () => {
    asUser(3, 'student')
    const progress = await mock.getProjectProgress(1)
    const checks = await mock.getProjectAiChecks(1)
    expect(progress.data.completionRate).toBe(100)
    expect(checks.data[0].stale).toBe(true)
    await expect(mock.submitProject(3)).rejects.toMatchObject({ response: { status: 403 } })
    logout()
  })

  it('does not expose or mark another user’s notifications in Mock mode', async () => {
    asUser(3, 'student')
    await expect(mock.publishNotice(2)).rejects.toMatchObject({ response: { status: 403 } })
    await expect(mock.getNotifyMessages(4)).rejects.toMatchObject({ response: { status: 403 } })
    await expect(mock.getUnreadCount(4)).rejects.toMatchObject({ response: { status: 403 } })
    await expect(mock.markMessageRead(7)).rejects.toMatchObject({ response: { status: 403 } })
    await expect(mock.markAllMessagesRead(4)).rejects.toMatchObject({ response: { status: 403 } })
    logout()
  })
})
