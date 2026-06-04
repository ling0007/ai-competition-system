<script setup>
defineProps({
  title: {
    type: String,
    required: true,
  },
  subtitle: {
    type: String,
    default: '',
  },
})
</script>

<template>
  <section class="feature-panel">
    <header class="feature-panel__header">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <div class="feature-panel__actions">
        <slot name="actions" />
      </div>
    </header>

    <div class="feature-panel__body">
      <slot />
    </div>
  </section>
</template>

<style scoped lang="scss">
.feature-panel {
  display: flex;
  flex-direction: column;
  gap: 24px;
  height: 100%;
  padding: 24px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
  box-shadow: var(--app-shadow-sm);
}

.feature-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.feature-panel__header h2 {
  margin: 0;
  color: var(--app-text-primary);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.35;
}

.feature-panel__header p {
  margin: 8px 0 0;
  color: var(--app-text-muted);
  font-size: 12px;
  line-height: 1.6;
}

.feature-panel__actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.feature-panel__body {
  min-height: 0;
}

@media (max-width: 768px) {
  .feature-panel {
    padding: 20px;
  }

  .feature-panel__header {
    flex-direction: column;
  }
}
</style>
