<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from 'lucide-vue-next'
import { useRouter } from 'vue-router'
import NoticeUploadPanel from '@/components/dashboard/NoticeUploadPanel.vue'
import { uploadNotice } from '@/api/competition.js'

const router = useRouter()
const uploading = ref(false)

async function handleUpload(payload) {
  uploading.value = true
  try {
    const response = await uploadNotice(payload)
    ElMessage.success('通知已保存，下一步可执行 AI 解析')
    await router.replace(`/admin/notices/${response.data.noticeId}`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '保存通知失败')
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="notice-create-view">
    <el-button :icon="ArrowLeft" @click="router.push('/admin/notices')">返回通知列表</el-button>
    <NoticeUploadPanel
      :loading-upload="uploading"
      :show-parse="false"
      @upload="handleUpload"
    />
  </div>
</template>

<style scoped>
.notice-create-view { display: grid; gap: 20px; }
.notice-create-view > .el-button { justify-self: start; }
</style>
