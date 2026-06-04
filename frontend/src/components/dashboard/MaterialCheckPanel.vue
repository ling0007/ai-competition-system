<script setup>
import { computed, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { CheckCircle2, Eye, Upload } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import { formatDateTime, formatPercent } from '@/utils/format'
import { resolveStatusMeta } from '@/utils/status'

const props = defineProps({
  project: {
    type: Object,
    default: null,
  },
  aiResult: {
    type: Object,
    default: null,
  },
  uploadMaterialId: {
    type: Number,
    default: null,
  },
  checking: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['upload-material', 'run-check', 'view-file'])

const remarks = reactive({})

watch(
  () => props.project?.materials,
  (materials) => {
    materials?.forEach((item) => {
      remarks[item.requirementId] = item.remark ?? ''
    })
  },
  { immediate: true },
)

const materialStats = computed(() => {
  const materials = props.project?.materials ?? []
  const submittedTotal = materials.filter((item) => item.submitStatus === 'submitted').length
  const requiredTotal = materials.length
  const pendingTotal = requiredTotal - submittedTotal

  return {
    submittedTotal,
    requiredTotal,
    pendingTotal,
  }
})

function handleMaterialUpload(row, file) {
  emit('upload-material', {
    requirementId: row.requirementId,
    file: file.raw,
    remark: remarks[row.requirementId],
  })
}

function resolveTagType(status) {
  return resolveStatusMeta(status).tagType
}

function resolveAlertType(status) {
  const tagType = resolveTagType(status)
  return tagType === 'danger' ? 'error' : tagType
}

function resolveActionText(row) {
  return row.fileName ? '重新上传' : '上传材料'
}

function previewFile(row) {
  if (!row.fileId || !row.fileName) {
    ElMessage.warning('该材料尚未上传文件')
    return
  }
  emit('view-file', row.fileId, row.fileName)
}

function getReviewStatusMeta(status) {
  if (!status) return { label: '未审核', tagType: 'info' }
  return resolveStatusMeta(status)
}
</script>

<template>
  <FeaturePanel
    title="材料上传与智能核验"
    subtitle="根据通知要求维护申报材料清单，系统将同步检查提交状态、版本信息与材料完整性。"
  >
    <template #actions>
      <el-button
        type="primary"
        :icon="CheckCircle2"
        :loading="checking"
        :disabled="!project?.projectId"
        @click="$emit('run-check')"
      >
        运行核验
      </el-button>
    </template>

    <div class="material-panel">
      <el-table
        v-if="project?.materials?.length"
        :data="project.materials"
        stripe
        class="material-panel__table"
      >
        <el-table-column prop="requirementName" label="材料名称" min-width="150" />
        <el-table-column prop="description" label="要求说明" min-width="220" show-overflow-tooltip />
        <el-table-column label="提交状态" width="110">
          <template #default="{ row }">
            <StatusTag
              :status="row.submitStatus"
              :label="resolveStatusMeta(row.submitStatus).label"
              :tone="resolveTagType(row.submitStatus)"
            />
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="110">
          <template #default="{ row }">
            <StatusTag
              :status="row.reviewStatus"
              :label="getReviewStatusMeta(row.reviewStatus).label"
              :tone="getReviewStatusMeta(row.reviewStatus).tagType"
            />
          </template>
        </el-table-column>
        <el-table-column label="版本 / 文件" min-width="180">
          <template #default="{ row }">
            <div class="material-panel__file">
              <strong>V{{ row.versionNo }}</strong>
              <span>{{ row.fileName || '未上传文件' }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="备注" min-width="220">
          <template #default="{ row }">
            <el-input
              v-model="remarks[row.requirementId]"
              size="small"
              placeholder="补充上传说明"
            />
          </template>
        </el-table-column>
        <el-table-column label="审核意见" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.reviewComment" style="color: var(--app-text-secondary); font-size: 13px;">
              {{ row.reviewComment }}
            </span>
            <span v-else style="color: var(--app-text-muted); font-size: 12px; font-style: italic;">
              暂无审核意见
            </span>
          </template>
        </el-table-column>
        <el-table-column label="提交时间" width="170">
          <template #default="{ row }">
            {{ row.submittedAt ? formatDateTime(row.submittedAt) : '待上传' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <div class="material-panel__actions">
              <el-upload
                :auto-upload="false"
                :show-file-list="false"
                :on-change="(file) => handleMaterialUpload(row, file)"
              >
                <el-button
                  class="app-button--outline"
                  size="small"
                  :icon="Upload"
                  :loading="uploadMaterialId === row.requirementId"
                >
                  {{ resolveActionText(row) }}
                </el-button>
              </el-upload>

              <el-button
                v-if="row.fileName"
                class="app-button--secondary"
                size="small"
                :icon="Eye"
                @click="previewFile(row)"
              >
                查看文件
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <el-empty
        v-else
        description="项目创建后将自动生成材料清单，并在此逐项维护提交状态。"
      />

      <transition name="fade-slide" mode="out-in">
        <div v-if="aiResult" class="material-panel__result">
          <div class="material-panel__result-head">
            <div>
              <h3>AI 核验结果</h3>
              <p>{{ aiResult.projectName }}</p>
            </div>
            <StatusTag
              :status="aiResult.reviewResult"
              :label="resolveStatusMeta(aiResult.reviewResult).label"
              :tone="resolveTagType(aiResult.reviewResult)"
              strong
            />
          </div>

          <el-alert :type="resolveAlertType(aiResult.reviewResult)" :closable="false" show-icon>
            <template #title>
              {{ aiResult.reviewComment }}
            </template>
          </el-alert>

          <div class="material-panel__stats">
            <div class="material-panel__stats-item">
              <span>完成率</span>
              <strong>{{ formatPercent(aiResult.completionRate) }}</strong>
            </div>
            <div class="material-panel__stats-item">
              <span>已提交</span>
              <strong>{{ materialStats.submittedTotal }}/{{ materialStats.requiredTotal }}</strong>
            </div>
            <div class="material-panel__stats-item material-panel__stats-item--danger">
              <span>待补</span>
              <strong>{{ materialStats.pendingTotal }} 项</strong>
            </div>
          </div>

          <div class="material-panel__tags">
            <el-tag
              v-for="item in aiResult.missingMaterials"
              :key="item"
              type="danger"
              effect="plain"
            >
              {{ item }}
            </el-tag>
            <span v-if="!aiResult.missingMaterials.length">当前所有必交材料均已提交。</span>
          </div>
        </div>

        <el-empty
          v-else
          description="完成材料上传后，可运行核验查看材料完整性与待补项。"
        />
      </transition>
    </div>
  </FeaturePanel>
</template>

<style scoped lang="scss">
.material-panel {
  display: grid;
  gap: 24px;
}

.material-panel__table {
  width: 100%;
}

.material-panel__file {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.material-panel__file strong {
  flex: 0 0 auto;
  color: var(--app-text-primary);
  font-size: 13px;
}

.material-panel__file span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.material-panel__file span,
.material-panel__result-head p,
.material-panel__stats-item span {
  color: var(--app-text-muted);
}

.material-panel__actions {
  display: flex;
  gap: 8px;
  flex-wrap: nowrap;
}

.material-panel__result {
  display: grid;
  gap: 16px;
  padding: 24px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
}

.material-panel__result-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.material-panel__result-head h3 {
  margin: 0 0 6px;
  color: var(--app-text-primary);
  font-size: 20px;
  font-weight: 700;
}

.material-panel__result-head p {
  margin: 0;
  font-size: 13px;
}

.material-panel__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.material-panel__stats-item {
  display: grid;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
}

.material-panel__stats-item--danger {
  border-color: var(--app-border);
  background: var(--app-surface-soft);
}

.material-panel__stats-item strong {
  color: var(--app-text-primary);
  font-size: 16px;
  font-weight: 600;
}

.material-panel__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  color: var(--app-text-secondary);
}

@media (max-width: 960px) {
  .material-panel__stats {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .material-panel__result-head {
    flex-direction: column;
  }
}
</style>
