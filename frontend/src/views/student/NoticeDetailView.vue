<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, CalendarDays, FileText } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import { getNoticeDetail } from '@/api/competition'
import { formatDateTime } from '@/utils/format'

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const notice = ref(null)
const expired = computed(() => notice.value?.deadline && new Date(notice.value.deadline).getTime() < Date.now())

async function loadNotice() {
  loading.value = true
  try {
    const response = await getNoticeDetail(Number(route.params.noticeId))
    notice.value = response.data
    if (notice.value?.publishStatus !== 'PUBLISHED') {
      ElMessage.warning('该通知当前不可申报')
      router.replace('/student/notices')
    }
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '通知详情加载失败')
  } finally {
    loading.value = false
  }
}

function applyNow() {
  router.push({ path: '/student/projects/new', query: { noticeId: notice.value.noticeId } })
}

onMounted(loadNotice)
</script>

<template>
  <div v-loading="loading" class="student-page">
    <el-button text :icon="ArrowLeft" @click="router.push('/student/notices')">返回申报大厅</el-button>
    <FeaturePanel v-if="notice" :title="notice.title" :subtitle="notice.organizer || '主办方待补充'">
      <template #actions>
        <el-button type="primary" :disabled="expired" @click="applyNow">
          {{ expired ? '已截止' : '立即申报' }}
        </el-button>
      </template>

      <div class="notice-detail__meta">
        <div><CalendarDays :size="18" /><span>截止时间</span><strong>{{ formatDateTime(notice.deadline) }}</strong></div>
        <div><FileText :size="18" /><span>适用对象</span><strong>{{ notice.targetGroup || '以通知正文为准' }}</strong></div>
      </div>

      <el-alert
        v-if="expired"
        type="warning"
        title="该通知已超过申报截止时间，不能再创建项目。"
        :closable="false"
        show-icon
      />

      <section class="notice-detail__section">
        <h3>通知说明</h3>
        <p>{{ notice.aiSummary || notice.rawText || '暂无说明' }}</p>
      </section>

      <section class="notice-detail__section">
        <h3>材料要求</h3>
        <div class="notice-detail__requirements">
          <article v-for="item in notice.materialRequirements" :key="item.requirementId">
            <div><strong>{{ item.name }}</strong><el-tag :type="item.required ? 'danger' : 'info'">{{ item.required ? '必交' : '选交' }}</el-tag></div>
            <p>{{ item.description || '暂无补充说明' }}</p>
          </article>
        </div>
      </section>
    </FeaturePanel>
  </div>
</template>

<style scoped lang="scss">
.student-page { display: grid; gap: 16px; }
.student-page > .el-button { justify-self: start; }
.notice-detail__meta { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; margin-bottom: 20px; }
.notice-detail__meta div { display: grid; grid-template-columns: auto 1fr; gap: 6px 10px; padding: 18px; border: 1px solid var(--app-border); border-radius: var(--app-radius-sm); }
.notice-detail__meta svg { grid-row: 1 / 3; color: var(--app-primary); }
.notice-detail__meta span { color: var(--app-text-muted); font-size: 13px; }
.notice-detail__section { margin-top: 24px; }
.notice-detail__section h3 { margin: 0 0 12px; }
.notice-detail__section > p { color: var(--app-text-secondary); line-height: 1.8; white-space: pre-wrap; }
.notice-detail__requirements { display: grid; gap: 12px; }
.notice-detail__requirements article { padding: 16px; border: 1px solid var(--app-border); border-radius: var(--app-radius-sm); }
.notice-detail__requirements article div { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.notice-detail__requirements p { margin: 8px 0 0; color: var(--app-text-tertiary); }
@media (max-width: 760px) { .notice-detail__meta { grid-template-columns: 1fr; } }
</style>
