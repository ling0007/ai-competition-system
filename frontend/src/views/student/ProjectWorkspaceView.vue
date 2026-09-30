<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Users } from 'lucide-vue-next'
import MaterialCheckPanel from '@/components/dashboard/MaterialCheckPanel.vue'
import ProgressOverviewPanel from '@/components/dashboard/ProgressOverviewPanel.vue'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import FileContentViewer from '@/components/shared/FileContentViewer.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import { listMaterialVersions, uploadProjectMaterial, validateMaterialFile } from '@/api/materials'
import {
  addProjectMember, getProject, getProjectProgress, listProjectAiChecks,
  removeProjectMember, runProjectAiCheck, searchUserOptions, submitStudentProject,
} from '@/api/projects'
import { formatDateTime } from '@/utils/format'
import { resolveProjectStatus, resolveStatusMeta } from '@/utils/status'
import { useMaterialCheckTask } from '@/composables/useMaterialCheckTask'
import { useMockApi } from '@/api/client'

const route = useRoute()
const router = useRouter()
const project = ref(null)
const progress = ref(null)
const aiChecks = ref([])
const activeTab = ref('overview')
const loading = ref(false)
const checking = ref(false)
const { task: checkTask, watchTask, resume: resumeCheck } = useMaterialCheckTask({
  onSuccess: async (finished) => {
    checking.value = false
    await loadWorkspace()
    ElMessage.success(finished.resultOrigin === 'FALLBACK'
      ? '系统降级核验完成，请人工核对结果' : 'AI 辅助核验完成')
  },
  onFailure: async (finished) => {
    checking.value = false
    ElMessage.warning(finished.errorSummary || '核验未完成，请重新运行')
  },
})
const uploadMaterialId = ref(null)
const uploadProgress = reactive({})
const uploadErrors = reactive({})
const retryPayloads = new Map()
const historyVisible = ref(false)
const historyLoading = ref(false)
const selectedRequirement = ref(null)
const versions = ref([])
const fileViewerVisible = ref(false)
const fileViewerFileId = ref(null)
const fileViewerFileName = ref('')
const memberOptions = ref([])
const memberUserId = ref(null)
const memberRole = ref('member')
const memberSaving = ref(false)

const projectId = computed(() => Number(route.params.projectId))
const latestAiCheck = computed(() => {
  const latest = aiChecks.value[0]
  if (!latest) return null
  if (latest.stale) return null
  return {
    ...latest,
    projectName: project.value?.projectName,
    reviewResult: latest.result,
    reviewComment: latest.issueSummary || (latest.result === 'PASSED' ? '本次检查未发现明确问题。' : '请查看材料快照并重新核对。'),
    completionRate: progress.value?.completionRate,
    missingMaterials: progress.value?.missingMaterials ?? [],
  }
})
const submittedMaterials = computed(() => project.value?.materials?.filter((item) => item.requiredFlag === 1 && item.uploaded) ?? [])
const canEditTeam = computed(() => project.value?.status === 'DRAFT')
const deadlineExpired = computed(() => progress.value?.deadline && new Date(progress.value.deadline).getTime() < Date.now())

function errorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

async function loadWorkspace() {
  if (!projectId.value) return
  loading.value = true
  try {
    const [detailResponse, progressResponse, checksResponse] = await Promise.all([
      getProject(projectId.value), getProjectProgress(projectId.value), listProjectAiChecks(projectId.value),
    ])
    project.value = detailResponse.data
    progress.value = progressResponse.data
    aiChecks.value = checksResponse.data ?? []
  } catch (error) {
    ElMessage.error(errorMessage(error, '项目工作区加载失败'))
  } finally {
    loading.value = false
  }
}

async function loadMemberOptions() {
  const response = await searchUserOptions(memberRole.value === 'advisor' ? 'teacher' : 'student')
  memberOptions.value = response.data.filter((item) => !project.value?.members?.some((member) => member.userId === item.value))
}

async function handleUpload(payload) {
  if (payload.retry) payload = retryPayloads.get(payload.requirementId)
  if (!payload?.file) return
  const validationError = validateMaterialFile(payload.file)
  if (validationError) {
    uploadErrors[payload.requirementId] = validationError
    ElMessage.warning(validationError)
    return
  }
  retryPayloads.set(payload.requirementId, payload)
  uploadMaterialId.value = payload.requirementId
  uploadProgress[payload.requirementId] = 0
  delete uploadErrors[payload.requirementId]
  try {
    await uploadProjectMaterial({ ...payload, projectId: projectId.value }, (value) => { uploadProgress[payload.requirementId] = value })
    ElMessage.success('材料上传成功，新版本等待人工审核')
    await loadWorkspace()
  } catch (error) {
    uploadErrors[payload.requirementId] = errorMessage(error, '上传失败')
  } finally {
    uploadMaterialId.value = null
    delete uploadProgress[payload.requirementId]
  }
}

async function runCheck() {
  checking.value = true
  try {
    const accepted = await runProjectAiCheck(projectId.value)
    if (useMockApi) {
      await loadWorkspace()
      checking.value = false
      ElMessage.success('AI 辅助核验完成')
    } else {
      watchTask(accepted.data)
      ElMessage.info('核验任务已接受，正在后台处理')
    }
  } catch (error) {
    ElMessage.error(errorMessage(error, 'AI 核验失败'))
    checking.value = false
  }
}

async function submit() {
  if (deadlineExpired.value) return ElMessage.warning('已超过截止时间，不能提交')
  try {
    await ElMessageBox.confirm(
      project.value.status === 'REVISION_REQUIRED' ? '确认按当前材料版本重新提交审核？' : '确认提交项目进入人工审核？',
      '提交确认',
      { type: 'warning' },
    )
    await submitStudentProject(projectId.value, project.value.status)
    await loadWorkspace()
    ElMessage.success(project.value.status === 'UNDER_REVIEW' ? '项目已提交审核' : '操作成功')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(errorMessage(error, '提交失败'))
  }
}

async function showHistory(row) {
  selectedRequirement.value = row
  historyVisible.value = true
  historyLoading.value = true
  try {
    const response = await listMaterialVersions(projectId.value, row.requirementId)
    versions.value = response.data ?? []
  } catch (error) {
    ElMessage.error(errorMessage(error, '版本历史加载失败'))
  } finally { historyLoading.value = false }
}

function viewFile(fileId, fileName) {
  fileViewerFileId.value = fileId
  fileViewerFileName.value = fileName
  fileViewerVisible.value = true
}

async function addMember() {
  if (!memberUserId.value) return
  memberSaving.value = true
  try {
    await addProjectMember(projectId.value, { userId: memberUserId.value, memberRole: memberRole.value })
    memberUserId.value = null
    await loadWorkspace()
    await loadMemberOptions()
    ElMessage.success('团队成员已更新')
  } catch (error) { ElMessage.error(errorMessage(error, '添加成员失败')) }
  finally { memberSaving.value = false }
}

async function removeMember(member) {
  try {
    await ElMessageBox.confirm(`确认移除「${member.realName}」？`, '移除成员', { type: 'warning' })
    await removeProjectMember(projectId.value, member.memberId)
    await loadWorkspace()
  } catch (error) { if (error !== 'cancel') ElMessage.error(errorMessage(error, '移除成员失败')) }
}

watch(memberRole, loadMemberOptions)
onMounted(async () => {
  await loadWorkspace()
  await loadMemberOptions()
  if (!useMockApi && projectId.value) {
    try {
      await resumeCheck(projectId.value)
      checking.value = ['PENDING', 'RUNNING'].includes(checkTask.value?.status)
    } catch { /* Workspace remains usable if task history is unavailable. */ }
  }
})
</script>

<template>
  <div v-loading="loading" class="workspace">
    <div class="workspace__back"><el-button text :icon="ArrowLeft" @click="router.push('/student/projects')">返回我的项目</el-button></div>
    <FeaturePanel v-if="project" :title="project.projectName" :subtitle="project.noticeTitle">
      <template #actions>
        <StatusTag :status="project.status" :label="resolveProjectStatus(project.status).label" :tone="resolveProjectStatus(project.status).tagType" strong />
      </template>

      <el-alert v-if="deadlineExpired" type="warning" title="申报已截止，系统将禁止提交或重新提交。" :closable="false" show-icon />
      <el-tabs v-model="activeTab" class="workspace__tabs">
        <el-tab-pane label="概览" name="overview">
          <ProgressOverviewPanel :project="project" :progress="progress" :submitted-materials="submittedMaterials" />
        </el-tab-pane>
        <el-tab-pane label="材料" name="materials">
          <MaterialCheckPanel
            :project="project" :progress="progress" :ai-result="latestAiCheck"
            :upload-material-id="uploadMaterialId" :upload-progress="uploadProgress" :upload-errors="uploadErrors"
            :checking="checking" :check-task="checkTask"
            :needs-recheck="aiChecks.length > 0 && aiChecks[0].stale"
            @upload-material="handleUpload" @run-check="runCheck"
            @view-file="viewFile" @submit-project="submit" @view-history="showHistory"
          />
        </el-tab-pane>
        <el-tab-pane label="团队" name="team">
          <section class="team-panel">
            <el-alert v-if="!canEditTeam" type="info" title="只有草稿状态允许修改团队成员。" :closable="false" />
            <div v-if="canEditTeam" class="team-panel__add">
              <el-select v-model="memberRole" style="width: 140px"><el-option label="项目成员" value="member" /><el-option label="指导教师" value="advisor" /></el-select>
              <el-select v-model="memberUserId" filterable clearable placeholder="选择用户" style="min-width: 240px"><el-option v-for="item in memberOptions" :key="item.value" :label="item.label" :value="item.value" /></el-select>
              <el-button type="primary" :icon="Users" :loading="memberSaving" @click="addMember">添加</el-button>
            </div>
            <div class="team-panel__list">
              <article v-for="member in project.members" :key="member.memberId">
                <div><strong>{{ member.realName }}</strong><span>{{ { leader: '负责人', advisor: '指导教师', member: '项目成员' }[member.memberRole] }}</span></div>
                <el-button v-if="canEditTeam && member.memberRole !== 'leader'" text type="danger" @click="removeMember(member)">移除</el-button>
              </article>
            </div>
          </section>
        </el-tab-pane>
        <el-tab-pane label="历史" name="history">
          <div class="history-panel">
            <section><h3>AI 检查历史</h3><el-timeline v-if="aiChecks.length"><el-timeline-item v-for="item in aiChecks" :key="item.id" :timestamp="formatDateTime(item.checkedAt)" placement="top"><div class="history-panel__entry"><StatusTag :status="item.result" :label="resolveStatusMeta(item.result).label" :tone="item.stale ? 'warning' : resolveStatusMeta(item.result).tagType" /><strong>{{ item.stale ? '结果已过期' : '对应当前材料快照' }}</strong><p>{{ item.issueSummary || '本次检查未发现明确问题。' }}</p><small>材料版本：{{ item.materialVersionIds?.join('、') || '无' }}；AI 结果不能替代人工审核。</small></div></el-timeline-item></el-timeline><el-empty v-else description="尚未运行 AI 核验" /></section>
            <section><h3>项目审核时间线</h3><el-timeline v-if="project.reviewRecords?.length"><el-timeline-item v-for="item in project.reviewRecords" :key="item.reviewId" :timestamp="formatDateTime(item.createdAt)" placement="top"><div class="history-panel__entry"><strong>{{ item.reviewerName || (item.reviewType === 'ai' ? 'AI 辅助检查' : '审核人员') }}</strong><p>{{ item.reviewComment }}</p></div></el-timeline-item></el-timeline><el-empty v-else description="暂无项目审核记录" /></section>
          </div>
        </el-tab-pane>
      </el-tabs>
    </FeaturePanel>

    <el-drawer v-model="historyVisible" :title="`${selectedRequirement?.requirementName || ''} · 版本历史`" size="520px">
      <div v-loading="historyLoading" class="version-list">
        <article v-for="item in versions" :key="item.materialId">
          <div class="version-list__head"><strong>V{{ item.versionNo }} · {{ item.fileName }}</strong><el-tag :type="item.currentVersion ? 'success' : 'info'">{{ item.currentVersion ? '当前版本' : '历史版本' }}</el-tag></div>
          <p>上传于 {{ formatDateTime(item.submittedAt) }} · {{ item.remark || '无备注' }}</p>
          <p>人工审核：{{ item.reviewStatus ? resolveStatusMeta(item.reviewStatus).label : '未审核' }}<span v-if="item.reviewComment"> · {{ item.reviewComment }}</span></p>
          <el-button size="small" @click="viewFile(item.fileId, item.fileName)">查看文件</el-button>
        </article>
        <el-empty v-if="!historyLoading && !versions.length" description="暂无历史版本" />
      </div>
    </el-drawer>

    <FileContentViewer
      :visible="fileViewerVisible"
      :file-id="fileViewerFileId"
      :file-name="fileViewerFileName"
      @close="fileViewerVisible = false"
    />
  </div>
</template>

<style scoped lang="scss">
.workspace { display: grid; gap: 12px; }
.workspace__back { justify-self: start; }
.workspace__tabs { margin-top: 14px; }
.team-panel, .history-panel { display: grid; gap: 18px; }
.team-panel__add { display: flex; flex-wrap: wrap; gap: 12px; }
.team-panel__list { display: grid; gap: 10px; }
.team-panel__list article, .version-list article, .history-panel__entry { padding: 16px; border: 1px solid var(--app-border); border-radius: var(--app-radius-sm); background: #fff; }
.team-panel__list article { display: flex; justify-content: space-between; align-items: center; }
.team-panel__list article div { display: grid; gap: 5px; }
.team-panel__list span, .version-list p, .history-panel__entry p, .history-panel__entry small { color: var(--app-text-tertiary); }
.history-panel { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.history-panel h3 { margin-top: 0; }
.version-list { display: grid; gap: 12px; }
.version-list__head { display: flex; justify-content: space-between; gap: 12px; }
@media (max-width: 900px) { .history-panel { grid-template-columns: 1fr; } }
</style>
