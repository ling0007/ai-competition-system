<script setup>
defineProps({
  title: {
    type: String,
    required: true,
  },
  value: {
    type: String,
    required: true,
  },
  description: {
    type: String,
    default: '',
  },
  icon: {
    type: Object,
    default: null,
  },
  accent: {
    type: String,
    default: 'blue',
  },
})
</script>

<template>
  <article class="stat-card" :class="`stat-card--${accent}`">
    <div class="stat-card__head">
      <span>{{ title }}</span>
      <div v-if="icon" class="stat-card__icon">
        <component :is="icon" :size="18" :stroke-width="1.5" />
      </div>
    </div>
    <strong>{{ value }}</strong>
    <p>{{ description }}</p>
  </article>
</template>

<style scoped lang="scss">
.stat-card {
  padding: 24px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
  box-shadow: var(--app-shadow-sm);
}

.stat-card--blue {
  border-color: var(--app-border);
}

.stat-card--green {
  border-color: var(--app-border);
}

.stat-card--amber {
  border-color: var(--app-border);
}

.stat-card--slate {
  border-color: var(--app-border);
}

.stat-card--red {
  border-color: var(--app-border);
  background: #ffffff;
}

.stat-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 18px;
}

.stat-card__head span,
.stat-card p {
  color: var(--app-text-muted);
}

.stat-card__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-sm);
  background: #ffffff;
  color: var(--app-primary);
}

.stat-card--red .stat-card__icon {
  color: var(--app-danger);
}

.stat-card--green .stat-card__icon {
  color: var(--app-success);
}

.stat-card strong {
  display: block;
  margin-bottom: 8px;
  color: var(--app-text-primary);
  font-size: 20px;
  font-weight: 700;
  line-height: 1.35;
}

.stat-card p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
}
</style>
