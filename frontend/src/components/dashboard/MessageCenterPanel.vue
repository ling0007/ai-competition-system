<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Bell, CheckCheck } from 'lucide-vue-next'
import FeaturePanel from '@/components/shared/FeaturePanel.vue'
import { getNotifyMessages, markMessageRead, markAllMessagesRead } from '@/api/competition'
import { formatDateTime } from '@/utils/format'

const props = defineProps({
  currentUser: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['messages-read'])

const loading = ref(false)
const markingAll = ref(false)
const messages = ref([])
const unreadOnly = ref(false)

const msgTypeConfig = {
  material: { label: '材料通知', tagType: 'warning' },
  deadline: { label: '截止提醒', tagType: 'danger' },
  system: { label: '系统消息', tagType: 'info' },
}

const hasUnread = computed(() => messages.value.some((m) => m.isRead === 0))

const displayedMessages = computed(() => {
  let list = messages.value.slice()
  if (unreadOnly.value) {
    list = list.filter((m) => m.isRead === 0)
  }
  return list
})

async function loadMessages() {
  loading.value = true
  try {
    const isRead = unreadOnly.value ? 0 : undefined
    const response = await getNotifyMessages(props.currentUser?.userId, isRead)
    messages.value = response.data ?? []
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '获取消息列表失败')
  } finally {
    loading.value = false
  }
}

async function handleMarkRead(msgId) {
  try {
    await markMessageRead(msgId)
    const msg = messages.value.find((m) => m.msgId === msgId)
    if (msg) {
      msg.isRead = 1
    }
    emit('messages-read')
  } catch (error) {
    ElMessage.error('标记已读失败')
  }
}

async function handleMarkAllRead() {
  markingAll.value = true
  try {
    await markAllMessagesRead(props.currentUser?.userId)
    messages.value.forEach((m) => { m.isRead = 1 })
    emit('messages-read')
    ElMessage.success('全部消息已标记为已读')
  } catch (error) {
    ElMessage.error('全部标记已读失败')
  } finally {
    markingAll.value = false
  }
}

function handleToggleUnread(val) {
  loadMessages()
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
      <el-radio-group
        v-model="unreadOnly"
        size="small"
        @change="handleToggleUnread"
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
      <template v-if="displayedMessages.length">
        <div
          v-for="msg in displayedMessages"
          :key="msg.msgId"
          class="msg-item"
          :class="{ 'msg-item--unread': msg.isRead === 0 }"
          @click="msg.isRead === 0 && handleMarkRead(msg.msgId)"
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
            <el-button
              v-if="msg.isRead === 0"
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
    margin: 0 -12px;
    padding-left: 12px;
    padding-right: 12px;
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
  display: flex;
  align-items: center;
  padding-top: 4px;
}

.msg-item__read-label {
  color: var(--app-text-muted, #9ca3af);
  font-size: 15px;
}
</style>
