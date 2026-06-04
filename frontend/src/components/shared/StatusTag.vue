<script setup>
import { computed } from 'vue'
import { resolveStatusMeta } from '@/utils/status'

const props = defineProps({
  status: {
    type: String,
    default: '',
  },
  label: {
    type: String,
    default: '',
  },
  tone: {
    type: String,
    default: '',
  },
  strong: {
    type: Boolean,
    default: false,
  },
})

const meta = computed(() => resolveStatusMeta(props.status))
const resolvedTone = computed(() => props.tone || meta.value.tagType || 'info')
const resolvedLabel = computed(() => props.label || meta.value.label || props.status || '状态')
</script>

<template>
  <span class="status-tag" :class="[`status-tag--${resolvedTone}`, { 'status-tag--strong': strong }]">
    {{ resolvedLabel }}
  </span>
</template>

<style scoped lang="scss">
.status-tag {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 28px;
  padding: 0 12px;
  border: 1px solid transparent;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
}

.status-tag--primary {
  color: var(--app-info);
  background: var(--app-info-bg);
}

.status-tag--success {
  color: var(--app-success);
  background: var(--app-success-bg);
}

.status-tag--info {
  color: var(--app-text-tertiary);
  background: #f9fafb;
}

.status-tag--warning {
  color: var(--app-warning);
  background: var(--app-warning-bg);
}

.status-tag--danger {
  color: var(--app-danger);
  background: var(--app-danger-bg);
}

.status-tag--strong.status-tag--primary,
.status-tag--strong.status-tag--success {
  font-weight: 600;
}

.status-tag--strong.status-tag--primary {
  color: var(--app-info);
  background: var(--app-info-bg);
}

.status-tag--strong.status-tag--danger {
  color: var(--app-danger);
  background: var(--app-danger-bg);
}

.status-tag--strong.status-tag--warning {
  color: var(--app-warning);
  background: var(--app-warning-bg);
}

.status-tag--strong.status-tag--info {
  color: var(--app-text-secondary);
  background: #f9fafb;
}
</style>
