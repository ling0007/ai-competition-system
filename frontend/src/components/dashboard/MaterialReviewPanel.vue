<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Check, Eye, FileEdit, RefreshCw } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import FileContentViewer from '@/components/shared/FileContentViewer.vue'
import { formatDateTime } from '@/utils/format'
import { resolveStatusMeta } from '@/utils/status'
import { getProjectDetail, reviewMaterial, resetMaterialReview } from '@/api/competition'

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

// File viewer state
const fileViewerVisible = ref(false)
const viewerFileId = ref(null)
const viewerFileName = ref('')

const materials = computed(() => props.project?.materials ?? [])

const submittedMaterials = computed(() =>
  materials.value.filter((m) => m.submitStatus === 'submitted'),
)

const stats = computed(() => {
  const total = materials.value.length
  const submitted = submittedMaterials.value.length
  const approved = materials.value.filter((m) => m.reviewStatus === 'approved').length
  const revision = materials.value.filter((m) => m.reviewStatus === 'revision').length
  const pendingReview = submitted - approved - revision
  const unreviewed = total - approved - revision
  return { total, submitted, approved, revision, pendingReview, unreviewed }
})

function getSubmitStatusMeta(status) {
  return resolveStatusMeta(status)
}

function getReviewStatusMeta(status) {
  if (!status) return { label: '未审核', tagType: 'info' }
  return resolveStatusMeta(status)
}

function openFileViewer(fileId, fileName) {
  viewerFileId.value = fileId
  viewerFileName.value = fileName || ''
  fileViewerVisible.value = true
}

async function handleApprove(material) {
  if (!props.project?.projectId || !props.currentUser?.userId) return
  reviewing.value = true
  try {
    await reviewMaterial({
      projectId: props.project.projectId,
      materialId: material.materialId,
      reviewStatus: 'approved',
      reviewComment: '材料审核通过，内容完整符合要求。',
      reviewerId: props.currentUser.userId,
    })
    ElMessage.success(`材料「${material.requirementName}」审核通过`)
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

async function submitRevision() {
  if (!pendingReviewMaterial.value || !props.project?.projectId || !props.currentUser?.userId) return
  reviewing.value = true
  try {
    await reviewMaterial({
      projectId: props.project.projectId,
      materialId: pendingReviewMaterial.value.materialId,
      reviewStatus: 'revision',
      reviewComment: reviewCommentInput.value || '请修改后重新提交。',
      reviewerId: props.currentUser.userId,
    })
    ElMessage.success(`已向学生发送修改意见：${pendingReviewMaterial.value.requirementName}`)
    showCommentDialog.value = false
    pendingReviewMaterial.value = null
    reviewCommentInput.value = ''
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

async function handleResetReview(material) {
  reviewing.value = true
  try {
    await resetMaterialReview(material.materialId)
    ElMessage.success(`材料「${material.requirementName}」审核状态已重置，恢复为未审核`)
    await refreshState()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error.message || '重置审核失败')
  } finally {
    reviewing.value = false
  }
}

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
        v-if="submittedMaterials.length"
        :data="submittedMaterials"
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
          <template #default="{ row }">V{{ row.versionNo }}</template>
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
              :label="getSubmitStatusMeta(row.submitStatus).label"
              :tone="getSubmitStatusMeta(row.submitStatus).tagType"
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

        <el-table-column label="操作" width="280" fixed="right">
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

              <template v-if="row.reviewStatus !== 'approved'">
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

              <template v-else>
                <el-button
                  size="small"
                  :icon="RefreshCw"
                  :loading="reviewing && pendingReviewMaterial?.materialId === row.materialId"
                  @click="handleResetReview(row)"
                >
                  重新审核
                </el-button>
              </template>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-else
        description="暂无学生提交的材料需要审核。"
      />
    </div>

    <!-- 修改意见对话框 -->
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
  flex-wrap: nowrap;
}

.review-panel__table {
  width: 100%;
}
</style>
