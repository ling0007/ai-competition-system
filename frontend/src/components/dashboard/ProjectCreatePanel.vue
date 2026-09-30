<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Plus, UserPlus, X } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import { formatDateTime, formatPercent } from '@/utils/format'
import { resolveProjectStatus } from '@/utils/status'

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
  initialNoticeId: {
    type: Number,
    default: null,
  },
  lockNotice: {
    type: Boolean,
    default: false,
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
  projectName: '',
  teamName: '',
  leaderId: null,
  noticeId: null,
  deadline: null,
  advisorId: null,
  memberUserIds: [],
})

// New member form for post-creation management (batch add)
const newMemberForm = reactive({
  userIds: [],
  memberRole: 'member',
})

// ── Role label map ──
const roleLabelMap = {
  leader: '负责人',
  advisor: '指导教师',
  member: '成员',
}

// ── Base option pools ──
const leaderOptions = computed(() => props.userOptions.filter((item) => item.role !== 'teacher') || [])
const allAdvisorOptions = computed(() => props.userOptions.filter((item) => item.role === 'teacher') || [])
const allMemberOptions = computed(() =>
  props.userOptions.filter((item) => item.role !== 'teacher' && item.role !== 'admin') || [],
)

// ── Remote search state: 指导教师 ──
const advisorSearchKeyword = ref('')
const advisorLoading = ref(false)
const advisorError = ref(null)
const remoteAdvisorOptions = ref([])

// ── Remote search state: 项目成员（创建表单） ──
const memberSearchKeyword = ref('')
const memberLoading = ref(false)
const memberError = ref(null)
const remoteMemberOptions = ref([])

// ── Remote search state: 成员管理添加 ──
const addMemberSearchKeyword = ref('')
const addMemberLoading = ref(false)
const addMemberError = ref(null)
const remoteAddMemberOptions = ref([])

// ── Existing member IDs ──
const existingMemberUserIds = computed(() =>
  (props.project?.members || []).map((m) => m.userId),
)

function remoteSearchAdvisor(query) {
  advisorSearchKeyword.value = query
  debouncedFilter(query, allAdvisorOptions, advisorLoading, advisorError, remoteAdvisorOptions, 'advisor')
}

function remoteSearchMembers(query) {
  memberSearchKeyword.value = query
  debouncedFilter(query, allMemberOptions, memberLoading, memberError, remoteMemberOptions, 'member')
}

function remoteSearchAddMembers(query) {
  addMemberSearchKeyword.value = query
  debouncedFilter(query, allMemberOptions, addMemberLoading, addMemberError, remoteAddMemberOptions, 'addMember')
}

const debounceTimers = { advisor: null, member: null, addMember: null }

function debouncedFilter(query, allOptions, loadingRef, errorRef, resultRef, key) {
  clearTimeout(debounceTimers[key])
  if (!query || !query.trim()) {
    resultRef.value = allOptions.value.slice(0, 20)
    loadingRef.value = false
    errorRef.value = null
    return
  }
  loadingRef.value = true
  errorRef.value = null
  debounceTimers[key] = setTimeout(() => {
    try {
      const kw = query.trim().toLowerCase()
      resultRef.value = allOptions.value.filter((item) =>
        item.label.toLowerCase().includes(kw),
      )
      loadingRef.value = false
    } catch {
      errorRef.value = '搜索失败，请重试'
      loadingRef.value = false
    }
  }, 300)
}

watch(() => props.userOptions, (opts) => {
  if (opts && opts.length) {
    remoteAdvisorOptions.value = allAdvisorOptions.value.slice(0, 20)
    remoteMemberOptions.value = allMemberOptions.value.slice(0, 20)
    remoteAddMemberOptions.value = allMemberOptions.value.slice(0, 20)
  }
}, { immediate: true })

// ── Computed member display ──
const projectStatus = computed(() => resolveProjectStatus(props.project?.status))
const advisorName = computed(() => {
  const advisor = props.project?.members?.find((m) => m.memberRole === 'advisor')
  return advisor?.realName || null
})
const memberNames = computed(() => {
  const members = props.project?.members?.filter((m) => m.memberRole === 'member') || []
  return members.map((m) => m.realName).join('、') || null
})

// ── Helper for options display ──
function isExistingMember(userId) {
  return existingMemberUserIds.value.includes(userId)
}

// ── When selected notice changes, sync its deadline ──
watch(() => form.noticeId, (newId) => {
  if (newId === null || newId === undefined || newId === '') {
    form.deadline = null
    return
  }
  const notice = props.noticeOptions.find(n => n.value == newId)
  form.deadline = notice?.deadline ? new Date(notice.deadline) : null
})

watch(
  () => [props.noticeOptions, props.userOptions, props.initialNoticeId],
  ([noticeOptions, userOptions]) => {
    if (props.initialNoticeId) {
      form.noticeId = props.initialNoticeId
    } else if (!form.noticeId && noticeOptions?.length) {
      form.noticeId = noticeOptions[0].value
    }

    if (!form.leaderId && userOptions?.length) {
      if (props.currentUser?.userId && props.currentUser.role !== 'teacher') {
        form.leaderId = props.currentUser.userId
      } else {
        form.leaderId = leaderOptions.value[0]?.value ?? userOptions[0].value
      }
    }

    if (!form.advisorId && props.currentUser?.userId && props.currentUser.role === 'teacher') {
      form.advisorId = props.currentUser.userId
    }
  },
  { immediate: true },
)

// ── Submit ──
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

// ── Batch add members ──
function handleBatchAddMembers() {
  if (!newMemberForm.userIds.length || !props.project?.projectId) return
  const role = newMemberForm.memberRole
  newMemberForm.userIds.forEach((userId) => {
    emit('add-member', props.project.projectId, { userId, memberRole: role })
  })
  newMemberForm.userIds = []
  newMemberForm.memberRole = 'member'
}

// ── Remove member ──
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

// ── Cleanup debounce timers ──
onBeforeUnmount(() => {
  Object.values(debounceTimers).forEach(clearTimeout)
})
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
            <el-input :model-value="currentUser?.realName || currentUser?.username || '当前登录学生'" disabled />
          </el-form-item>

          <el-form-item label="关联通知">
            <el-select v-model="form.noticeId" placeholder="请选择竞赛通知" filterable :disabled="lockNotice">
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
            <el-select
              v-model="form.advisorId"
              placeholder="搜索姓名并选择成员"
              filterable
              remote
              clearable
              :remote-method="remoteSearchAdvisor"
              :loading="advisorLoading"
              :popper-class="'member-select-popper'"
            >
              <template v-if="advisorError" #empty>
                <div class="member-select-state">
                  <p>{{ advisorError }}</p>
                  <el-button size="small" @click="remoteSearchAdvisor(advisorSearchKeyword)">重试</el-button>
                </div>
              </template>
              <template v-else #empty>
                <div class="member-select-state">
                  <p>{{ advisorLoading ? '搜索中...' : '未找到匹配的教师' }}</p>
                </div>
              </template>
              <el-option
                v-for="item in remoteAdvisorOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              >
                <div class="member-option">
                  <span class="member-option__name">{{ item.label }}</span>
                  <el-tag size="small" type="success" effect="plain">教师</el-tag>
                </div>
              </el-option>
            </el-select>
          </el-form-item>

          <el-form-item label="项目成员">
            <el-select
              v-model="form.memberUserIds"
              placeholder="搜索姓名并选择成员"
              filterable
              remote
              multiple
              collapse-tags
              collapse-tags-tooltip
              :remote-method="remoteSearchMembers"
              :loading="memberLoading"
              :popper-class="'member-select-popper'"
            >
              <template v-if="memberError" #empty>
                <div class="member-select-state">
                  <p>{{ memberError }}</p>
                  <el-button size="small" @click="remoteSearchMembers(memberSearchKeyword)">重试</el-button>
                </div>
              </template>
              <template v-else #empty>
                <div class="member-select-state">
                  <p>{{ memberLoading ? '搜索中...' : '未找到匹配的学生' }}</p>
                </div>
              </template>
              <el-option
                v-for="item in remoteMemberOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
                :disabled="item.value === form.leaderId"
              >
                <div class="member-option">
                  <span class="member-option__name">{{ item.label }}</span>
                  <span v-if="item.value === form.leaderId" class="member-option__joined">已加入</span>
                  <el-tag v-else size="small" type="info" effect="plain">学生</el-tag>
                </div>
              </el-option>
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
          <div v-if="project.status === 'DRAFT'" class="member-manager">
            <div class="member-manager__head">
              <h4>项目成员管理</h4>
              <span class="member-manager__count">{{ project.members?.length || 0 }} 人</span>
            </div>

            <!-- Add member form -->
            <div class="member-manager__add">
              <el-select
                v-model="newMemberForm.userIds"
                placeholder="搜索姓名并选择成员"
                filterable
                remote
                multiple
                collapse-tags
                collapse-tags-tooltip
                :remote-method="remoteSearchAddMembers"
                :loading="addMemberLoading"
                :popper-class="'member-select-popper'"
                class="member-manager__add-select"
              >
                <template v-if="addMemberError" #empty>
                  <div class="member-select-state">
                    <p>{{ addMemberError }}</p>
                    <el-button size="small" @click="remoteSearchAddMembers(addMemberSearchKeyword)">重试</el-button>
                  </div>
                </template>
                <template v-else #empty>
                  <div class="member-select-state">
                    <p>{{ addMemberLoading ? '搜索中...' : '未找到匹配的用户' }}</p>
                  </div>
                </template>
                <el-option
                  v-for="item in remoteAddMemberOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                  :disabled="isExistingMember(item.value)"
                >
                  <div class="member-option">
                    <span class="member-option__name">{{ item.label }}</span>
                    <span v-if="isExistingMember(item.value)" class="member-option__joined">已加入</span>
                    <el-tag v-else size="small" :type="item.role === 'teacher' ? 'success' : 'info'" effect="plain">
                      {{ item.role === 'teacher' ? '教师' : '学生' }}
                    </el-tag>
                  </div>
                </el-option>
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
                :disabled="!newMemberForm.userIds.length"
                @click="handleBatchAddMembers"
              >
                {{ newMemberForm.userIds.length ? `添加 ${newMemberForm.userIds.length} 名成员` : '添加' }}
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
          <el-alert
            v-else
            type="info"
            :closable="false"
            title="项目提交后成员名单已冻结。"
          />
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
  grid-template-columns: 1fr 1fr;
  gap: 32px;
  align-items: start;
}

.project-panel__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
}

.project-panel__preview {
  display: grid;
  gap: 18px;
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
  font-size: 28px;
  font-weight: 700;
}

.project-panel__preview-head p {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 17px;
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
  font-size: 18px;
  font-weight: 600;
}

.member-manager__count {
  color: var(--app-text-muted);
  font-size: 15px;
  font-weight: 500;
}

.member-manager__add {
  display: flex;
  gap: 10px;
  align-items: center;
}

.member-manager__add-select {
  flex: 1;
  min-width: 180px;
}

.member-manager__add-role {
  width: 130px;
  flex-shrink: 0;
}

// Member option two-line display
.member-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
}

.member-option__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
  font-weight: 500;
  color: var(--app-text-primary);
}

.member-option__joined {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  line-height: 1.2;
  background: var(--app-surface-soft, #f9fafb);
  color: var(--app-text-muted, #9ca3af);
  white-space: nowrap;
}

// Empty / error / loading state in dropdown
.member-select-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 20px 16px;
  text-align: center;
}

.member-select-state p {
  margin: 0;
  color: var(--app-text-muted, #9ca3af);
  font-size: 15px;
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
  font-size: 17px;
  font-weight: 500;
}

.member-manager__item-role {
  display: inline-flex;
  align-items: center;
  min-height: 26px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 14px;
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
  font-size: 16px;
}

@media (max-width: 900px) {
  .project-panel {
    grid-template-columns: 1fr;
    gap: 24px;
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
