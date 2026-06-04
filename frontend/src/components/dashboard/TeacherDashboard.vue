<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ClipboardCheck, FileText, Plus, Users } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import { getMyProjects } from '@/api/competition'
import { formatDateTime, formatPercent } from '@/utils/format'
import { resolveStatusMeta } from '@/utils/status'

const props = defineProps({
  currentUser: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['select-project', 'create-project'])

const projects = ref([])
const loading = ref(false)

const hasProjects = computed(() => projects.value.length > 0)

async function loadMyProjects() {
  loading.value = true
  try {
    const response = await getMyProjects()
    projects.value = response.data ?? []
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error.message || '获取项目列表失败')
  } finally {
    loading.value = false
  }
}

function handleSelectProject(project) {
  emit('select-project', project)
}

function resolveStatusTag(status) {
  return resolveStatusMeta(status).tagType
}

onMounted(loadMyProjects)
</script>

<template>
  <FeaturePanel
    title="我指导的项目"
    subtitle="管理您作为指导教师参与的竞赛项目，查看材料提交进度并审核学生材料。"
  >
    <template #actions>
      <el-button type="primary" :icon="Plus" @click="$emit('create-project')">
        创建新项目
      </el-button>
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
                :label="resolveStatusMeta(project.status).label"
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

        <!-- 空白状态 -->
        <el-empty
          v-else
          description="暂无您指导的项目，可以创建一个新项目开始使用。"
        >
          <el-button type="primary" @click="$emit('create-project')">
            创建新项目
          </el-button>
        </el-empty>
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
