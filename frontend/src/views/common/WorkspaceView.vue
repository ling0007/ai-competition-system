<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import AdminDashboard from '@/components/admin/AdminDashboard.vue'
import AgentTaskLogPanel from '@/components/dashboard/AgentTaskLogPanel.vue'
import MaterialCheckPanel from '@/components/dashboard/MaterialCheckPanel.vue'
import MaterialReviewPanel from '@/components/dashboard/MaterialReviewPanel.vue'
import MessageCenterPanel from '@/components/dashboard/MessageCenterPanel.vue'
import NoticeUploadPanel from '@/components/dashboard/NoticeUploadPanel.vue'
import NoticeListPanel from '@/components/dashboard/NoticeListPanel.vue'
import ProjectCreatePanel from '@/components/dashboard/ProjectCreatePanel.vue'
import TeacherDashboard from '@/components/dashboard/TeacherDashboard.vue'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import FileContentViewer from '@/components/shared/FileContentViewer.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import {
  addProjectMember,
  createProject,
  getDashboardBootstrap,
  getNoticeDetail,
  getProjectDetail,
  getProjectProgress,
  parseNotice,
  removeProjectMember,
  resubmitProject,
  runMaterialCheck,
  submitProject,
  uploadMaterial,
  uploadNotice,
} from '@/api/competition'
import { formatDateTime, formatPercent } from '@/utils/format'
import { resolveStatusMeta } from '@/utils/status'
import { getAuthState } from '@/state/auth.js'
import { useNoticeParseTask } from '@/composables/useNoticeParseTask.js'

const route = useRoute()
const router = useRouter()
const auth = getAuthState()
const currentUser = computed(() => auth.user)
const pageKey = computed(() => route.meta.pageKey)
const noticeOptions = ref([])
const userOptions = ref([])
const currentNotice = ref(null)
const currentProjectDetail = ref(null)
const currentProgress = ref(null)
const lastCheckResult = ref(null)
const messageCenterRef = ref(null)
const agentTaskLogRef = ref(null)
const { task: noticeParseTask, watchTask: watchNoticeTask, resume: resumeNoticeTask, stop: stopNoticeTask } = useNoticeParseTask({
  onSuccess: async (task) => {
    const response = await getNoticeDetail(task.businessId)
    currentNotice.value = response.data
    upsertNoticeOption(currentNotice.value)
    agentTaskLogRef.value?.refresh()
    if (task.resultOrigin === 'FALLBACK') ElMessage.warning('已生成降级解析草稿，请进入通知详情逐项人工核对')
    else ElMessage.success('通知解析完成，请进入通知详情核对草稿')
  },
  onFailure: async (task) => {
    if (task.businessId) {
      const response = await getNoticeDetail(task.businessId)
      currentNotice.value = response.data
    }
    ElMessage.error(task.errorSummary || '通知解析任务失败，请刷新后重试')
  },
})

// Teacher-specific state
const teacherSelectedProject = ref(null)
const fileViewerVisible = ref(false)
const fileViewerFileId = ref(null)
const fileViewerFileName = ref('')
const isTeacher = computed(() => currentUser.value?.role === 'teacher')
const isAdmin = computed(() => currentUser.value?.role === 'admin')

const loading = reactive({
  bootstrap: false,
  uploadNotice: false,
  parseNotice: false,
  createProject: false,
  uploadMaterialId: null,
  runCheck: false,
  addMember: false,
  removeMemberId: null,
})

// 通知列表 → 通知解析页面的加载与竞态保护
const loadingNoticeDetail = ref(false)
const noticeDetailRequestId = ref(0)


const submittedMaterials = computed(
  () => currentProjectDetail.value?.materials?.filter(
    (item) => item.requiredFlag === 1 && item.uploaded === true,
  ) ?? [],
)

const missingMaterials = computed(
  () => currentProjectDetail.value?.materials?.filter(
    (item) => item.requiredFlag === 1 && item.requirementSatisfied === false,
  ) ?? [],
)

const projectStatusMeta = computed(() => resolveStatusMeta(currentProjectDetail.value?.status))

function scrollToSection(key) {
  const role = currentUser.value?.role
  const routes = {
    student: { overview: '/student/dashboard', noticeList: '/student/notices', project: '/student/projects', material: '/student/materials', messages: '/messages' },
    teacher: { overview: '/teacher/dashboard', noticeList: '/teacher/projects', material: '/teacher/reviews/pending', logs: '/teacher/reviews/history' },
    admin: { overview: '/admin/users', noticeList: '/admin/notices', notice: '/admin/notices', project: '/admin/projects', material: '/admin/projects', logs: '/admin/ai-tasks' },
  }
  const target = routes[role]?.[key]
  if (target) router.push(target)
}

function syncBootstrapState(snapshot) {
  noticeOptions.value = snapshot.noticeOptions ?? []
  userOptions.value = snapshot.userOptions ?? []
  currentNotice.value = snapshot.notice ?? null
  currentProjectDetail.value = snapshot.projectDetail ?? null
  currentProgress.value = snapshot.progress ?? null
  lastCheckResult.value = snapshot.aiCheck ?? null
}

async function loadDashboardBootstrap() {
  loading.bootstrap = true

  try {
    const response = await getDashboardBootstrap(currentUser.value?.userId)
    syncBootstrapState(response.data)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '初始化系统数据失败'))
  } finally {
    loading.bootstrap = false
  }
}

async function refreshProjectState(projectId) {
  const [detailResponse, progressResponse] = await Promise.all([
    getProjectDetail(projectId),
    getProjectProgress(projectId),
  ])

  currentProjectDetail.value = detailResponse.data
  currentProgress.value = progressResponse.data
}

function mergeNoticeDraft(payload, responseData) {
  return {
    noticeId: responseData.noticeId,
    fileId: responseData.fileId,
    title: responseData.title,
    organizer: payload.organizer,
    deadline: payload.deadline,
    targetGroup: payload.targetGroup,
    rawText: payload.rawText,
    fileName: payload.file?.name ?? payload.fileName ?? '',
    aiSummary: '通知内容已保存，等待执行智能解析。',
    materialRequirements: [],
  }
}

function upsertNoticeOption(notice) {
  if (!notice?.noticeId) {
    return
  }

  const option = {
    value: notice.noticeId,
    label: notice.title,
    deadline: notice.deadline,
  }
  const existingIndex = noticeOptions.value.findIndex((item) => item.value === notice.noticeId)

  if (existingIndex >= 0) {
    noticeOptions.value.splice(existingIndex, 1, option)
    return
  }

  noticeOptions.value = [option, ...noticeOptions.value]
}

async function handleNoticeUpload(payload) {
  loading.uploadNotice = true

  try {
    const response = await uploadNotice(payload)
    currentNotice.value = mergeNoticeDraft(payload, response.data)
    upsertNoticeOption(currentNotice.value)
    ElMessage.success('通知信息已保存')
    scrollToSection('notice')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '保存通知信息失败'))
  } finally {
    loading.uploadNotice = false
  }
}

async function handleNoticeParse(noticeId) {
  loading.parseNotice = true

  try {
    const response = await parseNotice(noticeId)
    watchNoticeTask(response.data)
    currentNotice.value = (await getNoticeDetail(noticeId)).data
    ElMessage.info('AI 解析任务已提交，正在排队')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '通知解析失败'))
  } finally {
    loading.parseNotice = false
  }
}

watch(() => currentNotice.value?.noticeId, async (id) => {
  stopNoticeTask()
  noticeParseTask.value = null
  if (isAdmin.value && id) {
    try { await resumeNoticeTask(id) } catch { /* notice view remains usable */ }
  }
})

async function handleProjectCreate(payload) {
  loading.createProject = true

  try {
    const response = await createProject(payload)
    await refreshProjectState(response.data.projectId)
    lastCheckResult.value = null
    ElMessage.success('项目创建成功')
    scrollToSection('project')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '创建项目失败'))
  } finally {
    loading.createProject = false
  }
}

async function handleMaterialUpload({ requirementId, file, remark }) {
  if (!currentProjectDetail.value?.projectId) {
    ElMessage.warning('请先创建项目，再上传材料')
    return
  }

  loading.uploadMaterialId = requirementId

  try {
    const response = await uploadMaterial({
      projectId: currentProjectDetail.value.projectId,
      requirementId,
      file,
      remark,
    })
    await refreshProjectState(response.data.projectId)

    if (lastCheckResult.value?.projectId === response.data.projectId) {
      lastCheckResult.value = {
        ...lastCheckResult.value,
        completionRate: response.data.completionRate,
        missingMaterials: currentProgress.value?.missingMaterials ?? [],
      }
    }

    ElMessage.success('材料上传成功')
    scrollToSection('material')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '上传材料失败'))
  } finally {
    loading.uploadMaterialId = null
  }
}

async function handleRunMaterialCheck() {
  if (!currentProjectDetail.value?.projectId) {
    ElMessage.warning('请先创建项目，再运行核验')
    return
  }

  loading.runCheck = true

  try {
    const response = await runMaterialCheck(currentProjectDetail.value.projectId)
    lastCheckResult.value = response.data
    await refreshProjectState(currentProjectDetail.value.projectId)
    // 核验会创建 NotifyMessage 和 AgentTaskLog 记录，刷新相关面板
    messageCenterRef.value?.refresh()
    agentTaskLogRef.value?.refresh()
    ElMessage.success('核验完成')
    scrollToSection('material')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '运行核验失败'))
  } finally {
    loading.runCheck = false
  }
}

async function handleSubmitProject() {
  const projectId = currentProjectDetail.value?.projectId
  if (!projectId) return
  const status = currentProjectDetail.value?.status
  loading.runCheck = true
  try {
    if (status === 'REVISION_REQUIRED') {
      await resubmitProject(projectId)
      ElMessage.success('项目已重新提交审核')
    } else {
      await submitProject(projectId)
      ElMessage.success('项目已提交审核，等待教师审核')
    }
    await refreshProjectState(projectId)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '提交审核失败'))
  } finally {
    loading.runCheck = false
  }
}

async function handleAddMember(projectId, payload) {
  loading.addMember = true

  try {
    await addProjectMember(projectId, payload)
    await refreshProjectState(projectId)
    ElMessage.success('成员添加成功')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '添加成员失败'))
  } finally {
    loading.addMember = false
  }
}

async function handleRemoveMember(projectId, memberId) {
  loading.removeMemberId = memberId

  try {
    await removeProjectMember(projectId, memberId)
    await refreshProjectState(projectId)
    ElMessage.success('成员移除成功')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '移除成员失败'))
  } finally {
    loading.removeMemberId = null
  }
}

// ===== Teacher-specific handlers =====

async function handleTeacherSelectProject(project) {
  if (!project?.projectId) return
  await router.push(`/teacher/projects/${project.projectId}/review`)
}

function handleTeacherProjectUpdated(projectData) {
  teacherSelectedProject.value = projectData
  currentProjectDetail.value = projectData
  // 同步刷新 progress（状态/完成率可能已因项目级操作而变更）
  if (projectData?.projectId) {
    getProjectProgress(projectData.projectId)
      .then((r) => { currentProgress.value = r.data })
      .catch(() => {})
  }
}

// ===== Admin-specific handlers =====

async function handleAdminSelectProject(project) {
  if (!project?.projectId) return
  await router.push(`/admin/projects/${project.projectId}`)
}

function handleAdminBackToDashboard() {
  router.push('/admin/projects')
}

// ===== 通知列表 → 通知解析跳转 =====

async function handleSelectNotice(noticeRow) {
  noticeDetailRequestId.value++
  const requestId = noticeDetailRequestId.value
  loadingNoticeDetail.value = true

  try {
    const response = await getNoticeDetail(noticeRow.noticeId)
    // 竞态保护：只有最新请求的结果才生效
    if (requestId !== noticeDetailRequestId.value) return
    currentNotice.value = response.data
    scrollToSection('notice')
  } catch (error) {
    if (requestId !== noticeDetailRequestId.value) return
    ElMessage.error(resolveErrorMessage(error, '获取通知详情失败'))
  } finally {
    if (requestId === noticeDetailRequestId.value) {
      loadingNoticeDetail.value = false
    }
  }
}

function openFileViewer(fileId, fileName) {
  fileViewerFileId.value = fileId
  fileViewerFileName.value = fileName || ''
  fileViewerVisible.value = true
}

function resolveErrorMessage(error, fallback) {
  return (
    error?.response?.data?.message
    || error?.message
    || fallback
  )
}

async function loadRouteProject() {
  const projectId = Number(route.params.projectId)
  if (!projectId) return
  try {
    const response = await getProjectDetail(projectId)
    currentProjectDetail.value = response.data
    teacherSelectedProject.value = response.data
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '获取项目详情失败'))
  }
}

onMounted(async () => {
  if (!isTeacher.value && (!isAdmin.value || pageKey.value === 'admin-notices')) {
    await loadDashboardBootstrap()
  }
  await loadRouteProject()
})

watch(() => route.params.projectId, loadRouteProject)
</script>

<template>
  <AdminDashboard
    v-if="isAdmin && ['admin-users', 'admin-projects'].includes(pageKey)"
    :initial-tab="pageKey === 'admin-projects' ? 'projects' : 'users'"
    embedded
    @select-project="handleAdminSelectProject"
  />
  <template v-else>
    <section v-if="pageKey === 'overview' && isTeacher" class="dashboard-anchor dashboard-anchor--overview">
      <TeacherDashboard
        :current-user="currentUser"
        @select-project="handleTeacherSelectProject"
      />
    </section>

    <!-- 学生 overview: 项目总览卡片 -->
    <section v-else-if="pageKey === 'overview'" class="dashboard-anchor dashboard-anchor--overview">
      <!-- 无项目：引导前往申报大厅 -->
      <div v-if="!currentProjectDetail" class="dashboard-hero">
        <div class="dashboard-hero__project-card">
          <el-empty description="你还没有申报项目">
            <el-button type="primary" @click="scrollToSection('noticeList')">
              前往申报大厅
            </el-button>
          </el-empty>
        </div>
      </div>

      <!-- 有项目：显示项目总览卡片 -->
      <div v-else class="dashboard-hero">
        <div class="dashboard-hero__project-card">
          <div class="dashboard-hero__project-head">
            <div>
              <span>当前项目</span>
              <strong>{{ currentProjectDetail?.projectName || '尚未创建项目' }}</strong>
            </div>
            <StatusTag
              :status="currentProjectDetail?.status"
              :label="projectStatusMeta.label"
              :tone="projectStatusMeta.tagType"
              strong
            />
          </div>

          <div class="dashboard-hero__project-grid">
            <div>
              <span>关联通知</span>
              <strong>{{ currentNotice?.title || '待上传通知' }}</strong>
            </div>
            <div>
              <span>截止时间</span>
              <strong>{{ currentProgress?.deadline ? formatDateTime(currentProgress.deadline) : '待同步' }}</strong>
            </div>
            <div>
              <span>当前完成率</span>
              <strong>{{ currentProgress ? formatPercent(currentProgress.completionRate) : '0%' }}</strong>
            </div>
            <div>
              <span>项目状态</span>
              <strong>{{ projectStatusMeta.label }}</strong>
            </div>
          </div>

          <div class="dashboard-hero__materials">
            <div class="dashboard-hero__materials-card">
              <div class="dashboard-hero__materials-head">
                <span>已提交材料</span>
                <strong>{{ currentProgress?.submittedTotal ?? 0 }}</strong>
              </div>
              <div v-if="submittedMaterials.length" class="dashboard-hero__materials-list">
                <span
                  v-for="item in submittedMaterials"
                  :key="`submitted-${item.requirementId}`"
                  class="dashboard-hero__materials-item"
                >
                  {{ item.requirementName }}
                </span>
              </div>
              <p v-else class="dashboard-hero__materials-empty">当前暂无已提交材料</p>
            </div>

            <div class="dashboard-hero__materials-card dashboard-hero__materials-card--danger">
              <div class="dashboard-hero__materials-head">
                <span>待补材料</span>
                <strong>{{ currentProgress?.missingTotal ?? 0 }}</strong>
              </div>
              <div v-if="missingMaterials.length" class="dashboard-hero__materials-list">
                <span
                  v-for="item in missingMaterials"
                  :key="`missing-${item.requirementId}`"
                  class="dashboard-hero__materials-item dashboard-hero__materials-item--danger"
                >
                  {{ item.requirementName }}
                </span>
              </div>
              <p v-else class="dashboard-hero__materials-empty">当前无待补材料</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <div class="dashboard-grid">
      <section v-if="['notice-list', 'teacher-projects', 'admin-notices'].includes(pageKey)" class="dashboard-anchor dashboard-grid__notice-list">
        <NoticeListPanel
          :loading-detail="loadingNoticeDetail"
          :role="currentUser?.role"
          @select-notice="handleSelectNotice"
          @create-project="scrollToSection('project')"
        />
      </section>

      <section v-if="isAdmin && pageKey === 'admin-notices'" class="dashboard-anchor dashboard-grid__notice">
        <NoticeUploadPanel
          :notice="currentNotice"
          :parse-task="noticeParseTask"
          :loading-upload="loading.uploadNotice"
          :loading-parse="loading.parseNotice"
          :bootstrapping="loading.bootstrap"
          @upload="handleNoticeUpload"
          @parse="handleNoticeParse"
          @clear-notice="currentNotice = null"
        />
      </section>

      <section v-if="pageKey === 'student-project'" class="dashboard-anchor dashboard-grid__project">
        <ProjectCreatePanel
          :notice-options="noticeOptions"
          :user-options="userOptions"
          :project="currentProjectDetail"
          :current-user="currentUser"
          :creating="loading.createProject"
          :adding-member="loading.addMember"
          :removing-member-id="loading.removeMemberId"
          @create="handleProjectCreate"
          @add-member="handleAddMember"
          @remove-member="handleRemoveMember"
        />
      </section>

      <!-- 教师/管理员：材料审核面板 -->
      <section v-if="pageKey === 'review' || pageKey === 'admin-project-detail'" class="dashboard-anchor dashboard-grid__material">
        <template v-if="teacherSelectedProject">
          <div v-if="isAdmin" class="admin-back-bar"><el-button size="small" @click="handleAdminBackToDashboard">← 返回项目总览</el-button></div>
          <MaterialReviewPanel
            :project="teacherSelectedProject"
            :current-user="currentUser"
            @project-updated="handleTeacherProjectUpdated"
          />
        </template>
        <el-empty v-else description="当前没有待审核项目" />
      </section>

      <!-- 学生：材料检查面板 -->
      <section v-if="pageKey === 'student-materials'" class="dashboard-anchor dashboard-grid__material">
        <MaterialCheckPanel
          :project="currentProjectDetail"
          :progress="currentProgress"
          :ai-result="lastCheckResult"
          :upload-material-id="loading.uploadMaterialId"
          :checking="loading.runCheck"
          @upload-material="handleMaterialUpload"
          @run-check="handleRunMaterialCheck"
          @view-file="openFileViewer"
          @submit-project="handleSubmitProject"
        />
      </section>

      <!-- 消息中心 -->
      <section v-if="pageKey === 'messages'" class="dashboard-anchor dashboard-grid__messages">
        <MessageCenterPanel
          ref="messageCenterRef"
          :current-user="currentUser"
        />
      </section>

      <!-- AI 任务日志：仅管理员 -->
      <section v-if="isAdmin && pageKey === 'admin-ai-tasks'" class="dashboard-anchor dashboard-grid__logs">
        <AgentTaskLogPanel ref="agentTaskLogRef" :current-user="currentUser" />
      </section>

      <!-- 审核记录：教师（P0 阶段精简占位） -->
      <section v-if="isTeacher && pageKey === 'review-history'" class="dashboard-anchor dashboard-grid__logs">
        <FeaturePanel title="审核记录" subtitle="查看您的历史审核操作记录。">
          <el-empty description="审核记录功能将在后续版本中完善" :image-size="60" />
        </FeaturePanel>
      </section>
    </div>
  </template>

  <FileContentViewer
    :file-id="fileViewerFileId"
    :file-name="fileViewerFileName"
    :visible="fileViewerVisible"
    @close="fileViewerVisible = false"
  />

</template>

<style scoped lang="scss">
.app-shell__user-info {
  display: inline-flex;
  align-items: center;
  padding: 0 4px;
  font-size: 16px;
  font-weight: 500;
  color: var(--app-text-primary, #374151);
  white-space: nowrap;
}

.dashboard-anchor {
  scroll-margin-top: 84px;
}

.admin-back-bar {
  margin-bottom: 16px;
}

.dashboard-anchor--overview {
  display: grid;
  gap: 24px;
  margin-bottom: 24px;
}

.dashboard-actions {
  display: none;
}

.dashboard-hero {
  display: block;
  padding: 0;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
  box-shadow: var(--app-shadow-sm);
}

.dashboard-hero__project-card {
  display: grid;
  gap: 24px;
  padding: 24px;
  background: #ffffff;
}

.dashboard-hero__project-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.dashboard-hero__project-head span,
.dashboard-hero__project-grid span {
  display: block;
  margin-bottom: 6px;
  color: var(--app-text-muted);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.05em;
  line-height: 1.3;
  text-transform: uppercase;
}

.dashboard-hero__project-head strong,
.dashboard-hero__project-grid strong {
  color: var(--app-text-primary);
  font-size: 20px;
  font-weight: 600;
  line-height: 1.45;
}

.dashboard-hero__project-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.dashboard-hero__project-grid > div {
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface-soft);
}

.dashboard-hero__materials {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.dashboard-hero__materials-card {
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: var(--app-surface-soft);
}

.dashboard-hero__materials-card--danger {
  border-color: var(--app-border);
  background: var(--app-surface-soft);
}

.dashboard-hero__materials-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.dashboard-hero__materials-head span {
  color: var(--app-text-muted);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.dashboard-hero__materials-head strong {
  color: var(--app-text-primary);
  font-size: 26px;
  font-weight: 700;
}

.dashboard-hero__materials-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.dashboard-hero__materials-item {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: var(--app-info-bg);
  color: var(--app-info);
  font-size: 15px;
  font-weight: 500;
  line-height: 1.2;
}

.dashboard-hero__materials-item--danger {
  border-color: transparent;
  background: var(--app-danger-bg);
  color: var(--app-danger);
}

.dashboard-hero__materials-empty {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 15px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 32px;
}

@media (max-width: 960px) {
  .dashboard-hero__materials,
  .dashboard-hero__project-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .dashboard-hero__project-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .dashboard-actions {
    width: 100%;
  }
}

</style>
