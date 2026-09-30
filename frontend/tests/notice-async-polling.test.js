import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'

const api = vi.hoisted(() => ({ getNoticeParseTask: vi.fn(), getLatestNoticeParseTask: vi.fn() }))
vi.mock('../src/api/competition.js', () => api)
import { useNoticeParseTask } from '../src/composables/useNoticeParseTask.js'

function harness(callbacks = {}) {
  let state
  const wrapper = mount(defineComponent({
    setup() {
      state = useNoticeParseTask(callbacks)
      return () => null
    },
  }))
  return { wrapper, state }
}

describe('notice async polling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    api.getNoticeParseTask.mockReset()
    api.getLatestNoticeParseTask.mockReset()
  })
  afterEach(() => vi.useRealTimers())

  it('polls only nonterminal tasks and stops after success', async () => {
    const success = vi.fn()
    const { wrapper, state } = harness({ onSuccess: success })
    api.getNoticeParseTask
      .mockResolvedValueOnce({ data: { taskId: 7, status: 'RUNNING' } })
      .mockResolvedValueOnce({ data: { taskId: 7, status: 'SUCCESS', resultOrigin: 'FALLBACK' } })
    state.watchTask({ taskId: 7, noticeId: 3, status: 'PENDING' })
    await vi.advanceTimersByTimeAsync(3000)
    expect(api.getNoticeParseTask).toHaveBeenCalledTimes(2)
    expect(success).toHaveBeenCalledWith(expect.objectContaining({ resultOrigin: 'FALLBACK' }))
    expect(vi.getTimerCount()).toBe(0)
    wrapper.unmount()
  })

  it('resumes an active task after refresh and stops when view unmounts', async () => {
    api.getLatestNoticeParseTask.mockResolvedValue({ data: { taskId: 8, status: 'PENDING' } })
    const { wrapper, state } = harness()
    await state.resume(4)
    expect(state.task.value.taskId).toBe(8)
    expect(vi.getTimerCount()).toBe(1)
    wrapper.unmount()
    await vi.advanceTimersByTimeAsync(3000)
    expect(api.getNoticeParseTask).not.toHaveBeenCalled()
  })
})
