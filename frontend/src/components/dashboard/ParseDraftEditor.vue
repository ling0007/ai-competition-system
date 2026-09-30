<template>
  <div class="parse-draft-editor">
    <el-card shadow="never" header="AI解析草稿确认">
      <!-- 加载中 -->
      <el-skeleton v-if="loading" :rows="8" animated />

      <!-- 草稿不存在 -->
      <el-empty v-else-if="!draft" description="该通知没有待确认的解析草稿">
        <el-button type="primary" @click="$emit('close')">返回</el-button>
      </el-empty>

      <!-- 草稿编辑区 -->
      <template v-else>
        <el-descriptions :column="2" border style="margin-bottom: 20px">
          <el-descriptions-item label="草稿ID">{{ draft.id }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType" size="small">{{ statusLabel }}</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" label-position="left">
          <el-form-item label="通知标题" prop="aiTitle">
            <el-input v-model="form.aiTitle" placeholder="请输入通知标题" />
          </el-form-item>

          <el-form-item label="主办方">
            <el-input v-model="form.aiOrganizer" placeholder="请输入主办方名称" />
          </el-form-item>

          <el-form-item label="截止日期" prop="aiDeadline">
            <el-date-picker
              v-model="form.aiDeadline"
              type="datetime"
              placeholder="选择截止日期"
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              style="width: 100%"
            />
          </el-form-item>

          <el-form-item label="面向对象">
            <el-input v-model="form.aiTargetGroup" placeholder="例如：全日制本科生团队" />
          </el-form-item>

          <el-form-item label="关键内容">
            <el-input
              v-model="form.aiKeyPoints"
              type="textarea"
              :rows="3"
              placeholder="AI 提取的关键内容摘要"
            />
          </el-form-item>
        </el-form>

        <!-- 材料列表 -->
        <el-card shadow="never" header="材料要求清单" style="margin-top: 20px">
          <template #header>
            <div style="display: flex; align-items: center; justify-content: space-between">
              <span>材料要求清单（{{ form.materials.length }} 项）</span>
              <el-button type="primary" size="small" @click="addMaterial">
                + 添加材料
              </el-button>
            </div>
          </template>

          <el-table :data="form.materials" border stripe>
            <el-table-column prop="name" label="材料名称" min-width="160">
              <template #default="{ row }">
                <el-input v-model="row.name" size="small" placeholder="材料名称" />
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="200">
              <template #default="{ row }">
                <el-input v-model="row.description" size="small" placeholder="材料说明" />
              </template>
            </el-table-column>
            <el-table-column prop="isRequired" label="必交" width="70" align="center">
              <template #default="{ row }">
                <el-switch v-model="row.isRequired" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ $index }">
                <el-button
                  type="danger"
                  size="small"
                  text
                  @click="removeMaterial($index)"
                  :disabled="form.materials.length <= 1"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <!-- 原始 AI 响应（可折叠） -->
        <el-collapse style="margin-top: 20px">
          <el-collapse-item title="查看 AI 原始返回 JSON">
            <el-input
              :model-value="draft.rawAiResponse || '无'"
              type="textarea"
              :rows="8"
              readonly
            />
          </el-collapse-item>
        </el-collapse>

        <!-- 操作按钮 -->
        <div class="draft-actions">
          <el-button @click="$emit('close')">取消</el-button>
          <el-button
            type="primary"
            :loading="saving"
            @click="handleSave"
          >
            保存修改
          </el-button>
          <el-button
            type="success"
            :loading="confirming"
            @click="handleConfirm"
          >
            确认并写入正式数据
          </el-button>
        </div>
      </template>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getParseDraft, updateParseDraft, confirmParse } from '@/api/competition'

const props = defineProps({
  noticeId: { type: [Number, String], required: true },
})

const emit = defineEmits(['close', 'confirmed'])

const draft = ref(null)
const formRef = ref(null)
const loading = ref(false)
const saving = ref(false)
const confirming = ref(false)

const form = ref({
  aiTitle: '',
  aiOrganizer: '',
  aiDeadline: '',
  aiTargetGroup: '',
  aiKeyPoints: '',
  materials: [],
})

const rules = {
  aiTitle: [{ required: true, message: '请输入通知标题', trigger: 'blur' }],
  aiDeadline: [{ required: true, message: '请选择截止日期', trigger: 'change' }],
}

const statusType = computed(() => {
  if (!draft.value) return 'info'
  return draft.value.status === 'PENDING' ? 'warning' : 'success'
})

const statusLabel = computed(() => {
  if (!draft.value) return ''
  return draft.value.status === 'PENDING' ? '待确认' : '已确认'
})

function loadDraft() {
  loading.value = true
  getParseDraft(props.noticeId)
    .then((res) => {
      if (res.data) {
        draft.value = res.data
        form.value = {
          aiTitle: res.data.aiTitle || '',
          aiOrganizer: res.data.aiOrganizer || '',
          aiDeadline: res.data.aiDeadline || '',
          aiTargetGroup: res.data.aiTargetGroup || '',
          aiKeyPoints: res.data.aiKeyPoints || '',
          materials: (res.data.materials || []).map((m) => ({ ...m })),
        }
      } else {
        draft.value = null
      }
    })
    .catch((err) => {
      ElMessage.error(err.message || '获取草稿失败')
    })
    .finally(() => {
      loading.value = false
    })
}

function buildPayload() {
  return {
    aiTitle: form.value.aiTitle?.trim(),
    aiOrganizer: form.value.aiOrganizer?.trim(),
    aiDeadline: form.value.aiDeadline,
    aiTargetGroup: form.value.aiTargetGroup?.trim(),
    aiKeyPoints: form.value.aiKeyPoints?.trim(),
    materials: form.value.materials.map((material) => ({
      name: material.name?.trim(),
      description: material.description?.trim(),
      isRequired: material.isRequired,
    })),
  }
}

async function validateDraft() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return false
  if (!form.value.materials.length) {
    ElMessage.warning('至少保留一项材料要求')
    return false
  }
  if (form.value.materials.some((material) => !material.name?.trim())) {
    ElMessage.warning('请填写每一项材料名称')
    return false
  }
  return true
}

async function handleSave() {
  if (!await validateDraft()) return
  saving.value = true
  try {
    const response = await updateParseDraft(props.noticeId, buildPayload())
    draft.value = response.data
    ElMessage.success('草稿已保存')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleConfirm() {
  if (!await validateDraft()) return
  try {
    await ElMessageBox.confirm(
      '确认前会先保存当前修改。确认后，AI 草稿将写入正式通知和材料清单，但仍需单独发布后学生才可申报。',
      '确认解析结果',
      { confirmButtonText: '保存并确认', cancelButtonText: '继续编辑', type: 'warning' },
    )
    confirming.value = true
    await updateParseDraft(props.noticeId, buildPayload())
    await confirmParse(props.noticeId)
    ElMessage.success('解析结果已确认，正式通知数据已更新')
    emit('confirmed', props.noticeId)
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error?.response?.data?.message || error?.message || '确认失败')
    }
  } finally {
    confirming.value = false
  }
}

function addMaterial() {
  form.value.materials.push({ name: '', description: '', isRequired: false })
}

function removeMaterial(index) {
  form.value.materials.splice(index, 1)
}

onMounted(loadDraft)
</script>

<style scoped lang="scss">
.parse-draft-editor {
  max-width: 960px;
  margin: 0 auto;
}

.draft-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-light);
}
</style>
