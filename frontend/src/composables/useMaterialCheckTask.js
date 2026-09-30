import { onUnmounted, ref } from 'vue'
import { getLatestMaterialTask, getMaterialTask } from '@/api/projects'

const terminal = new Set(['SUCCESS', 'FAILED', 'TIMEOUT'])

export function useMaterialCheckTask({ onSuccess, onFailure } = {}) {
  const task = ref(null)
  let timer = null
  let generation = 0
  let errors = 0
  let acceptedAt = null

  function stop() {
    generation += 1
    if (timer !== null) window.clearTimeout(timer)
    timer = null
  }

  function schedule(id) {
    timer = window.setTimeout(() => poll(id), 1500)
  }

  async function poll(id) {
    const current = generation
    timer = null
    try {
      const response = await getMaterialTask(id)
      if (current !== generation) return
      errors = 0
      task.value = response.data
      if (terminal.has(task.value.status)) {
        stop()
        if (acceptedAt !== null) {
          console.info('material_task_visible', { taskId: id, visibleMs: Math.round(performance.now() - acceptedAt), status: task.value.status })
        }
        if (task.value.status === 'SUCCESS') await onSuccess?.(task.value)
        else await onFailure?.(task.value)
      } else schedule(id)
    } catch {
      if (current !== generation) return
      if (++errors >= 5) {
        stop()
        await onFailure?.({ status: 'QUERY_FAILED', errorSummary: '任务状态查询失败，请刷新页面重试' })
      } else schedule(id)
    }
  }

  function watchTask(accepted) {
    stop()
    errors = 0
    acceptedAt = performance.now()
    task.value = accepted
    schedule(accepted.taskId)
  }

  async function resume(projectId) {
    stop()
    acceptedAt = null
    const current = generation
    const response = await getLatestMaterialTask(projectId)
    if (current !== generation) return
    task.value = response.data
    if (task.value?.taskId && !terminal.has(task.value.status)) schedule(task.value.taskId)
  }

  onUnmounted(stop)
  return { task, watchTask, resume, stop }
}
