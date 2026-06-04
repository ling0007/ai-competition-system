<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Plus, UserPlus, X } from 'lucide-vue-next'
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
  currentUser: {
    type: Object,
    default: null,
  },
  creating: {
    type: Boolean,
    default: false,
  },
  addingMember: {
    type: Boolean,
    default: false,
  },
  removingMemberId: {
    type: [Number, null],
    default: null,
  },
})

const emit = defineEmits(['create', 'add-member', 'remove-member'])

const form = reactive({
  projectName: '基于大模型的校园竞赛申报材料智能核验助手',
  teamName: '逐梦智审队',
  leaderId: null,
  noticeId: null,
  deadline: null,
  advisorId: null,
  memberUserIds: [],
})

// New member form for post-creation management
const newMemberForm = reactive({
  userId: null,
  memberRole: 'member',
})

const leaderOptions = computed(() => props.userOptions.filter((item) => item.role !== 'teacher') || [])
const advisorOptions = computed(() => props.userOptions.filter((item) => item.role === 'teacher') || [])
const memberOptions = computed(() =>
  props.userOptions.filter((item) => item.role !== 'teacher' && item.role !== 'admin') || [],
)
const projectStatus = computed(() => resolveStatusMeta(props.project?.status))

// Existing member user IDs for filtering "add member" dropdown
const existingMemberUserIds = computed(() =>
  (props.project?.members || []).map((m) => m.userId),
)

// Available users for adding new members (not already in project)
const availableUserOptions = computed(() =>
  props.userOptions.filter((item) => !existingMemberUserIds.value.includes(item.value)),
)

// Computed member display
const advisorName = computed(() => {
  const advisor = props.project?.members?.find((m) => m.memberRole === 'advisor')
  return advisor?.realName || null
})

const memberNames = computed(() => {
  const members = props.project?.members?.filter((m) => m.memberRole === 'member') || []
  return members.map((m) => m.realName).join('、') || null
})

const roleLabelMap = {
  leader: '负责人',
  advisor: '指导教师',
  member: '成员',
}

// When selected notice changes, sync its deadline to the form
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
      // Pre-select current user as leader if they are a student
      if (props.currentUser?.userId && props.currentUser.role !== 'teacher') {
        form.leaderId = props.currentUser.userId
      } else {
        form.leaderId = leaderOptions.value[0]?.value ?? userOptions[0].value
      }
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
    advisorId: form.advisorId,
    memberUserIds: form.memberUserIds,
  })
}

function handleAddMember() {
  if (!newMemberForm.userId || !props.project?.projectId) return
  emit('add-member', props.project.projectId, {
    userId: newMemberForm.userId,
    memberRole: newMemberForm.memberRole,
  })
  newMemberForm.userId = null
  newMemberForm.memberRole = 'member'
}

async function handleRemoveMember(memberId, memberName, memberRole) {
  if (memberRole === 'leader') return
  try {
    await ElMessageBox.confirm(
      `确定要移除成员「${memberName}」吗？`,
      '移除成员',
      { confirmButtonText: '移除', cancelButtonText: '取消', type: 'warning' },
    )
    emit('remove-member', props.project.projectId, memberId)
  } catch {
    // User cancelled
  }
}
</script>

<template>
  <FeaturePanel
    title="创建申报项目"
    subtitle="基于竞赛通知快速完成项目建档，支持管理项目成员并自动初始化材料清单与项目进度。"
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

        <div class="project-panel__grid">
          <el-form-item label="指导教师">
            <el-select v-model="form.advisorId" placeholder="请选择指导教师（可选）" filterable clearable>
              <el-option
                v-for="item in advisorOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>

          <el-form-item label="项目成员">
            <el-select v-model="form.memberUserIds" placeholder="请选择成员（可多选）" filterable multiple>
              <el-option
                v-for="item in memberOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
                :disabled="item.value === form.leaderId"
              />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="项目截止时间">
          <el-date-picker
            v-model="form.deadline"
            type="datetime"
            placeholder="不填写则沿用竞赛通知截止时间"
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
            <el-descriptions-item label="指导教师">
              {{ advisorName || '未设置' }}
            </el-descriptions-item>
            <el-descriptions-item label="项目成员">
              <span v-if="memberNames">{{ memberNames }}</span>
              <span v-else class="project-panel__muted">无</span>
            </el-descriptions-item>
            <el-descriptions-item label="项目截止时间">
              {{ formatDateTime(project.deadline) }}
            </el-descriptions-item>
            <el-descriptions-item label="当前完成率">
              {{ formatPercent(project.completionRate) }}
            </el-descriptions-item>
          </el-descriptions>

          <!-- Member Management Section -->
          <div class="member-manager">
            <div class="member-manager__head">
              <h4>项目成员管理</h4>
              <span class="member-manager__count">{{ project.members?.length || 0 }} 人</span>
            </div>

            <!-- Add member form -->
            <div class="member-manager__add">
              <el-select
                v-model="newMemberForm.userId"
                placeholder="选择用户"
                filterable
                size="default"
                class="member-manager__add-select"
              >
                <el-option
                  v-for="item in availableUserOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
              <el-select
                v-model="newMemberForm.memberRole"
                size="default"
                class="member-manager__add-role"
              >
                <el-option label="成员" value="member" />
                <el-option label="指导教师" value="advisor" />
              </el-select>
              <el-button
                type="primary"
                :icon="UserPlus"
                :loading="addingMember"
                :disabled="!newMemberForm.userId"
                @click="handleAddMember"
              >
                添加
              </el-button>
            </div>

            <!-- Member list -->
            <div class="member-manager__list">
              <div
                v-for="member in project.members"
                :key="member.memberId"
                class="member-manager__item"
              >
                <div class="member-manager__item-info">
                  <span class="member-manager__item-name">{{ member.realName }}</span>
                  <span
                    class="member-manager__item-role"
                    :class="`member-manager__item-role--${member.memberRole}`"
                  >
                    {{ roleLabelMap[member.memberRole] || member.memberRole }}
                  </span>
                </div>
                <el-button
                  v-if="member.memberRole !== 'leader'"
                  :icon="X"
                  size="small"
                  type="danger"
                  text
                  :loading="removingMemberId === member.memberId"
                  @click="handleRemoveMember(member.memberId, member.realName, member.memberRole)"
                />
              </div>
              <div v-if="!project.members?.length" class="member-manager__empty">
                暂无成员
              </div>
            </div>
          </div>
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

.project-panel__muted {
  color: var(--app-text-muted);
}

// Member Manager
.member-manager {
  display: grid;
  gap: 14px;
  padding: 20px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface-soft);
}

.member-manager__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.member-manager__head h4 {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 15px;
  font-weight: 600;
}

.member-manager__count {
  color: var(--app-text-muted);
  font-size: 12px;
  font-weight: 500;
}

.member-manager__add {
  display: flex;
  gap: 10px;
  align-items: center;
}

.member-manager__add-select {
  flex: 1;
  min-width: 140px;
}

.member-manager__add-role {
  width: 130px;
  flex-shrink: 0;
}

.member-manager__list {
  display: grid;
  gap: 8px;
}

.member-manager__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
}

.member-manager__item-info {
  display: flex;
  align-items: center;
  gap: 10px;
}

.member-manager__item-name {
  color: var(--app-text-primary);
  font-size: 14px;
  font-weight: 500;
}

.member-manager__item-role {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 600;
  line-height: 1.2;
}

.member-manager__item-role--leader {
  background: var(--app-primary-bg, #eff6ff);
  color: var(--app-primary, #3b82f6);
}

.member-manager__item-role--advisor {
  background: var(--app-success-bg, #ecfdf5);
  color: var(--app-success, #10b981);
}

.member-manager__item-role--member {
  background: var(--app-info-bg, #f0f9ff);
  color: var(--app-info, #6366f1);
}

.member-manager__empty {
  padding: 16px;
  text-align: center;
  color: var(--app-text-muted);
  font-size: 13px;
}

@media (max-width: 860px) {
  .project-panel__grid {
    grid-template-columns: 1fr;
  }

  .project-panel__preview-head {
    flex-direction: column;
  }

  .member-manager__add {
    flex-wrap: wrap;
  }

  .member-manager__add-select,
  .member-manager__add-role {
    flex: 1 1 auto;
    min-width: 120px;
  }
}
</style>
