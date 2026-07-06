<script setup>
import { computed } from 'vue'
import { ChevronUp, PanelLeftClose, PanelLeftOpen } from 'lucide-vue-next'

const props = defineProps({
  collapsed: {
    type: Boolean,
    default: false,
  },
  activeMenu: {
    type: String,
    default: 'overview',
  },
  menuItems: {
    type: Array,
    default: () => [],
  },
  summaryItems: {
    type: Array,
    default: () => [],
  },
  unreadCount: {
    type: Number,
    default: 0,
  },
  currentUser: {
    type: Object,
    default: null,
  },
})

const emit = defineEmits(['toggle-sidebar', 'select-menu', 'open-profile', 'logout'])

const displayName = computed(() => {
  return props.currentUser?.realName || props.currentUser?.username || '用户'
})

const userInitial = computed(() => {
  const name = displayName.value
  return name ? name.charAt(0).toUpperCase() : 'U'
})
</script>

<template>
  <div class="app-shell" :class="{ 'app-shell--collapsed': collapsed }">
    <aside class="app-shell__aside">
      <div class="app-shell__brand">
        <div class="app-shell__brand-mark">AI</div>
        <div class="app-shell__brand-copy" :class="{ 'app-shell__text-block--hidden': collapsed }">
          <strong>Campus Copilot</strong>
          <span>竞赛申报智能工作台</span>
        </div>
      </div>

      <nav class="app-shell__nav" aria-label="后台导航">
        <button
          v-for="item in menuItems"
          :key="item.key"
          type="button"
          class="app-shell__nav-item"
          :class="{ 'app-shell__nav-item--active': activeMenu === item.key }"
          @click="$emit('select-menu', item.key)"
        >
          <span class="app-shell__nav-icon">
            <component :is="item.icon" :size="18" :stroke-width="1.5" />
          </span>
          <span class="app-shell__nav-label" :class="{ 'app-shell__nav-label--hidden': collapsed }">
            {{ item.label }}
          </span>
          <span
            v-if="item.key === 'messages' && unreadCount > 0"
            class="app-shell__nav-badge"
            :class="{ 'app-shell__nav-badge--collapsed': collapsed }"
          >
            {{ unreadCount > 99 ? '99+' : unreadCount }}
          </span>
        </button>
      </nav>

      <!-- 用户区（替旧"系统在线"） -->
      <div class="app-shell__user-spacer" />

      <el-popover
        placement="top"
        :width="200"
        trigger="click"
        :offset="8"
        popper-class="app-shell__user-popover"
      >
        <template #reference>
          <div class="app-shell__user-area">
            <div class="app-shell__user-avatar">{{ userInitial }}</div>
            <div class="app-shell__user-copy" :class="{ 'app-shell__text-block--hidden': collapsed }">
              <strong>{{ displayName }}</strong>
              <span>{{ currentUser?.role === 'teacher' ? '教师' : currentUser?.role === 'admin' ? '管理员' : '学生' }}</span>
            </div>
            <ChevronUp
              v-show="!collapsed"
              :size="14"
              :stroke-width="1.5"
              class="app-shell__user-chevron"
            />
          </div>
        </template>

        <div class="app-shell__user-menu">
          <button type="button" @click="$emit('open-profile')">个人信息</button>
          <button type="button" @click="$emit('logout')">退出登录</button>
        </div>
      </el-popover>
    </aside>

    <div class="app-shell__main">
      <header class="app-shell__header">
        <div class="app-shell__header-left">
          <button
            type="button"
            class="app-shell__toggle"
            :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
            @click="$emit('toggle-sidebar')"
          >
            <component :is="collapsed ? PanelLeftOpen : PanelLeftClose" :size="20" :stroke-width="1.5" />
          </button>

          <div class="app-shell__title">
            <strong>校园竞赛申报材料智能核验系统</strong>
            <span>竞赛申报管理平台</span>
          </div>
        </div>

        <!-- 预留：右侧可放全局操作，当前为空 -->
      </header>

      <main class="app-shell__content">
        <section v-if="summaryItems.length" class="app-shell__summary" aria-label="关键指标">
          <div v-for="item in summaryItems" :key="item.label" class="app-shell__summary-card">
            <span>{{ item.label }}</span>
            <strong>{{ item.value }}</strong>
          </div>
        </section>

        <slot />
      </main>
    </div>
  </div>
</template>

<style scoped lang="scss">
.app-shell {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  min-height: 100vh;
  background: var(--app-bg);
}

.app-shell--collapsed {
  grid-template-columns: 80px minmax(0, 1fr);
}

.app-shell__aside {
  position: sticky;
  top: 0;
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 24px;
  width: 240px;
  height: 100vh;
  overflow-y: auto;
  padding: 20px 16px;
  background: #1e293b;
}

.app-shell--collapsed .app-shell__aside {
  width: 80px;
  padding-inline: 12px;
}

.app-shell__brand {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
}

.app-shell__brand-mark {
  flex: 0 0 44px;
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--app-radius-sm);
  color: #ffffff;
  font-size: 18px;
  font-weight: 700;
}

.app-shell__brand-copy,
.app-shell__user-copy {
  display: grid;
  gap: 3px;
  min-width: 0;
  max-width: 160px;
  overflow: hidden;
}

.app-shell__text-block--hidden,
.app-shell__nav-label--hidden {
  width: 0;
  max-width: 0;
  opacity: 0;
  visibility: hidden;
}

.app-shell__brand-copy strong,
.app-shell__user-copy strong {
  overflow: hidden;
  color: #f9fafb;
  font-size: 17px;
  font-weight: 600;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-shell__brand-copy span,
.app-shell__user-copy span {
  overflow: hidden;
  color: #9ca3af;
  font-size: 14px;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-shell__nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.app-shell__nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 40px;
  padding: 0 12px;
  border: 0;
  border-radius: var(--app-radius-sm);
  background: transparent;
  color: #cbd5e1;
  cursor: pointer;
  text-align: left;
}

.app-shell__nav-item:hover {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.06);
}

.app-shell__nav-item--active {
  color: #ffffff;
  background: var(--app-primary);
}

.app-shell__nav-icon {
  flex: 0 0 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
}

.app-shell__nav-label {
  overflow: hidden;
  color: inherit;
  font-size: 16px;
  font-weight: 500;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 消息 Badge */
.app-shell__nav-badge {
  margin-left: auto;
  min-width: 22px;
  height: 22px;
  padding: 0 6px;
  border-radius: 999px;
  background: #ef4444;
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  line-height: 22px;
  text-align: center;
}

.app-shell__nav-badge--collapsed {
  position: absolute;
  top: 4px;
  right: 4px;
  margin-left: 0;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  font-size: 10px;
  line-height: 16px;
}

/* 用户区 */
.app-shell__user-spacer {
  flex: 1;
  min-height: 0;
}

.app-shell__user-area {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--app-radius-sm);
  cursor: pointer;
  transition: background 0.15s;
}

.app-shell__user-area:hover {
  background: rgba(255, 255, 255, 0.06);
}

.app-shell__user-avatar {
  flex: 0 0 36px;
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 999px;
  background: var(--app-primary);
  color: #ffffff;
  font-size: 15px;
  font-weight: 700;
}

.app-shell__user-chevron {
  flex: 0 0 auto;
  color: #9ca3af;
  margin-left: auto;
  transition: transform 0.2s;
}

.app-shell__user-menu {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 4px 0;
}

.app-shell__user-menu button {
  display: block;
  width: 100%;
  padding: 12px 18px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  text-align: left;
  font-size: 17px;
  color: #cbd5e1;
  cursor: pointer;
  transition: background 0.12s, color 0.12s;
}

.app-shell__user-menu button:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

/* Main area */
.app-shell__main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.app-shell__header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  height: 60px;
  padding: 0 24px;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
}

.app-shell__header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.app-shell__toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
  color: var(--app-text-secondary);
  cursor: pointer;
}

.app-shell__toggle:hover {
  color: var(--app-primary);
  border-color: var(--app-primary);
}

.app-shell__title {
  display: grid;
  min-width: 0;
}

.app-shell__title strong {
  overflow: hidden;
  color: var(--app-text-primary);
  font-size: 24px;
  font-weight: 700;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-shell__title span {
  color: var(--app-text-muted);
  font-size: 14px;
  line-height: 1.4;
}

.app-shell__content {
  display: grid;
  gap: 24px;
  min-width: 0;
  padding: 24px;
}

.app-shell__summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
}

.app-shell__summary-card {
  display: grid;
  gap: 8px;
  min-height: 92px;
  padding: 24px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
  box-shadow: var(--app-shadow-sm);
}

.app-shell__summary-card span {
  color: var(--app-text-muted);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.05em;
  line-height: 1.3;
  text-transform: uppercase;
}

.app-shell__summary-card strong {
  overflow: hidden;
  color: var(--app-text-primary);
  font-size: 26px;
  font-weight: 700;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Responsive */
@media (max-width: 1240px) {
  .app-shell,
  .app-shell--collapsed {
    grid-template-columns: 1fr;
  }

  .app-shell__aside,
  .app-shell--collapsed .app-shell__aside {
    position: static;
    width: 100%;
    height: auto;
    padding: 16px 24px;
  }

  .app-shell__nav {
    flex-flow: row wrap;
  }

  .app-shell__text-block--hidden,
  .app-shell__nav-label--hidden {
    width: auto;
    max-width: 160px;
    opacity: 1;
    visibility: visible;
  }

  .app-shell__user-spacer {
    display: none;
  }

  .app-shell__nav-badge--collapsed {
    position: static;
    min-width: 20px;
    height: 20px;
    font-size: 11px;
    line-height: 20px;
  }
}

@media (max-width: 900px) {
  .app-shell__header {
    align-items: flex-start;
    height: auto;
    min-height: 60px;
    padding-block: 12px;
  }

  .app-shell__summary {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .app-shell__aside,
  .app-shell--collapsed .app-shell__aside,
  .app-shell__header,
  .app-shell__content {
    padding-left: 16px;
    padding-right: 16px;
  }

  .app-shell__header {
    flex-direction: column;
  }
}
</style>

<style lang="scss">
/* 用户区 Popover — 非 scoped，因为 el-popover 渲染到 body */
.app-shell__user-popover {
  padding: 10px 6px !important;
  background: #1e293b !important;
  border: 1px solid rgba(255, 255, 255, 0.08) !important;
  border-radius: 8px !important;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4) !important;
}

.app-shell__user-popover .el-popover__title {
  color: #f9fafb;
}
</style>
