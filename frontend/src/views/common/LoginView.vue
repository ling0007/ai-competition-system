<script setup>
import { useRoute, useRouter } from 'vue-router'
import LoginPage from '@/components/auth/LoginPage.vue'
import { getRoleHome } from '@/config/roles.js'
import { login } from '@/state/auth.js'

const route = useRoute()
const router = useRouter()

function handleLogin(userData) {
  login(userData)
  const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
    ? route.query.redirect
    : getRoleHome(userData.role)
  router.replace(redirect)
}
</script>

<template>
  <LoginPage
    @login-success="handleLogin"
    @switch-to-register="router.push({ name: 'register' })"
  />
</template>
