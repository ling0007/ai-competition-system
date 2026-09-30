<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ClipboardCheck, FileText, Search, Users } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import { getMyProjects } from '@/api/competition'
import { formatDateTime, formatPercent } from '@/utils/format'
import { resolveProjectStatus } from '@/utils/status'

defineProps({
  currentUser: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['select-project'])

const projects = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(5)
const total = ref(0)

const statusOptions = [
  { value: '', label: '全部状态' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'UNDER_REVIEW', label: '审核中' },
  { value: 'APPROVED', label: '已通过' },
  { value: 'REVISION_REQUIRED', label: '需修改' },
]

const hasProjects = computed(() => projects.value.length > 0)

async function loadMyProjects() {
  loading.value = true
  try {
    const params = {
      keyword: searchKeyword.value || undefined,
      status: statusFilter.value || undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    }
    const response = await getMyProjects(params)
    projects.value = response.data?.records ?? []
    total.value = response.data?.total ?? 0
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error.message || '获取项目列表失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadMyProjects()
}

function handlePageChange(page) {
  currentPage.value = page
  loadMyProjects()
}

function handleSelectProject(project) {
  emit('select-project', project)
}

function resolveStatusTag(status) {
  return resolveProjectStatus(status).tagType
}

onMounted(loadMyProjects)
</script>

<template>
  <FeaturePanel
    title="我指导的项目"
    subtitle="管理您作为指导教师参与的竞赛项目，查看材料提交进度并审核学生材料。"
  >
    <template #actions>
      <el-input
        v-model="searchKeyword"
        placeholder="搜索项目名或通知..."
        size="small"
        clearable
        style="width: 200px"
        :prefix-icon="Search"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button type="primary" size="small" @click="handleSearch">搜索</el-button>
      <el-select
        v-model="statusFilter"
        placeholder="全部状态"
        size="small"
        clearable
        style="width: 130px"
        @change="handleSearch"
      >
        <el-option
          v-for="opt in statusOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </template>

    <div class="teacher-dashboard">
      <div v-loading="loading">
        <!-- 项目列表 -->
        <div v-if="hasProjects" class="teacher-dashboard__list">
          <div
            v-for="project in projects"
            :key="project.projectId"
            class="teacher-dashboard__card"
            @click="handleSelectProject(project)"
          >
            <div class="teacher-dashboard__card-head">
              <div>
                <h4>{{ project.projectName }}</h4>
                <p>{{ project.noticeTitle }}</p>
              </div>
              <StatusTag
                :status="project.status"
                :label="resolveProjectStatus(project.status).label"
                :tone="resolveStatusTag(project.status)"
              />
            </div>

            <div class="teacher-dashboard__card-grid">
              <div>
                <span>团队</span>
                <strong>{{ project.teamName || '未设置' }}</strong>
              </div>
              <div>
                <span>负责人</span>
                <strong>{{ project.leaderName }}</strong>
              </div>
              <div>
                <span>截止时间</span>
                <strong>{{ formatDateTime(project.deadline) }}</strong>
              </div>
              <div>
                <span>完成率</span>
                <strong>{{ formatPercent(project.completionRate) }}</strong>
              </div>
            </div>

            <div class="teacher-dashboard__card-stats">
              <div class="teacher-dashboard__stat">
                <FileText :size="14" />
                <span>{{ project.submittedCount }}/{{ project.totalCount }} 已提交</span>
              </div>
              <div class="teacher-dashboard__stat">
                <ClipboardCheck :size="14" />
                <span>{{ project.reviewedCount }}/{{ project.totalCount }} 已审核</span>
              </div>
              <div class="teacher-dashboard__stat">
                <Users :size="14" />
                <span>{{ project.memberNames?.length || 0 }} 名成员</span>
              </div>
            </div>

            <div v-if="project.memberNames?.length" class="teacher-dashboard__members">
              <el-tag
                v-for="(name, idx) in project.memberNames"
                :key="idx"
                size="small"
                effect="plain"
              >
                {{ name }}
              </el-tag>
            </div>
          </div>
        </div>

        <el-empty
          v-else
          description="暂无您指导的项目"
        />

        <div v-if="total > pageSize" class="teacher-dashboard__pagination">
          <el-pagination
            v-model:current-page="currentPage"
            :page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            background
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </div>
  </FeaturePanel>
</template>

<style scoped lang="scss">
.teacher-dashboard {
  display: grid;
  gap: 20px;
}

.teacher-dashboard__list {
  display: grid;
  gap: 16px;
}

.teacher-dashboard__card {
  display: grid;
  gap: 16px;
  padding: 20px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
  cursor: pointer;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;

  &:hover {
    border-color: var(--app-primary, #3b82f6);
    box-shadow: 0 2px 12px rgba(59, 130, 246, 0.1);
  }
}

.teacher-dashboard__card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.teacher-dashboard__card-head h4 {
  margin: 0 0 4px;
  color: var(--app-text-primary);
  font-size: 16px;
  font-weight: 600;
}

.teacher-dashboard__card-head p {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.5;
}

.teacher-dashboard__card-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.teacher-dashboard__card-grid > div {
  padding: 12px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface-soft);
}

.teacher-dashboard__card-grid span {
  display: block;
  margin-bottom: 4px;
  color: var(--app-text-muted);
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.teacher-dashboard__card-grid strong {
  color: var(--app-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.teacher-dashboard__card-stats {
  display: flex;
  gap: 20px;
  padding: 12px 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface-soft);
}

.teacher-dashboard__stat {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.teacher-dashboard__members {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.teacher-dashboard__pagination {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

@media (max-width: 860px) {
  .teacher-dashboard__card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .teacher-dashboard__card-stats {
    flex-wrap: wrap;
    gap: 10px;
  }
}
</style>
