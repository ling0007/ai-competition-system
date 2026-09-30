import { onUnmounted, ref } from 'vue'
import { getLatestNoticeParseTask, getNoticeParseTask } from '@/api/competition.js'

const terminal = new Set(['SUCCESS', 'FAILED', 'TIMEOUT'])

export function useNoticeParseTask({ onSuccess, onFailure } = {}) {
  const task = ref(null)
  let timer = null
  let generation = 0
  let acceptedAt = null
  let failures = 0

  function stop() {
    generation += 1
    if (timer !== null) window.clearTimeout(timer)
    timer = null
  }

  function schedule(id) {
    if (timer !== null) window.clearTimeout(timer)
    timer = window.setTimeout(() => poll(id), 1500)
  }

  async function poll(id) {
    const current = generation
    timer = null
    try {
      const response = await getNoticeParseTask(id)
      if (current !== generation) return
      failures = 0
      task.value = response.data
      if (terminal.has(task.value.status)) {
        stop()
        if (acceptedAt !== null) {
          console.info('notice_task_visible', { taskId: id, visibleMs: Math.round(performance.now() - acceptedAt), status: task.value.status })
        }
        if (task.value.status === 'SUCCESS') await onSuccess?.(task.value)
        else await onFailure?.(task.value)
      } else {
        schedule(id)
      }
    } catch {
      if (current !== generation) return
      failures += 1
      if (failures >= 5) {
        stop()
        await onFailure?.({ status: 'QUERY_FAILED', errorSummary: '任务状态查询失败，请刷新页面重试' })
      } else schedule(id)
    }
  }

  function watchTask(accepted) {
    stop()
    failures = 0
    acceptedAt = performance.now()
    task.value = { taskId: accepted.taskId, businessId: accepted.noticeId, status: accepted.status }
    schedule(accepted.taskId)
  }

  async function resume(noticeId) {
    stop()
    acceptedAt = null
    const current = generation
    const response = await getLatestNoticeParseTask(noticeId)
    if (current !== generation) return
    task.value = response.data
    if (task.value?.taskId && !terminal.has(task.value.status)) schedule(task.value.taskId)
  }

  onUnmounted(stop)
  return { task, watchTask, resume, stop }
}
