<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Eye, FileEdit, RefreshCw } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import FileContentViewer from '@/components/shared/FileContentViewer.vue'
import { formatDateTime } from '@/utils/format'
import { resolveMaterialReviewDecision, resolveProjectStatus } from '@/utils/status'
import { getProjectDetail, reviewMaterial, approveProject, requestRevision } from '@/api/competition'

const props = defineProps({
  project: {
    type: Object,
    default: null,
  },
  currentUser: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['project-updated'])

const reviewing = ref(false)
const reviewCommentInput = ref('')
const showCommentDialog = ref(false)
const pendingReviewMaterial = ref(null)

// Project-level dialog state
const showProjectCommentDialog = ref(false)
const projectCommentInput = ref('')
const projectActionLoading = ref(false)
const reReviewMaterialId = ref(null) // 正在"重新审核"的材料 ID

// File viewer state
const fileViewerVisible = ref(false)
const viewerFileId = ref(null)
const viewerFileName = ref('')

const materials = computed(() => props.project?.materials ?? [])

const submittedMaterials = computed(() =>
  materials.value.filter((m) => m.uploaded === true),
)

// 必交材料（requiredFlag === 1）
const requiredMaterials = computed(() =>
  materials.value.filter((m) => m.requiredFlag === 1),
)

// 统计：已审核 / 已提交的必交材料数
const requiredReviewedCount = computed(() =>
  requiredMaterials.value.filter((m) => m.reviewStatus != null).length,
)

const requiredSubmittedCount = computed(() =>
  requiredMaterials.value.filter((m) => m.uploaded === true).length,
)

const blockers = computed(() => requiredMaterials.value
  .filter((m) => !m.uploaded || m.reviewStatus !== 'APPROVED')
  .map((m) => `${m.requirementName}：${!m.uploaded ? '未上传' : m.reviewStatus === 'REVISION_REQUIRED' ? '需修改' : '当前版本未审核'}`))

// 前端只按生命周期展示操作；材料完整性与当前版本审核由后端最终校验。
const canApproveProject = computed(() =>
  props.project?.status === 'UNDER_REVIEW' && blockers.value.length === 0,
)

const stats = computed(() => {
  const total = materials.value.length
  const submitted = submittedMaterials.value.length
  const approved = materials.value.filter((m) => m.reviewStatus === 'APPROVED').length
  const revision = materials.value.filter((m) => m.reviewStatus === 'REVISION_REQUIRED').length
  const pendingReview = submitted - approved - revision
  const unreviewed = total - approved - revision
  return { total, submitted, approved, revision, pendingReview, unreviewed }
})

function getSubmitStatusMeta(row) {
  return row.uploaded === true
    ? { label: '已提交', tagType: 'success' }
    : { label: '待上传', tagType: 'info' }
}

function getReviewStatusMeta(status) {
  if (!status) return { label: '未审核', tagType: 'info' }
  return resolveMaterialReviewDecision(status)
}

function openFileViewer(fileId, fileName) {
  viewerFileId.value = fileId
  viewerFileName.value = fileName || ''
  fileViewerVisible.value = true
}

// ===== 单项材料审核 =====

async function handleApprove(material) {
  if (!props.project?.projectId) return
  reviewing.value = true
  try {
    await reviewMaterial({
      projectId: props.project.projectId,
      materialId: material.materialId,
      reviewStatus: 'APPROVED',
      reviewComment: '材料审核通过，内容完整符合要求。',
    })
    ElMessage.success(`材料「${material.requirementName}」审核通过`)
    reReviewMaterialId.value = null
    await refreshState()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error.message || '审核操作失败')
  } finally {
    reviewing.value = false
  }
}

function handleRequestRevision(material) {
  pendingReviewMaterial.value = material
  reviewCommentInput.value = material.reviewComment || ''
  showCommentDialog.value = true
}

function handleStartReReview(material) {
  reReviewMaterialId.value = reReviewMaterialId.value === material.materialId ? null : material.materialId
}

async function submitRevision() {
  if (!pendingReviewMaterial.value || !props.project?.projectId) return
  reviewing.value = true
  try {
    await reviewMaterial({
      projectId: props.project.projectId,
      materialId: pendingReviewMaterial.value.materialId,
      reviewStatus: 'REVISION_REQUIRED',
      reviewComment: reviewCommentInput.value || '请修改后重新提交。',
    })
    ElMessage.success(`已向学生发送修改意见：${pendingReviewMaterial.value.requirementName}`)
    showCommentDialog.value = false
    pendingReviewMaterial.value = null
    reviewCommentInput.value = ''
    reReviewMaterialId.value = null
    await refreshState()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error.message || '操作失败')
  } finally {
    reviewing.value = false
  }
}

function cancelComment() {
  showCommentDialog.value = false
  pendingReviewMaterial.value = null
  reviewCommentInput.value = ''
}

// ===== 项目级审核决定 =====

function handleRequestRevisionClick() {
  projectCommentInput.value = ''
  showProjectCommentDialog.value = true
}

async function submitProjectRevision() {
  if (!projectCommentInput.value.trim()) {
    ElMessage.warning('请填写具体退回原因')
    return
  }
  projectActionLoading.value = true
  try {
    await requestRevision(props.project.projectId, projectCommentInput.value.trim())
    ElMessage.success('项目已退回修改')
    showProjectCommentDialog.value = false
    projectCommentInput.value = ''
    await refreshState()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error.message || '操作失败')
  } finally {
    projectActionLoading.value = false
  }
}

function cancelProjectComment() {
  showProjectCommentDialog.value = false
  projectCommentInput.value = ''
}

async function handleApproveProject() {
  try {
    await ElMessageBox.confirm(
      '确认通过该项目？通过后项目状态将变为"审核通过"，学生将收到通知。',
      '确认操作',
      { confirmButtonText: '确认通过', cancelButtonText: '取消', type: 'success' },
    )
  } catch {
    return // 用户取消
  }
  projectActionLoading.value = true
  try {
    await approveProject(props.project.projectId)
    ElMessage.success('项目已审核通过')
    await refreshState()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error.message || '操作失败')
  } finally {
    projectActionLoading.value = false
  }
}

// ===== 通用 =====

async function refreshState() {
  try {
    const response = await getProjectDetail(props.project.projectId)
    emit('project-updated', response.data)
  } catch {
    // silent
  }
}

function avatarName(fileName) {
  if (!fileName) return '?'
  const dot = fileName.lastIndexOf('.')
  return dot > 0 ? fileName.substring(dot + 1).toUpperCase() : fileName.substring(0, 2).toUpperCase()
}
</script>

<template>
  <FeaturePanel
    title="材料审核"
    subtitle="查看学生提交的材料内容，进行审核（通过）或留下修改意见。"
  >
    <template #actions>
      <div class="review-panel__stats">
        <span class="review-panel__stat-item">
          已提交 <strong>{{ stats.submitted }}</strong>
        </span>
        <span class="review-panel__stat-item review-panel__stat-item--success">
          已通过 <strong>{{ stats.approved }}</strong>
        </span>
        <span class="review-panel__stat-item review-panel__stat-item--danger">
          需修改 <strong>{{ stats.revision }}</strong>
        </span>
        <span class="review-panel__stat-item review-panel__stat-item--info">
          待审核 <strong>{{ stats.pendingReview }}</strong>
        </span>
      </div>
    </template>

    <div class="review-panel">
      <el-table
        v-if="materials.length"
        :data="materials"
        stripe
        class="review-panel__table"
      >
        <el-table-column label="材料名称" min-width="160">
          <template #default="{ row }">
            <div class="review-panel__material-name">
              <span class="review-panel__avatar">{{ avatarName(row.fileName) }}</span>
              <div>
                <strong>{{ row.requirementName }}</strong>
                <small>{{ row.fileName }}</small>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="版本" width="80">
          <template #default="{ row }">{{ row.versionNo ? `V${row.versionNo}` : '—' }}</template>
        </el-table-column>

        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">
            {{ formatDateTime(row.submittedAt) }}
          </template>
        </el-table-column>

        <el-table-column label="提交状态" width="100">
          <template #default="{ row }">
            <StatusTag
              :status="row.submitStatus"
              :label="getSubmitStatusMeta(row).label"
              :tone="getSubmitStatusMeta(row).tagType"
            />
          </template>
        </el-table-column>

        <el-table-column label="审核状态" width="100">
          <template #default="{ row }">
            <StatusTag
              :status="row.reviewStatus"
              :label="getReviewStatusMeta(row.reviewStatus).label"
              :tone="getReviewStatusMeta(row.reviewStatus).tagType"
            />
          </template>
        </el-table-column>

        <el-table-column label="审核意见" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.reviewComment" class="review-panel__comment">
              {{ row.reviewComment }}
            </span>
            <span v-else class="review-panel__comment--empty">暂无</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="320" fixed="right">
          <template #default="{ row }">
            <div class="review-panel__actions">
              <el-button
                class="app-button--secondary"
                size="small"
                :icon="Eye"
                :disabled="!row.fileId"
                @click="openFileViewer(row.fileId, row.fileName)"
              >
                查看文件
              </el-button>

              <!-- 未审核 → 通过 + 需修改 -->
              <template v-if="project?.status === 'UNDER_REVIEW' && row.uploaded && !row.reviewStatus">
                <el-button
                  size="small"
                  type="success"
                  :icon="Check"
                  :loading="reviewing && pendingReviewMaterial?.materialId === row.materialId"
                  @click="handleApprove(row)"
                >
                  通过
                </el-button>
                <el-button
                  size="small"
                  type="warning"
                  :icon="FileEdit"
                  @click="handleRequestRevision(row)"
                >
                  需修改
                </el-button>
              </template>

              <!-- 已审核 → Tag + 重新审核 -->
              <template v-else-if="project?.status === 'UNDER_REVIEW' && row.uploaded">
                <el-button
                  size="small"
                  :icon="RefreshCw"
                  @click="handleStartReReview(row)"
                >
                  {{ reReviewMaterialId === row.materialId ? '取消' : '重新审核' }}
                </el-button>
              </template>
            </div>

            <!-- 重新审核展开的操作按钮 -->
            <div v-if="project?.status === 'UNDER_REVIEW' && reReviewMaterialId === row.materialId" class="review-panel__rereview-actions">
              <el-button
                size="small"
                type="success"
                :icon="Check"
                :loading="reviewing"
                @click="handleApprove(row)"
              >
                通过
              </el-button>
              <el-button
                size="small"
                type="warning"
                :icon="FileEdit"
                @click="handleRequestRevision(row)"
              >
                需修改
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-else
        description="暂无学生提交的材料需要审核。"
      />
    </div>

    <!-- 项目级操作区 -->
    <template v-if="project?.status">
      <el-divider />
      <div class="project-action-bar">
        <!-- UNDER_REVIEW：项目审核决定 -->
        <template v-if="project.status === 'UNDER_REVIEW'">
          <div class="project-action-bar__header">
            <h4>项目审核决定</h4>
            <StatusTag :status="project.status" :label="resolveProjectStatus(project.status).label" :tone="resolveProjectStatus(project.status).tagType" />
          </div>
          <div class="project-action-bar__progress">
            <span>必交材料审核进度：{{ requiredReviewedCount }} / {{ requiredSubmittedCount }}</span>
          </div>
          <el-alert v-if="blockers.length" title="项目通过前仍有阻断项（后端会再次校验）" type="warning" :closable="false">
            <ul><li v-for="item in blockers" :key="item">{{ item }}</li></ul>
          </el-alert>
          <el-alert v-else title="当前必交材料均已通过；最终决定以服务端校验为准。" type="success" :closable="false" />
          <div class="project-action-bar__actions">
            <el-button type="warning" @click="handleRequestRevisionClick">退回修改</el-button>
            <el-tooltip content="后端将校验全部必交材料及其当前版本审核结果">
              <el-button
                type="success"
                :disabled="!canApproveProject"
                :loading="projectActionLoading"
                @click="handleApproveProject"
              >
                通过项目
              </el-button>
            </el-tooltip>
          </div>
        </template>

        <!-- REVISION_REQUIRED -->
        <template v-else-if="project.status === 'REVISION_REQUIRED'">
          <div class="project-action-bar__header">
            <h4>项目状态</h4>
            <StatusTag :status="project.status" :label="resolveProjectStatus(project.status).label" :tone="resolveProjectStatus(project.status).tagType" />
          </div>
          <el-alert type="warning" :closable="false" show-icon>
            项目已退回修改，等待学生修改后重新提交。
          </el-alert>
        </template>

        <!-- APPROVED -->
        <template v-else-if="project.status === 'APPROVED'">
          <div class="project-action-bar__header">
            <h4>项目状态</h4>
            <StatusTag :status="project.status" :label="resolveProjectStatus(project.status).label" :tone="resolveProjectStatus(project.status).tagType" />
          </div>
          <el-alert type="success" :closable="false" show-icon>
            项目已审核通过。
          </el-alert>
        </template>

        <!-- DRAFT -->
        <template v-else-if="project.status === 'DRAFT'">
          <el-alert type="info" :closable="false" show-icon>
            项目尚未提交审核，提交后教师可进行审核。
          </el-alert>
        </template>
      </div>
    </template>

    <!-- 修改意见对话框（单项材料） -->
    <el-dialog
      v-model="showCommentDialog"
      title="填写修改意见"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item :label="`为「${pendingReviewMaterial?.requirementName || ''}」填写修改意见：`">
          <el-input
            v-model="reviewCommentInput"
            type="textarea"
            :rows="5"
            placeholder="请具体说明哪些内容需要修改，以便学生理解。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelComment">取消</el-button>
        <el-button type="primary" :loading="reviewing" @click="submitRevision">
          提交意见
        </el-button>
      </template>
    </el-dialog>

    <!-- 项目退回修改意见对话框 -->
    <el-dialog
      v-model="showProjectCommentDialog"
      title="填写退回修改意见"
      width="500px"
      :close-on-click-modal="false"
    >
      <el-form label-position="top">
        <el-form-item label="为项目填写总体退回修改意见：">
          <el-input
            v-model="projectCommentInput"
            type="textarea"
            :rows="5"
            placeholder="请说明退回原因和需要修改的内容，学生将收到此意见。"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelProjectComment">取消</el-button>
        <el-button type="primary" :disabled="!projectCommentInput.trim()" :loading="projectActionLoading" @click="submitProjectRevision">
          确认退回
        </el-button>
      </template>
    </el-dialog>

    <!-- 文件内容查看器 -->
    <FileContentViewer
      :file-id="viewerFileId"
      :file-name="viewerFileName"
      :visible="fileViewerVisible"
      @close="fileViewerVisible = false"
    />
  </FeaturePanel>
</template>

<style scoped lang="scss">
.review-panel {
  display: grid;
  gap: 20px;
}

.review-panel__stats {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
}

.review-panel__stat-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border: 1px solid var(--app-border);
  border-radius: 999px;
  font-size: 12px;
  color: var(--app-text-secondary);
  background: var(--app-surface-soft);
}

.review-panel__stat-item strong {
  font-weight: 700;
  color: var(--app-text-primary);
}

.review-panel__stat-item--success strong {
  color: var(--app-success, #10b981);
}

.review-panel__stat-item--danger strong {
  color: var(--app-danger, #ef4444);
}

.review-panel__stat-item--info strong {
  color: var(--app-info, #6366f1);
}

.review-panel__material-name {
  display: flex;
  align-items: center;
  gap: 10px;
}

.review-panel__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 6px;
  background: var(--app-primary-bg);
  color: var(--app-primary);
  font-size: 11px;
  font-weight: 700;
  flex-shrink: 0;
}

.review-panel__material-name div {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.review-panel__material-name strong {
  color: var(--app-text-primary);
  font-size: 14px;
  font-weight: 600;
}

.review-panel__material-name small {
  color: var(--app-text-muted);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-panel__comment {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.review-panel__comment--empty {
  color: var(--app-text-muted);
  font-size: 12px;
  font-style: italic;
}

.review-panel__actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.review-panel__actions > .el-button + .el-button,
.review-panel__rereview-actions > .el-button + .el-button {
  margin-left: 0;
}

.review-panel__table {
  width: 100%;
}

.review-panel__rereview-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px dashed var(--app-border);
}

.project-action-bar {
  display: grid;
  gap: 12px;
}

.project-action-bar__header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.project-action-bar__header h4 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.project-action-bar__progress {
  font-size: 13px;
  color: var(--app-text-secondary);
}

.project-action-bar__actions {
  display: flex;
  gap: 12px;
}

.project-action-bar__hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--app-text-muted);
}

.project-action-bar__hint-icon {
  color: var(--app-warning, #f59e0b);
  flex-shrink: 0;
}
</style>
