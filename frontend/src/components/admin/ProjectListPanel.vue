<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from 'lucide-vue-next'
import StatusTag from '@/components/shared/StatusTag.vue'
import { getAllProjects, listNotices } from '@/api/competition'
import { formatDateTime, formatPercent } from '@/utils/format'
import { resolveProjectStatus } from '@/utils/status'

const emit = defineEmits(['select-project'])

// ==================== 数据 ====================

const projects = ref([])
const loading = ref(false)
const total = ref(0)

// 筛选条件
const keyword = ref('')
const status = ref('')
const noticeId = ref(null)
const currentPage = ref(1)
const pageSize = ref(10)

// 通知下拉选项
const noticeOptions = ref([])
const noticeLoading = ref(false)

// ==================== 常量 ====================

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'UNDER_REVIEW', label: '待审核' },
  { value: 'REVISION_REQUIRED', label: '退回修改' },
  { value: 'APPROVED', label: '审核通过' },
]

const totalText = computed(() => `共 ${total.value} 个项目`)

// ==================== 方法 ====================

async function loadNoticeOptions() {
  noticeLoading.value = true
  try {
    const response = await listNotices({ pageSize: 200 })
    if (response.code === 200) {
      noticeOptions.value = (response.data?.records || []).map((n) => ({
        value: n.noticeId,
        label: n.title,
      }))
    }
  } catch {
    // 通知列表加载失败不影响项目列表
  } finally {
    noticeLoading.value = false
  }
}

async function loadProjects() {
  loading.value = true
  try {
    const params = {
      keyword: keyword.value || undefined,
      status: status.value || undefined,
      noticeId: noticeId.value || undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    }
    const response = await getAllProjects(params)
    if (response.code === 200) {
      projects.value = response.data?.records || []
      total.value = response.data?.total || 0

      // pageNum 超出总页数时重置为最后一页
      const totalPages = response.data?.pages || 0
      if (totalPages > 0 && currentPage.value > totalPages) {
        currentPage.value = totalPages
        // 避免无限递归 —— 仅在确实需要纠正时重新加载
        await loadProjectsAfterPageCorrection()
        return
      }
    } else {
      ElMessage.error(response.message || '获取项目列表失败')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '获取项目列表失败')
  } finally {
    loading.value = false
  }
}

async function loadProjectsAfterPageCorrection() {
  loading.value = true
  try {
    const params = {
      keyword: keyword.value || undefined,
      status: status.value || undefined,
      noticeId: noticeId.value || undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    }
    const response = await getAllProjects(params)
    if (response.code === 200) {
      projects.value = response.data?.records || []
      total.value = response.data?.total || 0
    }
  } catch {
    // 已在主方法中处理
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadProjects()
}

function handleReset() {
  keyword.value = ''
  status.value = ''
  noticeId.value = null
  currentPage.value = 1
  loadProjects()
}

function handleStatusChange() {
  currentPage.value = 1
  loadProjects()
}

function handleNoticeChange() {
  currentPage.value = 1
  loadProjects()
}

function handlePageChange(page) {
  currentPage.value = page
  loadProjects()
}

function handleSizeChange(size) {
  pageSize.value = size
  currentPage.value = 1
  loadProjects()
}

function handleViewDetail(project) {
  emit('select-project', project)
}

function statusTagType(statusVal) {
  return resolveProjectStatus(statusVal).tagType
}

function statusLabel(statusVal) {
  return resolveProjectStatus(statusVal).label
}

// ==================== 生命周期 ====================

onMounted(() => {
  loadNoticeOptions()
  loadProjects()
})
</script>

<template>
  <div class="project-list-panel">
    <!-- 标题行 -->
    <div class="project-list-panel__header">
      <h2 class="project-list-panel__title">项目总览</h2>
      <span class="project-list-panel__count">{{ totalText }}</span>
    </div>

    <!-- 筛选区 -->
    <div class="project-list-panel__toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索项目名称、负责人或通知..."
        size="default"
        clearable
        style="width: 280px"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select
        v-model="status"
        placeholder="全部状态"
        size="default"
        clearable
        style="width: 140px"
        @change="handleStatusChange"
      >
        <el-option
          v-for="opt in statusOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-select
        v-model="noticeId"
        placeholder="全部通知"
        size="default"
        clearable
        filterable
        style="width: 240px"
        :loading="noticeLoading"
        @change="handleNoticeChange"
      >
        <el-option
          v-for="opt in noticeOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </div>

    <!-- 表格 -->
    <div class="project-list-panel__table">
      <el-table
        v-if="projects.length"
        :data="projects"
        v-loading="loading"
        stripe
        style="width: 100%"
        empty-text="暂无项目数据"
        :highlight-current-row="false"
      >
        <el-table-column
          prop="projectName"
          label="项目名称"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column
          prop="noticeTitle"
          label="所属通知"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column
          prop="leaderName"
          label="负责人"
          width="120"
        />
        <el-table-column
          label="完成率"
          width="140"
          align="center"
        >
          <template #default="{ row }">
            <div class="project-list-panel__progress">
              <el-progress
                :percentage="Number(row.completionRate ?? 0)"
                :stroke-width="6"
                :show-text="false"
              />
              <span class="project-list-panel__progress-text">
                {{ formatPercent(row.completionRate) }}
              </span>
            </div>
          </template>
        </el-table-column>
        <el-table-column
          label="项目状态"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <StatusTag
              :status="row.status"
              :label="statusLabel(row.status)"
              :tone="statusTagType(row.status)"
            />
          </template>
        </el-table-column>
        <el-table-column
          label="创建时间"
          width="170"
        >
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          width="120"
          align="center"
          fixed="right"
        >
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              @click="handleViewDetail(row)"
            >
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无项目数据" :image-size="80" />
    </div>

    <!-- 分页 -->
    <div v-if="total > pageSize" class="project-list-panel__pagination">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<style scoped lang="scss">
.project-list-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.project-list-panel__header {
  display: flex;
  align-items: baseline;
  gap: 16px;
}

.project-list-panel__title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: var(--app-text-primary, #111827);
}

.project-list-panel__count {
  color: var(--app-text-muted, #6b7280);
  font-size: 15px;
}

.project-list-panel__toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.project-list-panel__table {
  border: 1px solid var(--app-border, #e5e7eb);
  border-radius: 8px;
  background: #ffffff;
  overflow: hidden;
  box-shadow: var(--app-shadow-sm);
}

.project-list-panel__progress {
  display: flex;
  align-items: center;
  gap: 8px;

  .el-progress {
    flex: 1;
    max-width: 80px;
  }
}

.project-list-panel__progress-text {
  font-size: 13px;
  color: var(--app-text-secondary, #4b5563);
  white-space: nowrap;
  min-width: 40px;
}

.project-list-panel__pagination {
  display: flex;
  justify-content: center;
}
</style>
