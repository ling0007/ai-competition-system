<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Bot, Eye, Send, Archive, Upload } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'
import ParseDraftEditor from '@/components/dashboard/ParseDraftEditor.vue'
import FileContentViewer from '@/components/shared/FileContentViewer.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import {
  archiveNotice,
  getNoticeDetail,
  parseNotice,
  publishNotice,
  replaceNoticeAttachment,
} from '@/api/competition.js'
import { formatDateTime } from '@/utils/format.js'
import { resolveNoticeParseStatus, resolveNoticePublishStatus } from '@/utils/status.js'
import { useNoticeParseTask } from '@/composables/useNoticeParseTask.js'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const parsing = ref(false)
const publishing = ref(false)
const archiving = ref(false)
const replacingAttachment = ref(false)
const detail = ref(null)
const draftVisible = ref(false)
const fileVisible = ref(false)
const { task: parseTask, watchTask, resume: resumeTask, stop: stopTask } = useNoticeParseTask({
  onSuccess: async (task) => {
    await loadDetail()
    draftVisible.value = true
    if (task.resultOrigin === 'FALLBACK') ElMessage.warning('AI 服务不可用，已生成降级草稿，请逐项人工核对')
    else ElMessage.success('AI 解析完成，请核对草稿')
  },
  onFailure: async (task) => {
    await loadDetail()
    ElMessage.error(task.errorSummary || (task.status === 'TIMEOUT' ? '解析任务超时，请重新发起' : '解析任务失败'))
  },
})

const noticeId = computed(() => Number(route.params.noticeId))
const parseMeta = computed(() => resolveNoticeParseStatus(detail.value?.parseStatus))
const publishMeta = computed(() => resolveNoticePublishStatus(detail.value?.publishStatus))
const isConfirmed = computed(() => Boolean(detail.value?.confirmedAt))
const canParse = computed(() =>
  detail.value?.publishStatus === 'DRAFT'
    && ['DRAFT', 'FAILED', 'PARSED'].includes(detail.value?.parseStatus)
    && !isConfirmed.value,
)
const canEditDraft = computed(() => detail.value?.parseStatus === 'PARSED' && !isConfirmed.value)
const canPublish = computed(() =>
  detail.value?.publishStatus === 'DRAFT'
    && detail.value?.parseStatus === 'PARSED'
    && isConfirmed.value
    && detail.value?.materialRequirements?.length > 0,
)
const canArchive = computed(() => detail.value?.publishStatus === 'PUBLISHED')
const canReplaceAttachment = computed(() =>
  detail.value?.publishStatus === 'DRAFT'
    && !isConfirmed.value
    && ['DRAFT', 'FAILED'].includes(detail.value?.parseStatus),
)
const lifecycleStep = computed(() => {
  if (detail.value?.publishStatus === 'ARCHIVED') return 4
  if (detail.value?.publishStatus === 'PUBLISHED') return 3
  if (isConfirmed.value) return 2
  if (detail.value?.parseStatus === 'PARSED') return 1
  return 0
})
const nextAction = computed(() => {
  if (!detail.value) return ''
  if (detail.value.publishStatus === 'ARCHIVED') return '该通知已归档，生命周期已结束。'
  if (detail.value.publishStatus === 'PUBLISHED') return '通知已发布，学生可以据此创建申报项目；停止新申报时可归档。'
  if (detail.value.parseStatus === 'FAILED') return 'AI 解析失败，请检查通知文本或附件后重新解析。'
  if (detail.value.parseStatus === 'DRAFT') return '下一步：运行 AI 解析，生成待管理员核对的结构化草稿。'
  if (detail.value.parseStatus === 'PARSING') return 'AI 正在解析通知，请稍后刷新页面查看结果。'
  if (!isConfirmed.value) return '下一步：核对并编辑 AI 草稿，确认后才会写入正式通知数据。'
  if (!detail.value.materialRequirements?.length) return '正式材料清单为空，暂时不能发布。'
  return '解析结果已确认，可以发布通知。发布后学生才能发起申报。'
})

async function loadDetail() {
  loading.value = true
  try {
    const response = await getNoticeDetail(noticeId.value)
    detail.value = response.data
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '获取通知详情失败')
  } finally {
    loading.value = false
  }
}

async function handleParse() {
  parsing.value = true
  try {
    const response = await parseNotice(noticeId.value)
    watchTask(response.data)
    ElMessage.info('解析任务已提交，正在排队')
    await loadDetail()
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || 'AI 解析失败')
    await loadDetail()
  } finally {
    parsing.value = false
  }
}

async function handleReplaceAttachment(uploadFile) {
  const file = uploadFile.raw
  if (!file?.size) {
    ElMessage.error('请选择非空通知附件')
    return
  }
  try {
    await ElMessageBox.confirm('替换附件后需要重新进行 AI 解析。',
      '确认替换附件', { confirmButtonText: '替换附件', cancelButtonText: '取消', type: 'warning' })
    replacingAttachment.value = true
    await replaceNoticeAttachment(noticeId.value, file)
    ElMessage.success('附件已替换，请重新进行 AI 解析')
    await loadDetail()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || error?.message || '替换附件失败')
    }
  } finally {
    replacingAttachment.value = false
  }
}

async function handleConfirmed() {
  draftVisible.value = false
  await loadDetail()
}

async function handlePublish() {
  try {
    await ElMessageBox.confirm(
      '发布后学生将看到该通知并可创建申报项目，请确认正式字段和材料清单均已核对。',
      '发布通知',
      { confirmButtonText: '确认发布', cancelButtonText: '取消', type: 'warning' },
    )
    publishing.value = true
    await publishNotice(noticeId.value)
    ElMessage.success('通知已发布')
    await loadDetail()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || error?.message || '发布失败')
    }
  } finally {
    publishing.value = false
  }
}

async function handleArchive() {
  try {
    await ElMessageBox.confirm(
      '归档后将停止新的项目申报，已有项目不会被删除。此操作不能在当前流程中撤销。',
      '归档通知',
      { confirmButtonText: '确认归档', cancelButtonText: '取消', type: 'warning' },
    )
    archiving.value = true
    await archiveNotice(noticeId.value)
    ElMessage.success('通知已归档')
    await loadDetail()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || error?.message || '归档失败')
    }
  } finally {
    archiving.value = false
  }
}

onMounted(async () => {
  await loadDetail()
  try { await resumeTask(noticeId.value) } catch { /* detail remains usable */ }
})
watch(noticeId, async (id) => {
  stopTask()
  parseTask.value = null
  await loadDetail()
  try { await resumeTask(id) } catch { /* detail remains usable */ }
})
</script>

<template>
  <div v-loading="loading" class="notice-detail-view">
    <div class="notice-detail-view__toolbar">
      <el-button :icon="ArrowLeft" @click="router.push('/admin/notices')">返回通知列表</el-button>
      <div v-if="detail" class="notice-detail-view__actions">
        <el-upload v-if="canReplaceAttachment" :auto-upload="false" :show-file-list="false"
          accept=".pdf,.doc,.docx,.txt" :on-change="handleReplaceAttachment">
          <el-button :icon="Upload" :loading="replacingAttachment">{{ detail.noticeFileId ? '重传附件' : '上传附件' }}</el-button>
        </el-upload>
        <el-button
          v-if="canParse"
          :icon="Bot"
          :loading="parsing"
          @click="handleParse"
        >
          {{ detail.parseStatus === 'DRAFT' ? '开始 AI 解析' : '重新解析' }}
        </el-button>
        <el-button v-if="canEditDraft" type="primary" :icon="Eye" @click="draftVisible = true">
          核对解析草稿
        </el-button>
        <el-button v-if="canPublish" type="success" :icon="Send" :loading="publishing" @click="handlePublish">
          发布通知
        </el-button>
        <el-button v-if="canArchive" type="warning" :icon="Archive" :loading="archiving" @click="handleArchive">
          归档通知
        </el-button>
      </div>
    </div>

    <template v-if="detail">
      <section class="notice-detail-view__hero">
        <div>
          <p>通知 #{{ detail.noticeId }}</p>
          <h1>{{ detail.title || '未命名通知' }}</h1>
          <span>{{ detail.organizer || '主办方待补充' }}</span>
        </div>
        <div class="notice-detail-view__status">
          <StatusTag :status="detail.parseStatus" :label="parseMeta.label" :tone="parseMeta.tagType" strong />
          <StatusTag :status="detail.publishStatus" :label="publishMeta.label" :tone="publishMeta.tagType" strong />
        </div>
      </section>

      <section class="notice-detail-view__card">
        <el-steps :active="lifecycleStep" finish-status="success" align-center>
          <el-step title="已上传" description="正式通知草稿" />
          <el-step title="AI 已解析" description="等待人工核对" />
          <el-step title="人工已确认" description="写入正式数据" />
          <el-step title="已发布" description="学生可申报" />
          <el-step title="已归档" description="停止新申报" />
        </el-steps>
        <el-alert :title="nextAction" type="info" :closable="false" show-icon />
        <el-alert v-if="parseTask && ['PENDING', 'RUNNING'].includes(parseTask.status)"
          :title="parseTask.status === 'PENDING' ? 'AI 解析排队中' : 'AI 正在解析通知'"
          type="info" :closable="false" show-icon class="mt-3" />
        <el-alert v-if="parseTask?.status === 'SUCCESS' && parseTask.resultOrigin === 'FALLBACK'"
          title="AI 服务不可用：这是降级草稿，确认前请逐项对照原通知核对"
          type="warning" :closable="false" show-icon class="mt-3" />
        <el-alert v-if="['FAILED', 'TIMEOUT'].includes(parseTask?.status)"
          :title="parseTask.errorSummary || (parseTask.status === 'TIMEOUT' ? '解析任务超时' : '解析任务失败')"
          type="error" :closable="false" show-icon class="mt-3" />
      </section>

      <section class="notice-detail-view__grid">
        <div class="notice-detail-view__card">
          <h2>通知信息</h2>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="截止时间">{{ formatDateTime(detail.deadline) }}</el-descriptions-item>
            <el-descriptions-item label="适用对象">{{ detail.targetGroup || '待补充' }}</el-descriptions-item>
            <el-descriptions-item label="附件">
              <el-button
                v-if="detail.noticeFileId"
                link
                type="primary"
                @click="fileVisible = true"
              >
                {{ detail.fileName || '查看通知附件' }}
              </el-button>
              <span v-else>未上传附件</span>
            </el-descriptions-item>
            <el-descriptions-item label="确认时间">{{ formatDateTime(detail.confirmedAt) }}</el-descriptions-item>
            <el-descriptions-item label="发布时间">{{ formatDateTime(detail.publishedAt) }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="notice-detail-view__card">
          <h2>AI 摘要与原文</h2>
          <h3>AI 摘要</h3>
          <p>{{ detail.aiSummary || '尚未生成 AI 摘要' }}</p>
          <h3>通知原文 / 补充说明</h3>
          <p class="notice-detail-view__raw">{{ detail.rawText || '无文本内容' }}</p>
        </div>
      </section>

      <section class="notice-detail-view__card">
        <div class="notice-detail-view__section-head">
          <div>
            <h2>正式材料要求</h2>
            <p>只有管理员确认解析草稿后，这里的清单才成为业务事实。</p>
          </div>
          <strong>{{ detail.materialRequirements?.length ?? 0 }} 项</strong>
        </div>
        <el-table v-if="detail.materialRequirements?.length" :data="detail.materialRequirements">
          <el-table-column type="index" label="#" width="60" />
          <el-table-column prop="name" label="材料名称" min-width="180" />
          <el-table-column prop="description" label="说明" min-width="260" />
          <el-table-column label="要求" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.required ? 'danger' : 'info'">{{ row.required ? '必交' : '选交' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="尚无正式材料清单，请先完成解析草稿确认" />
      </section>
    </template>

    <el-drawer v-model="draftVisible" title="AI 解析草稿 · 人工确认" size="min(960px, 92vw)" destroy-on-close>
      <ParseDraftEditor
        v-if="draftVisible"
        :notice-id="noticeId"
        @close="draftVisible = false"
        @confirmed="handleConfirmed"
      />
    </el-drawer>

    <FileContentViewer
      :file-id="detail?.noticeFileId"
      :file-name="detail?.fileName"
      :visible="fileVisible"
      @close="fileVisible = false"
    />
  </div>
</template>

<style scoped lang="scss">
.notice-detail-view { display: grid; gap: 20px; min-height: 320px; }
.notice-detail-view__toolbar,
.notice-detail-view__hero,
.notice-detail-view__section-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.notice-detail-view__actions,
.notice-detail-view__status { display: flex; flex-wrap: wrap; gap: 10px; }
.notice-detail-view__hero,
.notice-detail-view__card { padding: 24px; border: 1px solid var(--app-border); border-radius: var(--app-radius-sm); background: #fff; box-shadow: var(--app-shadow-sm); }
.notice-detail-view__hero p { margin: 0 0 8px; color: var(--app-primary); font-weight: 700; }
.notice-detail-view__hero h1 { margin: 0 0 8px; font-size: 28px; }
.notice-detail-view__hero span,
.notice-detail-view__section-head p { color: var(--app-text-muted); }
.notice-detail-view__card { display: grid; gap: 20px; }
.notice-detail-view__card h2,
.notice-detail-view__card h3,
.notice-detail-view__card p { margin: 0; }
.notice-detail-view__card h2 { font-size: 20px; }
.notice-detail-view__card h3 { color: var(--app-text-secondary); font-size: 15px; }
.notice-detail-view__card p { color: var(--app-text-secondary); line-height: 1.7; }
.notice-detail-view__grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 20px; }
.notice-detail-view__raw { max-height: 220px; overflow: auto; white-space: pre-wrap; }
.notice-detail-view__section-head strong { color: var(--app-primary); font-size: 24px; }
@media (max-width: 900px) {
  .notice-detail-view__grid { grid-template-columns: 1fr; }
  .notice-detail-view__toolbar,
  .notice-detail-view__hero { align-items: flex-start; flex-direction: column; }
}
</style>
