<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CheckCheck } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import { listNotifications, readNotification, readAllNotifications } from '@/api/notifications'
import { resolveApiErrorMessage } from '@/api/errors'
import { projectMessageTarget } from '@/utils/messageLinks'
import { formatDateTime } from '@/utils/format'

const props = defineProps({
  currentUser: {
    type: Object,
    default: null,
  },
})
const router = useRouter()

const emit = defineEmits(['messages-read'])

const loading = ref(false)
const markingAll = ref(false)
const messages = ref([])
const unreadOnly = ref(false)
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(5)
const total = ref(0)

const msgTypeConfig = {
  project: { label: '项目通知', tagType: 'success' },
  material: { label: '材料通知', tagType: 'warning' },
  deadline: { label: '截止提醒', tagType: 'danger' },
  system: { label: '系统消息', tagType: 'info' },
}

const hasUnread = computed(() => messages.value.some((m) => m.isRead === 0))

async function loadMessages() {
  loading.value = true
  try {
    const response = await listNotifications({
      isRead: unreadOnly.value ? 0 : undefined,
      keyword: searchKeyword.value || undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value,
    })
    messages.value = response.data?.records ?? []
    total.value = response.data?.total ?? 0
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '获取消息列表失败'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadMessages()
}

function handlePageChange(page) {
  currentPage.value = page
  loadMessages()
}

async function handleMarkRead(msgId) {
  try {
    await readNotification(msgId)
    const msg = messages.value.find((m) => m.msgId === msgId)
    if (msg) {
      msg.isRead = 1
    }
    emit('messages-read')
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '标记已读失败'))
  }
}

async function handleMarkAllRead() {
  markingAll.value = true
  try {
    await readAllNotifications()
    messages.value.forEach((m) => { m.isRead = 1 })
    emit('messages-read')
    ElMessage.success('全部消息已标记为已读')
  } catch (error) {
    ElMessage.error(resolveApiErrorMessage(error, '全部标记已读失败'))
  } finally {
    markingAll.value = false
  }
}

function openProject(msg) {
  const target = projectMessageTarget(props.currentUser?.role, msg.projectId)
  if (target) router.push(target)
}

onMounted(() => {
  loadMessages()
})

defineExpose({ refresh: loadMessages })
</script>

<template>
  <FeaturePanel
    title="消息中心"
    subtitle="查看系统通知、材料审核反馈与截止提醒"
  >
    <template #actions>
      <el-input
        v-model="searchKeyword"
        placeholder="搜索消息内容..."
        size="small"
        clearable
        style="width: 200px"
        @keyup.enter="handleSearch"
        @clear="handleSearch"
      />
      <el-button type="primary" size="small" @click="handleSearch">搜索</el-button>
      <el-radio-group
        v-model="unreadOnly"
        size="small"
        @change="handleSearch"
      >
        <el-radio-button :value="false">全部消息</el-radio-button>
        <el-radio-button :value="true">仅未读</el-radio-button>
      </el-radio-group>
      <el-button
        v-if="hasUnread"
        :loading="markingAll"
        size="small"
        type="primary"
        @click="handleMarkAllRead"
      >
        <CheckCheck :size="15" style="margin-right: 4px" />
        全部已读
      </el-button>
    </template>

    <div v-loading="loading" class="msg-list">
      <template v-if="messages.length">
        <div
          v-for="msg in messages"
          :key="msg.msgId"
          class="msg-item"
          :class="{ 'msg-item--unread': msg.isRead === 0 }"
        >
          <div class="msg-item__indicator">
            <span v-if="msg.isRead === 0" class="msg-item__dot" />
          </div>

          <div class="msg-item__body">
            <div class="msg-item__header">
              <el-tag
                size="small"
                :type="msgTypeConfig[msg.msgType]?.tagType || 'info'"
                effect="plain"
              >
                {{ msgTypeConfig[msg.msgType]?.label || msg.msgType }}
              </el-tag>
              <span class="msg-item__time">{{ formatDateTime(msg.createdAt) }}</span>
            </div>

            <p class="msg-item__content">{{ msg.msgContent }}</p>
          </div>

          <div class="msg-item__action">
            <el-button v-if="msg.projectId" class="msg-item__project-button" size="small" type="primary" @click="openProject(msg)">查看项目</el-button>
            <el-button
              v-if="msg.isRead === 0"
              class="msg-item__read-button"
              size="small"
              type="primary"
              @click.stop="handleMarkRead(msg.msgId)"
            >
              标为已读
            </el-button>
            <span v-else class="msg-item__read-label">已读</span>
          </div>
        </div>
      </template>

      <el-empty v-else description="暂无消息" />
    </div>

    <div v-if="total > pageSize" class="msg-pagination">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        background
        small
        @current-change="handlePageChange"
      />
    </div>
  </FeaturePanel>
</template>

<style scoped lang="scss">
.msg-list {
  display: flex;
  flex-direction: column;
}

.msg-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 18px 0;
  border-bottom: 1px solid var(--app-border, #e5e7eb);
  cursor: default;
  transition: background 0.15s;

  &:first-child {
    padding-top: 0;
  }

  &:last-child {
    border-bottom: 0;
    padding-bottom: 0;
  }
}

.msg-item--unread {
  cursor: pointer;

  &:hover {
    background: var(--app-surface-soft, #f9fafb);
    border-radius: 6px;
  }
}

.msg-item__indicator {
  flex: 0 0 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding-top: 6px;
}

.msg-item__dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--app-primary, #3b82f6);
}

.msg-item__body {
  flex: 1;
  min-width: 0;
}

.msg-item__header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 8px;
}

.msg-item__time {
  color: var(--app-text-muted, #9ca3af);
  font-size: 15px;
}

.msg-item__content {
  margin: 0;
  color: var(--app-text-secondary, #6b7280);
  font-size: 17px;
  line-height: 1.7;
  word-break: break-word;
}

.msg-item--unread .msg-item__content {
  color: var(--app-text-primary, #374151);
  font-weight: 500;
}

.msg-item__action {
  flex: 0 0 auto;
  display: grid;
  grid-template-columns: repeat(2, 104px);
  gap: 8px;
  align-items: center;
  padding-top: 4px;
}

.msg-item__project-button {
  grid-column: 1;
  width: 100%;
}

.msg-item__read-button,
.msg-item__read-label {
  grid-column: 2;
  width: 100%;
}

.msg-item__action .el-button + .el-button {
  margin-left: 0;
}

.msg-item__read-label {
  color: var(--app-text-muted, #9ca3af);
  font-size: 15px;
  text-align: center;
}

@media (max-width: 640px) {
  .msg-item {
    display: grid;
    grid-template-columns: 12px minmax(0, 1fr);
  }

  .msg-item__indicator {
    grid-column: 1;
  }

  .msg-item__body {
    grid-column: 2;
  }

  .msg-item__action {
    grid-column: 2;
    padding-top: 0;
  }
}

.msg-pagination {
  display: flex;
  justify-content: center;
  margin-top: 20px;
}
</style>
