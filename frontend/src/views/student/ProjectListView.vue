<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Search } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import { listMyProjects } from '@/api/projects'
import { formatDateTime, formatPercent } from '@/utils/format'
import { resolveProjectStatus } from '@/utils/status'

const router = useRouter()
const loading = ref(false)
const projects = ref([])
const total = ref(0)
const filters = reactive({ keyword: '', status: '', pageNum: 1, pageSize: 10 })

async function loadProjects() {
  loading.value = true
  try {
    const response = await listMyProjects({ ...filters, keyword: filters.keyword || undefined, status: filters.status || undefined })
    projects.value = response.data?.records ?? []
    total.value = response.data?.total ?? 0
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '项目列表加载失败')
  } finally {
    loading.value = false
  }
}

function search() { filters.pageNum = 1; loadProjects() }
onMounted(loadProjects)
</script>

<template>
  <FeaturePanel title="我的项目" subtitle="查看并切换你参与的全部申报项目，每个项目都可通过独立链接直接访问。">
    <template #actions>
      <el-input v-model="filters.keyword" clearable placeholder="搜索项目或通知" :prefix-icon="Search" @keyup.enter="search" @clear="search" />
      <el-select v-model="filters.status" clearable placeholder="全部状态" @change="search">
        <el-option label="草稿" value="DRAFT" />
        <el-option label="审核中" value="UNDER_REVIEW" />
        <el-option label="需修改" value="REVISION_REQUIRED" />
        <el-option label="已通过" value="APPROVED" />
      </el-select>
      <el-button type="primary" :icon="Plus" @click="router.push('/student/notices')">发起申报</el-button>
    </template>

    <div v-loading="loading" class="project-list">
      <article v-for="project in projects" :key="project.projectId" class="project-list__card" @click="router.push(`/student/projects/${project.projectId}`)">
        <div class="project-list__head">
          <div><small>{{ project.noticeTitle }}</small><h3>{{ project.projectName }}</h3></div>
          <StatusTag :status="project.status" :label="resolveProjectStatus(project.status).label" :tone="resolveProjectStatus(project.status).tagType" />
        </div>
        <p>{{ project.teamName || '未填写团队名称' }} · 负责人 {{ project.leaderName }}</p>
        <div class="project-list__progress"><el-progress :percentage="Number(project.completionRate || 0)" :show-text="false" /><strong>{{ formatPercent(project.completionRate) }}</strong></div>
        <small>截止 {{ formatDateTime(project.deadline) }}</small>
      </article>
      <el-empty v-if="!loading && !projects.length" description="暂无申报项目，先去申报大厅选择通知。">
        <el-button type="primary" @click="router.push('/student/notices')">前往申报大厅</el-button>
      </el-empty>
    </div>

    <el-pagination v-if="total > filters.pageSize" v-model:current-page="filters.pageNum" :page-size="filters.pageSize" :total="total" layout="total, prev, pager, next" @current-change="loadProjects" />
  </FeaturePanel>
</template>

<style scoped lang="scss">
.project-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.project-list__card { padding: 20px; border: 1px solid var(--app-border); border-radius: var(--app-radius-sm); background: #fff; cursor: pointer; transition: border-color var(--app-transition); }
.project-list__card:hover { border-color: var(--app-primary); }
.project-list__head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.project-list__head h3 { margin: 5px 0 0; font-size: 18px; }
.project-list small, .project-list p { color: var(--app-text-tertiary); }
.project-list__progress { display: grid; grid-template-columns: 1fr auto; align-items: center; gap: 12px; margin: 18px 0 12px; }
@media (max-width: 840px) { .project-list { grid-template-columns: 1fr; } }
</style>
