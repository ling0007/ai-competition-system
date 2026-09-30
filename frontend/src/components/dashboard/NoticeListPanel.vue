<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Eye, Plus, Search } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import { listNotices } from '@/api/competition'
import { formatDateTime } from '@/utils/format'
import { resolveNoticePublishStatus } from '@/utils/status'

const props = defineProps({
  loadingDetail: {
    type: Boolean,
    default: false,
  },
  role: {
    type: String,
    default: 'student',
  },
})

const emit = defineEmits(['select-notice', 'create-project', 'create-notice'])

const loading = ref(false)
const notices = ref([])
const searchKeyword = ref('')
const publishStatusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const subtitle = computed(() => props.role === 'student'
  ? '浏览已发布的申报通知，确认截止时间和材料要求后发起申报。'
  : '浏览所有已上传的申报通知，查看 AI 解析状态和发布进度。')

const publishStatusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '未发布' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'ARCHIVED', label: '已归档' },
]

async function loadNotices() {
  loading.value = true
  try {
    const isStudent = props.role === 'student'
    const params = {
      keyword: searchKeyword.value || undefined,
      publishStatus: isStudent ? 'PUBLISHED' : (publishStatusFilter.value || undefined),
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    }
    const response = await listNotices(params)
    notices.value = response.data?.records ?? []
    total.value = response.data?.total ?? 0
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '获取通知列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadNotices()
}

function handlePageChange(page) {
  currentPage.value = page
  loadNotices()
}

onMounted(() => {
  loadNotices()
})

defineExpose({ refresh: loadNotices })
</script>

<template>
  <FeaturePanel
    title="申报通知列表"
    :subtitle="subtitle"
  >
    <template #actions>
      <el-button
        v-if="role === 'admin'"
        type="primary"
        size="small"
        :icon="Plus"
        @click="emit('create-notice')"
      >
        新建通知
      </el-button>
      <el-input
        v-model="searchKeyword"
        placeholder="搜索标题或主办方..."
        size="small"
        clearable
        style="width: 220px"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-select
        v-if="role !== 'student'"
        v-model="publishStatusFilter"
        placeholder="全部状态"
        size="small"
        clearable
        style="width: 130px"
        @change="handleSearch"
      >
        <el-option
          v-for="opt in publishStatusOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
      <el-button size="small" @click="handleSearch">搜索</el-button>
    </template>

    <div v-loading="loading" class="notice-list">
      <el-table
        v-if="notices.length"
        :data="notices"
        stripe
        style="width: 100%"
        size="default"
        empty-text="暂无通知数据"
      >
        <el-table-column prop="noticeId" label="ID" width="70" align="center" />

        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="notice-list__title">{{ row.title }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="organizer" label="主办方" width="150" show-overflow-tooltip />

        <el-table-column label="发布状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag
              :status="row.publishStatus"
              :label="resolveNoticePublishStatus(row.publishStatus).label"
              :tone="resolveNoticePublishStatus(row.publishStatus).tagType"
            />
          </template>
        </el-table-column>

        <el-table-column v-if="role !== 'student'" label="解析状态" width="100" align="center">
          <template #default="{ row }">
            <StatusTag
              :status="row.parseStatus"
              :label="row.parseStatus === 'PARSED' ? '已解析' : row.parseStatus === 'DRAFT' ? '待解析' : row.parseStatus === 'PARSING' ? '解析中' : row.parseStatus"
              :tone="row.parseStatus === 'PARSED' ? 'success' : row.parseStatus === 'FAILED' ? 'danger' : 'info'"
            />
          </template>
        </el-table-column>

        <el-table-column label="截止时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.deadline) }}
          </template>
        </el-table-column>

        <el-table-column label="创建时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.createdAt) }}
          </template>
        </el-table-column>

        <el-table-column v-if="role !== 'teacher'" label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <div class="notice-list__row-actions">
              <el-button
                size="small"
                type="primary"
                :loading="loadingDetail"
                @click="emit('select-notice', row)"
              >
                <Eye :size="14" style="margin-right: 4px" />
                {{ role === 'student' ? '查看详情' : '查看' }}
              </el-button>
              <el-button
                v-if="role === 'student'"
                size="small"
                type="success"
                @click="emit('create-project', row)"
              >
                <Plus :size="14" style="margin-right: 4px" />
                立即申报
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-else description="暂无通知数据" :image-size="80" />
    </div>

    <div v-if="total > pageSize" class="notice-list__pagination">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next, jumper"
        background
        @current-change="handlePageChange"
      />
    </div>
  </FeaturePanel>
</template>

<style scoped lang="scss">
.notice-list {
  display: grid;
  gap: 16px;
}

.notice-list__title {
  font-weight: 500;
  color: var(--app-text-primary);
}

.notice-list__row-actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.notice-list__row-actions .el-button {
  width: 124px;
  margin: 0;
}

.notice-list__pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>
