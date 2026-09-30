<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import { listMyProjects } from '@/api/projects'
import { formatDateTime } from '@/utils/format'
import { resolveProjectStatus } from '@/utils/status'

const router = useRouter()
const filters = reactive({ keyword: '', status: 'UNDER_REVIEW', deadlineBefore: '', pageNum: 1, pageSize: 10 })
const records = ref([])
const total = ref(0)
const pendingTotal = ref(null)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [list, pending] = await Promise.all([
      listMyProjects({ ...filters, keyword: filters.keyword || undefined, deadlineBefore: filters.deadlineBefore ? `${filters.deadlineBefore}T23:59:59` : undefined }),
      listMyProjects({ status: 'UNDER_REVIEW', pageNum: 1, pageSize: 1 }),
    ])
    records.value = list.data?.records ?? []
    total.value = list.data?.total ?? 0
    pendingTotal.value = pending.data?.total ?? 0
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '审核队列加载失败')
  } finally {
    loading.value = false
  }
}
function search() { filters.pageNum = 1; load() }
onMounted(load)
</script>

<template>
  <FeaturePanel title="我的审核队列" subtitle="仅展示您作为指导教师的项目；待办数量来自服务端分页总数。">
    <template #actions>
      <el-button @click="router.push('/teacher/reviews/history')">我的审核记录</el-button>
    </template>
    <p v-if="pendingTotal !== null">待审核项目：<strong>{{ pendingTotal }}</strong></p>
    <div class="queue-filters">
      <el-input v-model="filters.keyword" clearable placeholder="搜索项目或通知" @keyup.enter="search" @clear="search" />
      <el-select v-model="filters.status" placeholder="全部状态" clearable @change="search">
        <el-option label="审核中" value="UNDER_REVIEW" />
        <el-option label="需修改" value="REVISION_REQUIRED" />
        <el-option label="已通过" value="APPROVED" />
        <el-option label="草稿" value="DRAFT" />
      </el-select>
      <el-date-picker v-model="filters.deadlineBefore" type="date" value-format="YYYY-MM-DD" placeholder="截止日期不晚于" clearable @change="search" />
      <el-button type="primary" @click="search">筛选</el-button>
    </div>
    <div v-loading="loading">
      <el-table v-if="records.length" :data="records" @row-click="(row) => router.push(`/teacher/projects/${row.projectId}/review`)">
        <el-table-column label="项目" min-width="180" prop="projectName" />
        <el-table-column label="通知" min-width="180" prop="noticeTitle" />
        <el-table-column label="负责人" prop="leaderName" width="100" />
        <el-table-column label="截止时间" width="180"><template #default="{ row }">{{ formatDateTime(row.deadline) }}</template></el-table-column>
        <el-table-column label="状态" width="120"><template #default="{ row }"><StatusTag :status="row.status" :label="resolveProjectStatus(row.status).label" :tone="resolveProjectStatus(row.status).tagType" /></template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click.stop="router.push(`/teacher/projects/${row.projectId}/review`)">查看审核</el-button></template></el-table-column>
      </el-table>
      <el-empty v-else-if="!loading" description="当前筛选条件下没有指导项目；可切换状态或日期查看。" />
    </div>
    <el-pagination v-if="total > filters.pageSize" v-model:current-page="filters.pageNum" :page-size="filters.pageSize" :total="total" layout="total, prev, pager, next" @current-change="load" />
  </FeaturePanel>
</template>

<style scoped>
.queue-filters { display: flex; flex-wrap: wrap; gap: 12px; margin: 16px 0; }
.queue-filters .el-input { width: 240px; }
.queue-filters .el-select { width: 140px; }
</style>
