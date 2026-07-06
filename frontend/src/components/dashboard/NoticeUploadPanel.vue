<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { Bot, Upload } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatusTag from '@/components/shared/StatusTag.vue'
import { formatDateTime } from '@/utils/format'

const props = defineProps({
  notice: {
    type: Object,
    default: null,
  },
  loadingUpload: {
    type: Boolean,
    default: false,
  },
  loadingParse: {
    type: Boolean,
    default: false,
  },
  bootstrapping: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['upload', 'parse', 'clear-notice'])

const form = reactive({
  title: '',
  organizer: '',
  deadline: null,
  targetGroup: '',
  rawText: '',
  file: null,
})

const fileList = ref([])

watch(
  () => props.notice,
  (notice) => {
    if (!notice) {
      return
    }

    form.title = notice.title ?? ''
    form.organizer = notice.organizer ?? ''
    form.deadline = notice.deadline ? new Date(notice.deadline) : null
    form.targetGroup = notice.targetGroup ?? ''
    form.rawText = notice.rawText ?? ''

    if (notice.fileName) {
      fileList.value = [{ name: notice.fileName, url: notice.fileName, status: 'success' }]
    }
  },
  { immediate: true },
)

const noticeStatus = computed(() => {
  if (props.loadingParse) {
    return {
      label: '解析中',
      tone: 'primary',
    }
  }

  if (props.notice?.materialRequirements?.length) {
    return {
      label: '解析完成',
      tone: 'success',
    }
  }

  if (props.notice?.noticeId) {
    return {
      label: '待解析',
      tone: 'info',
    }
  }

  return {
    label: '待上传',
    tone: 'info',
  }
})

function handleFileChange(file, uploadFiles) {
  fileList.value = uploadFiles.slice(-1)
  form.file = file.raw
}

function handleFileRemove() {
  fileList.value = []
  form.file = null
  emit('clear-notice')
}

function submitNotice() {
  emit('upload', {
    ...form,
    file: form.file,
    fileName: fileList.value[0]?.name ?? '',
  })
}

function triggerParse() {
  if (props.notice?.noticeId) {
    emit('parse', props.notice.noticeId)
  }
}
</script>

<template>
  <FeaturePanel
    title="竞赛通知上传与智能解析"
    subtitle="上传竞赛通知文件，系统将自动识别申报标题、主办单位、截止时间、适用对象及材料要求。"
  >
    <template #actions>
      <StatusTag :label="noticeStatus.label" :tone="noticeStatus.tone" />
    </template>

    <div class="notice-panel">
      <div class="notice-panel__form">
        <el-form label-position="top">
          <div class="notice-panel__grid">
            <el-form-item label="通知标题">
              <el-input v-model="form.title" placeholder="请输入竞赛通知标题" />
            </el-form-item>

            <el-form-item label="主办单位">
              <el-input v-model="form.organizer" placeholder="请输入主办单位名称" />
            </el-form-item>

            <el-form-item label="截止时间">
              <el-date-picker
                v-model="form.deadline"
                type="datetime"
                placeholder="请选择截止时间"
                format="YYYY-MM-DD HH:mm"
              />
            </el-form-item>

            <el-form-item label="适用对象">
              <el-input v-model="form.targetGroup" placeholder="请输入适用对象" />
            </el-form-item>
          </div>

          <el-form-item label="通知原文 / 补充说明">
            <el-input
              v-model="form.rawText"
              type="textarea"
              :rows="5"
              placeholder="可粘贴通知正文或补充说明，便于系统识别材料要求与关键信息。"
            />
          </el-form-item>

          <div class="notice-panel__actions">
            <el-upload
              :auto-upload="false"
              :file-list="fileList"
              :limit="1"
              :show-file-list="true"
              :on-change="handleFileChange"
              :on-remove="handleFileRemove"
            >
              <el-button class="app-button--outline" :icon="Upload">上传通知</el-button>
            </el-upload>

            <el-button
              class="app-button--secondary"
              :loading="loadingUpload"
              :disabled="!form.file && !form.rawText && !form.title"
              @click="submitNotice"
            >
              保存信息
            </el-button>

            <el-button
              type="primary"
              :icon="Bot"
              :disabled="!notice?.noticeId"
              :loading="loadingParse"
              @click="triggerParse"
            >
              智能解析
            </el-button>
          </div>
        </el-form>
      </div>

      <transition name="fade-slide" mode="out-in">
        <div v-if="notice && !bootstrapping" class="notice-panel__result">
          <div class="notice-panel__result-head">
            <h3>解析结果</h3>
            <span>{{ notice.fileName || '未上传附件' }}</span>
          </div>

          <el-descriptions :column="1" border>
            <el-descriptions-item label="标题">
              {{ notice.title || '未命名通知' }}
            </el-descriptions-item>
            <el-descriptions-item label="截止时间">
              {{ formatDateTime(notice.deadline) }}
            </el-descriptions-item>
            <el-descriptions-item label="适用对象">
              {{ notice.targetGroup || '待补充' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="notice-panel__summary">
            <span>AI 解析摘要</span>
            <p>{{ notice.aiSummary || '通知保存后可执行智能解析，自动生成摘要信息。' }}</p>
          </div>

          <div class="notice-panel__requirements">
            <span>材料要求</span>
            <div class="notice-panel__tags">
              <el-tag
                v-for="item in notice.materialRequirements"
                :key="item"
                effect="plain"
                type="primary"
              >
                {{ item }}
              </el-tag>
              <span v-if="!notice.materialRequirements?.length" class="notice-panel__placeholder">
                暂未识别材料清单
              </span>
            </div>
          </div>
        </div>

        <el-empty
          v-else
          description="上传竞赛通知后，这里会展示系统解析出的标题、截止时间、适用对象与材料要求。"
        />
      </transition>
    </div>
  </FeaturePanel>
</template>

<style scoped lang="scss">
.notice-panel {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 32px;
  align-items: start;
}

.notice-panel__grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
}

.notice-panel__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 8px;
}

.notice-panel__result {
  display: grid;
  gap: 18px;
  padding: 24px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
}

.notice-panel__result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.notice-panel__result-head h3,
.notice-panel__summary span,
.notice-panel__requirements span {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.notice-panel__result-head span {
  color: var(--app-text-muted);
  font-size: 16px;
}

.notice-panel__summary,
.notice-panel__requirements {
  display: grid;
  gap: 12px;
}

.notice-panel__summary p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 19px;
  line-height: 1.8;
}

.notice-panel__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.notice-panel__placeholder {
  color: var(--app-text-tertiary);
  font-size: 17px;
}

@media (max-width: 900px) {
  .notice-panel {
    grid-template-columns: 1fr;
    gap: 24px;
  }

  .notice-panel__result-head {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
