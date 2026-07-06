<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ScrollText } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import { getAgentTaskLogs } from '@/api/competition'
import { formatDateTime } from '@/utils/format'

const props = defineProps({
  currentUser: {
    type: Object,
    default: null,
  },
})

const loading = ref(false)
const logs = ref([])
const toolFilter = ref('')

const toolOptions = [
  { value: '', label: '全部工具' },
  { value: 'parseNoticeTool', label: '通知解析' },
  { value: 'checkMaterialTool', label: '材料检查' },
]

const toolNameMap = {
  parseNoticeTool: '通知解析',
  checkMaterialTool: '材料检查',
}

const statusMetaMap = {
  success: { label: '成功', tagType: 'success' },
  failed: { label: '失败', tagType: 'danger' },
}

const expandedRows = ref([])

async function loadLogs() {
  loading.value = true
  try {
    const params = {}
    if (toolFilter.value) {
      params.toolName = toolFilter.value
    }
    const response = await getAgentTaskLogs(params)
    logs.value = response.data ?? []
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '获取审计日志失败')
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  loadLogs()
}

function handleRowExpand(row) {
  // Single-row expand behavior
  expandedRows.value = [row.taskId]
}

onMounted(() => {
  loadLogs()
})

defineExpose({ refresh: loadLogs })
</script>

<template>
  <FeaturePanel
    title="智能体审计日志"
    subtitle="记录每次 AI 工具调用的输入摘要与执行结果"
  >
    <template #actions>
      <el-select
        v-model="toolFilter"
        placeholder="筛选工具"
        size="small"
        style="width: 140px"
        clearable
        @change="handleFilterChange"
      >
        <el-option
          v-for="opt in toolOptions"
          :key="opt.value"
          :label="opt.label"
          :value="opt.value"
        />
      </el-select>
    </template>

    <el-table
      v-loading="loading"
      :data="logs"
      row-key="taskId"
      :expand-row-keys="expandedRows"
      stripe
      size="default"
      empty-text="暂无审计日志记录"
      @expand-change="handleRowExpand"
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="log-expand">
            <div class="log-expand__section">
              <strong>完整执行结果：</strong>
              <p>{{ row.resultSummary || '无' }}</p>
            </div>
            <div class="log-expand__section">
              <strong>输入摘要：</strong>
              <p>{{ row.inputSummary || '无' }}</p>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="taskId" label="任务ID" width="90" align="center" />

      <el-table-column prop="projectId" label="项目ID" width="90" align="center">
        <template #default="{ row }">
          <span v-if="row.projectId">{{ row.projectId }}</span>
          <span v-else class="log-table__muted">-</span>
        </template>
      </el-table-column>

      <el-table-column prop="toolName" label="工具名称" width="130">
        <template #default="{ row }">
          <el-tag size="small" type="info" effect="plain">
            {{ toolNameMap[row.toolName] || row.toolName }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="inputSummary" label="输入摘要" min-width="240" show-overflow-tooltip />

      <el-table-column prop="executeStatus" label="执行状态" width="100" align="center">
        <template #default="{ row }">
          <StatusTag
            :status="row.executeStatus"
            :label="statusMetaMap[row.executeStatus]?.label || row.executeStatus"
            :tone="statusMetaMap[row.executeStatus]?.tagType || 'default'"
            strong
          />
        </template>
      </el-table-column>

      <el-table-column prop="createdAt" label="执行时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createdAt) }}
        </template>
      </el-table-column>
    </el-table>
  </FeaturePanel>
</template>

<style scoped lang="scss">
.log-expand {
  display: grid;
  gap: 16px;
  padding: 16px 24px;
  background: var(--app-surface-soft, #f9fafb);
}

.log-expand__section {
  display: grid;
  gap: 6px;
}

.log-expand__section strong {
  color: var(--app-text-primary, #374151);
  font-size: 16px;
  font-weight: 600;
}

.log-expand__section p {
  margin: 0;
  color: var(--app-text-secondary, #6b7280);
  font-size: 16px;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

.log-table__muted {
  color: var(--app-text-muted, #9ca3af);
}
</style>
