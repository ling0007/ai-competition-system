import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'

const api = vi.hoisted(() => ({ getMaterialTask: vi.fn(), getLatestMaterialTask: vi.fn() }))
vi.mock('../src/api/projects.js', () => api)
import { useMaterialCheckTask } from '../src/composables/useMaterialCheckTask.js'

function harness(callbacks = {}) {
  let state
  const wrapper = mount(defineComponent({
    setup() {
      state = useMaterialCheckTask(callbacks)
      return () => null
    },
  }))
  return { wrapper, state }
}

describe('material async polling', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    api.getMaterialTask.mockReset()
    api.getLatestMaterialTask.mockReset()
  })
  afterEach(() => vi.useRealTimers())

  it('resumes after refresh and stops at a fallback success', async () => {
    const success = vi.fn()
    api.getLatestMaterialTask.mockResolvedValue({ data: { taskId: 9, status: 'RUNNING' } })
    api.getMaterialTask.mockResolvedValue({ data: { taskId: 9, status: 'SUCCESS', resultOrigin: 'FALLBACK' } })
    const { wrapper, state } = harness({ onSuccess: success })
    await state.resume(3)
    await vi.advanceTimersByTimeAsync(1500)
    expect(success).toHaveBeenCalledWith(expect.objectContaining({ resultOrigin: 'FALLBACK' }))
    expect(vi.getTimerCount()).toBe(0)
    wrapper.unmount()
  })

  it('stops on unmount and bounds query errors', async () => {
    const failed = vi.fn()
    api.getMaterialTask.mockRejectedValue(new Error('offline'))
    const { wrapper, state } = harness({ onFailure: failed })
    state.watchTask({ taskId: 10, projectId: 3, status: 'PENDING' })
    await vi.advanceTimersByTimeAsync(7500)
    expect(api.getMaterialTask).toHaveBeenCalledTimes(5)
    expect(failed).toHaveBeenCalledWith(expect.objectContaining({ status: 'QUERY_FAILED' }))
    expect(vi.getTimerCount()).toBe(0)
    wrapper.unmount()
  })
})
