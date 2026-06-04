<script setup>
import { watch } from 'vue'
import { ElMessage } from 'element-plus'
import { downloadFileBlob } from '@/api/competition'

const props = defineProps({
  fileId: {
    type: Number,
    default: null,
  },
  fileName: {
    type: String,
    default: '',
  },
  visible: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['close'])

watch(
  () => [props.visible, props.fileId],
  async ([visible, fileId]) => {
    if (!visible || !fileId) return
    try {
      const blob = await downloadFileBlob(fileId)
      const url = URL.createObjectURL(blob)
      window.open(url, '_blank')
    } catch (error) {
      ElMessage.error('文件下载失败：' + (error?.message || '未知错误'))
    } finally {
      emit('close')
    }
  },
)
</script>

<template>
  <!-- 组件不渲染任何 UI，仅负责下载并在新标签页中打开原文件 -->
  <div style="display: none" />
</template>
