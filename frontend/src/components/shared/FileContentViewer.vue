<script setup>
import { onUnmounted, ref, watch } from 'vue'
import { downloadFileBlob } from '@/api/competition'
import { resolveApiErrorMessage } from '@/api/errors'

const props = defineProps({
  fileId: { type: Number, default: null },
  fileName: { type: String, default: '' },
  visible: { type: Boolean, default: false },
})
const emit = defineEmits(['close'])
const objectUrl = ref(null)
const previewable = ref(false)
const loading = ref(false)
const errorMessage = ref('')
let requestId = 0

function release() {
  if (objectUrl.value) URL.revokeObjectURL(objectUrl.value)
  objectUrl.value = null
}

watch(() => [props.visible, props.fileId], async ([visible, fileId]) => {
  const current = ++requestId
  release()
  errorMessage.value = ''
  if (!visible || !fileId) return
  loading.value = true
  try {
    const blob = await downloadFileBlob(fileId)
    if (current !== requestId) return
    previewable.value = /^(application\/pdf|image\/(png|jpeg|gif|webp)|text\/plain)$/.test(blob.type)
    objectUrl.value = URL.createObjectURL(blob)
  } catch (error) {
    if (current === requestId) errorMessage.value = resolveApiErrorMessage(error, '文件加载失败')
  } finally {
    if (current === requestId) loading.value = false
  }
}, { immediate: true })
onUnmounted(() => { requestId++; release() })
</script>

<template>
  <el-dialog :model-value="visible" :title="fileName || '查看文件'" width="min(900px, 95vw)" @close="emit('close')">
    <div v-loading="loading" class="file-viewer">
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" />
      <iframe v-else-if="objectUrl && previewable" :src="objectUrl" :title="fileName || '文件预览'" />
      <el-empty v-else-if="objectUrl" description="此类型暂不支持页面预览，请下载后查看。" />
    </div>
    <template #footer>
      <a v-if="objectUrl" :href="objectUrl" :download="fileName || '附件'" class="file-viewer__download">下载文件</a>
      <el-button @click="emit('close')">关闭</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.file-viewer { min-height: 220px; }
.file-viewer iframe { display: block; width: 100%; height: min(70vh, 680px); border: 0; }
.file-viewer__download { margin-right: 12px; color: var(--app-primary); }
</style>
