<script setup>
import { useRouter } from 'vue-router'
import { getRoleHome } from '@/config/roles.js'
import { getAuthState } from '@/state/auth.js'

const router = useRouter()
const auth = getAuthState()
</script>

<template>
  <main class="error-page">
    <p>403</p>
    <h1>无权访问此页面</h1>
    <span>当前账号没有该页面所需的角色权限。</span>
    <el-button type="primary" @click="router.replace(getRoleHome(auth.user?.role))">返回工作台</el-button>
  </main>
</template>

<style scoped>
.error-page { display: grid; place-items: center; align-content: center; gap: 14px; min-height: 100vh; padding: 32px; text-align: center; }
.error-page p { margin: 0; color: var(--app-primary); font-size: 72px; font-weight: 800; }
.error-page h1 { margin: 0; color: var(--app-text-primary); }
.error-page span { margin-bottom: 10px; color: var(--app-text-muted); }
</style>
