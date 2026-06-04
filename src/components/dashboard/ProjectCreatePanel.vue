<script setup>
import { computed, reactive, watch } from 'vue'
import { Plus } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import { formatDateTime, formatPercent } from '@/utils/format'
import { resolveStatusMeta } from '@/utils/status'

const props = defineProps({
  noticeOptions: {
    type: Array,
    default: () => [],
  },
  userOptions: {
    type: Array,
    default: () => [],
  },
  project: {
    type: Object,
    default: null,
  },
  creating: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['create'])

const form = reactive({
  projectName: '基于大模型的校园竞赛申报材料智能核验助手',
  teamName: '逐梦智审队',
  leaderId: null,
  noticeId: null,
  deadline: null,
})

const leaderOptions = computed(() => props.userOptions.filter((item) => item.role !== 'teacher') || [])
const projectStatus = computed(() => resolveStatusMeta(props.project?.status))

// When selected notice changes, sync its deadline to the form
// Uses == (not ===) because el-select may coerce number values to strings
watch(() => form.noticeId, (newId) => {
  if (newId === null || newId === undefined || newId === '') {
    form.deadline = null
    return
  }
  const notice = props.noticeOptions.find(n => n.value == newId)
  form.deadline = notice?.deadline ? new Date(notice.deadline) : null
})

watch(
  () => [props.noticeOptions, props.userOptions],
  ([noticeOptions, userOptions]) => {
    if (!form.noticeId && noticeOptions.length) {
      form.noticeId = noticeOptions[0].value
    }

    if (!form.leaderId && userOptions.length) {
      form.leaderId = leaderOptions.value[0]?.value ?? userOptions[0].value
    }
  },
  { immediate: true },
)

function submitProject() {
  emit('create', {
    noticeId: form.noticeId,
    leaderId: form.leaderId,
    projectName: form.projectName,
    teamName: form.teamName,
    deadline: form.deadline,
    memberUserIds: [],
  })
}
</script>

<template>
  <FeaturePanel
    title="创建申报项目"
    subtitle="基于竞赛通知快速完成项目建档，并自动初始化材料清单与项目进度。"
  >
    <template #actions>
      <StatusTag
        :status="project?.status"
        :label="projectStatus.label"
        :tone="projectStatus.tagType"
      />
    </template>

    <div class="project-panel">
      <el-form label-position="top">
        <el-form-item label="项目名称">
          <el-input v-model="form.projectName" placeholder="请输入申报项目名称" />
        </el-form-item>

        <el-form-item label="团队名称">
          <el-input v-model="form.teamName" placeholder="请输入团队名称" />
        </el-form-item>

        <div class="project-panel__grid">
          <el-form-item label="负责人">
            <el-select v-model="form.leaderId" placeholder="请选择负责人" filterable>
              <el-option
                v-for="item in leaderOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="关联通知">
            <el-select v-model="form.noticeId" placeholder="请选择竞赛通知" filterable>
              <el-option
                v-for="item in noticeOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="项目截止时间">
          <el-date-picker
            v-model="form.deadline"
            type="datetime"
            :placeholder="selectedNoticeDeadline ? '已自动填充通知截止时间，可手动覆盖' : '不填写则沿用竞赛通知截止时间'"
            format="YYYY-MM-DD HH:mm"
          />
        </el-form-item>

        <el-button
          type="primary"
          :icon="Plus"
          :loading="creating"
          :disabled="!form.noticeId || !form.leaderId || !form.projectName"
          @click="submitProject"
        >
          创建项目
        </el-button>
      </el-form>

      <transition name="fade-slide" mode="out-in">
        <div v-if="project" class="project-panel__preview">
          <div class="project-panel__preview-head">
            <div>
              <h3>{{ project.projectName }}</h3>
              <p>{{ project.noticeTitle }}</p>
            </div>
            <StatusTag
              :status="project.status"
              :label="projectStatus.label"
              :tone="projectStatus.tagType"
              strong
            />
          </div>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="团队名称">
              {{ project.teamName || '待填写' }}
            </el-descriptions-item>
            <el-descriptions-item label="负责人">
              {{ project.leaderName }}
            </el-descriptions-item>
            <el-descriptions-item label="项目截止时间">
              {{ formatDateTime(project.deadline) }}
            </el-descriptions-item>
            <el-descriptions-item label="当前完成率">
              {{ formatPercent(project.completionRate) }}
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <el-empty
          v-else
          description="选择竞赛通知并填写基础信息后，可一键创建申报项目。"
        />
      </transition>
    </div>
  </FeaturePanel>
</template>

<style scoped lang="scss">
.project-panel {
  display: grid;
  gap: 24px;
}

.project-panel__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 16px;
}

.project-panel__preview {
  display: grid;
  gap: 16px;
  padding: 24px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
}

.project-panel__preview-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.project-panel__preview-head h3 {
  margin: 0 0 6px;
  color: var(--app-text-primary);
  font-size: 20px;
  font-weight: 700;
}

.project-panel__preview-head p {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
  line-height: 1.7;
}

@media (max-width: 860px) {
  .project-panel__grid {
    grid-template-columns: 1fr;
  }

  .project-panel__preview-head {
    flex-direction: column;
  }
}
</style>
