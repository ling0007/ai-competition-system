<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import { listMyReviewHistory } from '@/api/reviews'
import { formatDateTime } from '@/utils/format'

const router = useRouter()
const filters = reactive({ pageNum: 1, pageSize: 10 })
const records = ref([])
const total = ref(0)
const loading = ref(false)
async function load() {
  loading.value = true
  try {
    const response = await listMyReviewHistory({ ...filters })
    records.value = response.data?.records ?? []
    total.value = response.data?.total ?? 0
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '审核记录加载失败')
  } finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <FeaturePanel title="我的审核记录" subtitle="历史决定按审核人和项目归属读取；材料意见始终绑定当时的版本。">
    <div v-loading="loading">
      <el-table v-if="records.length" :data="records">
        <el-table-column label="项目" min-width="150"><template #default="{ row }"><el-button link type="primary" @click="router.push(`/teacher/projects/${row.projectId}/review`)">{{ row.projectName }}</el-button></template></el-table-column>
        <el-table-column label="审核对象" min-width="130"><template #default="{ row }">{{ row.reviewType === 'project' ? '项目决定' : row.requirementName }}</template></el-table-column>
        <el-table-column label="版本" width="75"><template #default="{ row }">{{ row.versionNo == null ? '—' : `V${row.versionNo}` }}</template></el-table-column>
        <el-table-column label="决定" prop="decision" width="150" />
        <el-table-column label="意见" prop="comment" min-width="180" show-overflow-tooltip />
        <el-table-column label="审核人" prop="reviewerName" width="100" />
        <el-table-column label="时间" width="180"><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></el-table-column>
      </el-table>
      <el-empty v-else-if="!loading" description="还没有审核记录；可前往审核队列处理待办。"><el-button @click="router.push('/teacher/reviews/pending')">查看待办</el-button></el-empty>
    </div>
    <el-pagination v-if="total > filters.pageSize" v-model:current-page="filters.pageNum" :page-size="filters.pageSize" :total="total" layout="total, prev, pager, next" @current-change="load" />
  </FeaturePanel>
</template>
