<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import {
  ClipboardCheck,
  FileText,
  Files,
  LayoutDashboard,
  School,
} from 'lucide-vue-next'
import AppShell from '@/components/layout/AppShell.vue'
import LoginPage from '@/components/auth/LoginPage.vue'
import RegisterPage from '@/components/auth/RegisterPage.vue'
import MaterialCheckPanel from '@/components/dashboard/MaterialCheckPanel.vue'
import MaterialReviewPanel from '@/components/dashboard/MaterialReviewPanel.vue'
import NoticeUploadPanel from '@/components/dashboard/NoticeUploadPanel.vue'
import ProjectCreatePanel from '@/components/dashboard/ProjectCreatePanel.vue'
import TeacherDashboard from '@/components/dashboard/TeacherDashboard.vue'
import FileContentViewer from '@/components/shared/FileContentViewer.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import {
  addProjectMember,
  createProject,
  getDashboardBootstrap,
  getProjectDetail,
  getProjectProgress,
  getMyProjects,
  parseNotice,
  removeProjectMember,
  runMaterialCheck,
  uploadMaterial,
  uploadNotice,
} from '@/api/competition'
import { formatDateTime, formatPercent } from '@/utils/format'
import { resolveStatusMeta } from '@/utils/status'

const sidebarCollapsed = ref(false)
const activeMenu = ref('overview')
const noticeOptions = ref([])
const userOptions = ref([])
const currentNotice = ref(null)
const currentProjectDetail = ref(null)
const currentProgress = ref(null)
const lastCheckResult = ref(null)
const currentView = ref('login')
const currentUser = ref(null)

const overviewSection = ref(null)
const noticeSection = ref(null)
const projectSection = ref(null)
const materialSection = ref(null)

let sectionObserver = null

// Teacher-specific state
const teacherProjects = ref([])
const teacherSelectedProject = ref(null)
const teacherLoadingProjects = ref(false)
const fileViewerVisible = ref(false)
const fileViewerFileId = ref(null)
const fileViewerFileName = ref('')
const isTeacher = computed(() => currentUser.value?.role === 'teacher')

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

const studentMenuItems = [
  { key: 'overview', label: '总览看板', icon: LayoutDashboard },
  { key: 'notice', label: '通知解析', icon: FileText },
  { key: 'project', label: '项目申报', icon: Files },
  { key: 'material', label: '材料检查', icon: ClipboardCheck },
]

const teacherMenuItems = [
  { key: 'overview', label: '我的项目', icon: School },
  { key: 'notice', label: '通知解析', icon: FileText },
  { key: 'project', label: '项目申报', icon: Files },
  { key: 'material', label: '材料审核', icon: ClipboardCheck },
]

const menuItems = computed(() =>
  isTeacher.value ? teacherMenuItems : studentMenuItems,
)

const submittedMaterials = computed(
  () => currentProjectDetail.value?.materials?.filter((item) => item.submitStatus === 'submitted') ?? [],
)

const missingMaterials = computed(
  () => currentProjectDetail.value?.materials?.filter((item) => item.submitStatus !== 'submitted') ?? [],
)

const projectStatusMeta = computed(() => resolveStatusMeta(currentProjectDetail.value?.status))

const shellSummary = computed(() => [
  {
    label: '项目状态',
    value: projectStatusMeta.value.label,
  },
  {
    label: '完成率',
    value: currentProgress.value ? formatPercent(currentProgress.value.completionRate) : '0%',
  },
  {
    label: '截止时间',
    value: currentProgress.value?.deadline ? formatDateTime(currentProgress.value.deadline) : '待同步',
  },
])

function getSectionElements() {
  return {
    overview: overviewSection.value,
    notice: noticeSection.value,
    project: projectSection.value,
    material: materialSection.value,
  }
}

function scrollToSection(key) {
  const target = getSectionElements()[key]

  if (!target) {
    return
  }

  activeMenu.value = key
  target.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  })
}

function initSectionObserver() {
  sectionObserver?.disconnect()

  sectionObserver = new IntersectionObserver(
    (entries) => {
      const visibleEntry = entries
        .filter((entry) => entry.isIntersecting)
        .sort((left, right) => right.intersectionRatio - left.intersectionRatio)[0]

      if (visibleEntry?.target?.dataset?.section) {
        activeMenu.value = visibleEntry.target.dataset.section
      }
    },
    {
      threshold: [0.3, 0.55, 0.75],
      rootMargin: '-80px 0px -35% 0px',
    },
  )

  Object.entries(getSectionElements()).forEach(([key, element]) => {
    if (!element) {
      return
    }

    element.dataset.section = key
    sectionObserver.observe(element)
  })
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
    currentNotice.value = {
      ...currentNotice.value,
      ...response.data,
    }
    upsertNoticeOption(currentNotice.value)
    ElMessage.success('通知解析完成')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '通知解析失败'))
  } finally {
    loading.parseNotice = false
  }
}

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
    ElMessage.success('核验完成')
    scrollToSection('material')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '运行核验失败'))
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
  teacherSelectedProject.value = null
  try {
    const response = await getProjectDetail(project.projectId)
    teacherSelectedProject.value = response.data
    currentProjectDetail.value = response.data
    scrollToSection('material')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '获取项目详情失败'))
  }
}

function handleTeacherProjectUpdated(projectData) {
  teacherSelectedProject.value = projectData
  currentProjectDetail.value = projectData
}

function handleTeacherCreateProject() {
  scrollToSection('project')
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

function decodeTokenPayload(token) {
  try {
    const payload = token.split('.')[1] || token
    const binary = atob(payload)
    const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes))
  } catch {
    return null
  }
}

function enterDashboard(userData) {
  if (!userData?.token) {
    ElMessage.error('登录数据异常，请重试')
    return
  }
  localStorage.setItem('auth_token', userData.token)
  currentUser.value = {
    userId: userData.userId,
    username: userData.username,
    realName: userData.realName,
    role: userData.role,
  }
  currentView.value = 'dashboard'
  nextTick(async () => {
    await loadDashboardBootstrap()
    await nextTick()
    initSectionObserver()
  })
}

function handleLoginSuccess(userData) {
  enterDashboard(userData)
}

function handleLogout() {
  localStorage.removeItem('auth_token')
  currentUser.value = null
  currentView.value = 'login'
  sectionObserver?.disconnect()
}

onMounted(async () => {
  const token = localStorage.getItem('auth_token')
  if (token) {
    const payload = decodeTokenPayload(token)
    if (payload && payload.exp * 1000 > Date.now()) {
      currentUser.value = {
        userId: Number(payload.sub) || payload.userId,
        username: payload.username,
        role: payload.role,
      }
      currentView.value = 'dashboard'
      await loadDashboardBootstrap()
      await nextTick()
      initSectionObserver()
      return
    }
    localStorage.removeItem('auth_token')
  }
  currentView.value = 'login'
})

onBeforeUnmount(() => {
  sectionObserver?.disconnect()
})
</script>

<template>
  <LoginPage
    v-if="currentView === 'login'"
    @login-success="handleLoginSuccess"
    @switch-to-register="currentView = 'register'"
  />
  <RegisterPage
    v-else-if="currentView === 'register'"
    @switch-to-login="currentView = 'login'"
  />
  <AppShell
    v-else
    :collapsed="sidebarCollapsed"
    :active-menu="activeMenu"
    :menu-items="menuItems"
    :summary-items="shellSummary"
    @toggle-sidebar="sidebarCollapsed = !sidebarCollapsed"
    @select-menu="scrollToSection"
  >
    <template #hero-actions>
      <span class="app-shell__user-info">{{ currentUser?.realName || currentUser?.username }}</span>
      <el-button v-if="isTeacher" type="primary" @click="scrollToSection('overview')">我的项目</el-button>
      <el-button v-else type="primary" @click="scrollToSection('project')">新建项目</el-button>
      <el-button class="app-button--secondary" @click="scrollToSection('notice')">上传通知</el-button>
      <el-button v-if="isTeacher" class="app-button--secondary" @click="scrollToSection('material')">材料审核</el-button>
      <el-button v-else class="app-button--secondary" @click="scrollToSection('material')">查看材料清单</el-button>
      <el-button class="app-button--outline" @click="handleLogout">退出登录</el-button>
    </template>

    <!-- 教师 overview: 项目列表 -->
    <section v-if="isTeacher" ref="overviewSection" class="dashboard-anchor dashboard-anchor--overview">
      <TeacherDashboard
        :current-user="currentUser"
        @select-project="handleTeacherSelectProject"
        @create-project="handleTeacherCreateProject"
      />
    </section>

    <!-- 学生 overview: 项目总览卡片 -->
    <section v-else ref="overviewSection" class="dashboard-anchor dashboard-anchor--overview">
      <div class="dashboard-actions">
        <el-button type="primary" @click="scrollToSection('project')">新建项目</el-button>
        <el-button class="app-button--secondary" @click="scrollToSection('notice')">上传通知</el-button>
        <el-button class="app-button--secondary" @click="scrollToSection('material')">查看材料清单</el-button>
      </div>

      <div class="dashboard-hero">
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
      <section ref="noticeSection" class="dashboard-anchor dashboard-grid__notice">
        <NoticeUploadPanel
          :notice="currentNotice"
          :loading-upload="loading.uploadNotice"
          :loading-parse="loading.parseNotice"
          :bootstrapping="loading.bootstrap"
          @upload="handleNoticeUpload"
          @parse="handleNoticeParse"
        />
      </section>

      <section ref="projectSection" class="dashboard-anchor dashboard-grid__project">
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

      <!-- 教师：材料审核面板 -->
      <section v-if="isTeacher" ref="materialSection" class="dashboard-anchor dashboard-grid__material">
        <template v-if="teacherSelectedProject">
          <MaterialReviewPanel
            :project="teacherSelectedProject"
            :current-user="currentUser"
            @project-updated="handleTeacherProjectUpdated"
          />
        </template>
        <el-empty v-else description="请从上方「我的项目」中选择一个项目进行材料审核" />
      </section>

      <!-- 学生：材料检查面板 -->
      <section v-else ref="materialSection" class="dashboard-anchor dashboard-grid__material">
        <MaterialCheckPanel
          :project="currentProjectDetail"
          :ai-result="lastCheckResult"
          :upload-material-id="loading.uploadMaterialId"
          :checking="loading.runCheck"
          @upload-material="handleMaterialUpload"
          @run-check="handleRunMaterialCheck"
          @view-file="openFileViewer"
        />
      </section>
    </div>
  </AppShell>

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
  font-size: 13px;
  font-weight: 500;
  color: var(--app-text-primary, #374151);
  white-space: nowrap;
}

.dashboard-anchor {
  scroll-margin-top: 84px;
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
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  line-height: 1.3;
  text-transform: uppercase;
}

.dashboard-hero__project-head strong,
.dashboard-hero__project-grid strong {
  color: var(--app-text-primary);
  font-size: 16px;
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
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.dashboard-hero__materials-head strong {
  color: var(--app-text-primary);
  font-size: 20px;
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
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  border: 1px solid transparent;
  background: var(--app-info-bg);
  color: var(--app-info);
  font-size: 12px;
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
  font-size: 12px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 24px;
}

.dashboard-grid__material {
  grid-column: 1 / -1;
}

@media (max-width: 1280px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .dashboard-grid__material {
    grid-column: auto;
  }
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
