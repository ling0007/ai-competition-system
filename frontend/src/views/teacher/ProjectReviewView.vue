<script setup>
import { ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import MaterialReviewPanel from '@/components/dashboard/MaterialReviewPanel.vue'
import { getProject } from '@/api/projects'
import { getProjectReviewStatus } from '@/api/competition'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const project = ref(null)
const currentMaterials = ref([])
const loading = ref(false)
const errorMessage = ref('')

async function load() {
  project.value = null
  loading.value = true
  errorMessage.value = ''
  try {
    const id = Number(route.params.projectId)
    if (!Number.isSafeInteger(id) || id <= 0) throw new Error('无效的项目地址')
    const [detail, status] = await Promise.all([getProject(id), getProjectReviewStatus(id)])
    project.value = detail.data
    currentMaterials.value = status.data ?? []
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || error?.message || '项目加载失败'
    ElMessage.error(errorMessage.value)
  } finally { loading.value = false }
}
watch(() => route.params.projectId, load, { immediate: true })

function updated(detail) {
  project.value = detail
  load()
}
</script>

<template>
  <div v-loading="loading" class="review-workspace">
    <el-button @click="router.push('/teacher/reviews/pending')">← 返回审核队列</el-button>
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false">
      <el-button @click="load">重试</el-button>
    </el-alert>
    <template v-if="project">
      <FeaturePanel :title="project.projectName" subtitle="项目审核工作区 · 当前材料版本与项目决定由后端确认">
        <div class="review-summary">
          <span>关联通知：{{ project.noticeTitle }}</span>
          <span>团队：{{ project.teamName || '未填写' }}</span>
          <span>负责人：{{ project.leaderName }}</span>
          <span>截止：{{ formatDateTime(project.deadline) }}</span>
          <span>项目状态：{{ project.status }}</span>
        </div>
      </FeaturePanel>
      <MaterialReviewPanel :project="{ ...project, materials: currentMaterials }" @project-updated="updated" />
      <FeaturePanel title="项目时间线" subtitle="学生提交、材料审核及项目决定的审计记录；旧版本意见不会改变当前审核状态。">
        <el-timeline v-if="project.reviewRecords?.length">
          <el-timeline-item v-for="record in project.reviewRecords" :key="record.reviewId" :timestamp="formatDateTime(record.createdAt)">
            <strong>{{ record.reviewerName || '系统' }} · {{ record.reviewResult }}</strong>
            <p>{{ record.reviewComment || record.reviewType }}</p>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无审核记录；提交后的决定会显示在此处。" />
      </FeaturePanel>
    </template>
  </div>
</template>

<style scoped>
.review-workspace { display: grid; gap: 18px; }
.review-summary { display: flex; flex-wrap: wrap; gap: 12px 24px; color: var(--app-text-secondary); }
</style>
