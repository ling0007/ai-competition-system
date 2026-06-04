<script setup>
import { computed } from 'vue'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import StatCard from '@/components/shared/StatCard.vue'
import { formatDateTime, formatPercent } from '@/utils/format'
import { resolveStatusMeta } from '@/utils/status'
import { BarChart3, ClipboardCheck, TriangleAlert } from 'lucide-vue-next'

const props = defineProps({
  project: {
    type: Object,
    default: null,
  },
  progress: {
    type: Object,
    default: null,
  },
  submittedMaterials: {
    type: Array,
    default: () => [],
  },
})

const progressStatus = computed(() => resolveStatusMeta(props.progress?.status || props.project?.status))

const cards = computed(() => [
  {
    title: '完成率',
    value: props.progress ? formatPercent(props.progress.completionRate) : '0%',
    description: props.progress ? `截止 ${formatDateTime(props.progress.deadline)}` : '等待项目创建',
    icon: BarChart3,
    accent: 'blue',
  },
  {
    title: '已提交材料',
    value: String(props.progress?.submittedTotal ?? 0),
    description: props.project?.projectName ?? '暂无项目',
    icon: ClipboardCheck,
    accent: 'green',
  },
  {
    title: '缺失材料',
    value: String(props.progress?.missingTotal ?? 0),
    description: progressStatus.value.label,
    icon: TriangleAlert,
    accent: props.progress?.missingTotal ? 'amber' : 'slate',
  },
])
</script>

<template>
  <FeaturePanel
    title="项目进度总览"
    subtitle="以看板方式聚合项目完成率、已提交材料、缺失项和当前状态，适合课堂演示与后续扩展。"
  >
    <template #actions>
      <el-tag :type="progressStatus.tagType" effect="dark">{{ progressStatus.label }}</el-tag>
    </template>

    <div class="progress-panel">
      <div class="progress-panel__stats">
        <StatCard
          v-for="item in cards"
          :key="item.title"
          :title="item.title"
          :value="item.value"
          :description="item.description"
          :icon="item.icon"
          :accent="item.accent"
        />
      </div>

      <transition name="fade-slide" mode="out-in">
        <div v-if="progress" class="progress-panel__body">
          <div class="progress-panel__bar">
            <div class="progress-panel__bar-head">
              <div>
                <span>材料完成进度</span>
                <strong>{{ formatPercent(progress.completionRate) }}</strong>
              </div>
              <small>{{ progress.submittedTotal }}/{{ progress.requiredTotal }} 项必交材料已完成</small>
            </div>
            <el-progress
              :percentage="Number(progress.completionRate)"
              :stroke-width="12"
              :show-text="false"
              status="success"
            />
          </div>

          <div class="progress-panel__lists">
            <div class="progress-panel__list-card">
              <h3>已提交材料</h3>
              <div v-if="submittedMaterials.length" class="progress-panel__list">
                <div
                  v-for="item in submittedMaterials"
                  :key="item.requirementId"
                  class="progress-panel__list-item"
                >
                  <div>
                    <strong>{{ item.requirementName }}</strong>
                    <span>{{ item.fileName }}</span>
                  </div>
                  <small>{{ formatDateTime(item.submittedAt) }}</small>
                </div>
              </div>
              <el-empty v-else description="暂未提交材料" />
            </div>

            <div class="progress-panel__list-card">
              <h3>缺失材料</h3>
              <div v-if="progress.missingMaterials.length" class="progress-panel__missing">
                <el-tag
                  v-for="item in progress.missingMaterials"
                  :key="item"
                  type="warning"
                  effect="plain"
                >
                  {{ item }}
                </el-tag>
              </div>
              <el-empty v-else description="当前没有缺失材料" />
            </div>
          </div>
        </div>

        <el-empty
          v-else
          description="项目创建完成后，这里会显示完成率、已提交材料、缺失材料和当前状态。"
        />
      </transition>
    </div>
  </FeaturePanel>
</template>

<style scoped lang="scss">
.progress-panel {
  display: grid;
  gap: 24px;
}

.progress-panel__stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}

.progress-panel__body {
  display: grid;
  gap: 18px;
}

.progress-panel__bar,
.progress-panel__list-card {
  padding: 24px;
  border-radius: var(--app-radius-sm);
  border: 1px solid var(--app-border);
  background: #ffffff;
  box-shadow: var(--app-shadow-sm);
}

.progress-panel__bar-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.progress-panel__bar-head span,
.progress-panel__bar-head small,
.progress-panel__list-item span,
.progress-panel__list-item small {
  color: var(--app-text-muted);
}

.progress-panel__bar-head strong {
  display: block;
  margin-top: 8px;
  color: var(--app-text-primary);
  font-size: 24px;
}

.progress-panel__lists {
  display: grid;
  gap: 16px;
}

.progress-panel__list-card h3 {
  margin: 0 0 14px;
  color: var(--app-text-primary);
  font-size: 16px;
  font-weight: 700;
}

.progress-panel__list {
  display: grid;
  gap: 12px;
}

.progress-panel__list-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 16px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
}

.progress-panel__list-item div {
  display: grid;
  gap: 4px;
}

.progress-panel__list-item strong {
  color: var(--app-text-primary);
}

.progress-panel__missing {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

@media (max-width: 1120px) {
  .progress-panel__stats {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 860px) {
  .progress-panel__bar-head,
  .progress-panel__list-item {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
