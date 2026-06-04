<script setup>
import { PanelLeftClose, PanelLeftOpen } from 'lucide-vue-next'

defineProps({
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
})

defineEmits(['toggle-sidebar', 'select-menu'])
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
        </button>
      </nav>

      <div class="app-shell__status">
        <div class="app-shell__status-dot" />
        <div class="app-shell__status-copy" :class="{ 'app-shell__text-block--hidden': collapsed }">
          <strong>系统在线</strong>
          <span>数据与材料状态实时同步</span>
        </div>
      </div>
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

        <div v-if="$slots['hero-actions']" class="app-shell__header-actions">
          <slot name="hero-actions" />
        </div>
      </header>

      <main class="app-shell__content">
        <section class="app-shell__summary" aria-label="关键指标">
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
  flex: 0 0 40px;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--app-radius-sm);
  color: #ffffff;
  font-size: 15px;
  font-weight: 700;
}

.app-shell__brand-copy,
.app-shell__status-copy {
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
.app-shell__status-copy strong {
  overflow: hidden;
  color: #f9fafb;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-shell__brand-copy span,
.app-shell__status-copy span {
  overflow: hidden;
  color: #9ca3af;
  font-size: 12px;
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
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-shell__status {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 64px;
  margin-top: auto;
  padding: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--app-radius-sm);
}

.app-shell__status-dot {
  flex: 0 0 8px;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: var(--app-success);
}

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
  font-size: 20px;
  font-weight: 700;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-shell__title span {
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.4;
}

.app-shell__header-actions {
  display: flex;
  flex: 0 0 auto;
  align-items: center;
  gap: 8px;
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
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  line-height: 1.3;
  text-transform: uppercase;
}

.app-shell__summary-card strong {
  overflow: hidden;
  color: var(--app-text-primary);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

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

  .app-shell__status {
    display: none;
  }
}

@media (max-width: 900px) {
  .app-shell__header {
    align-items: flex-start;
    height: auto;
    min-height: 60px;
    padding-block: 12px;
  }

  .app-shell__header-actions {
    flex-wrap: wrap;
    justify-content: flex-end;
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

  .app-shell__header-actions {
    width: 100%;
    justify-content: flex-start;
  }
}
</style>
