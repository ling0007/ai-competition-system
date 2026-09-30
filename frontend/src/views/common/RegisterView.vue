<script setup>
import { useRouter } from 'vue-router'
import RegisterPage from '@/components/auth/RegisterPage.vue'

const router = useRouter()
</script>

<template>
  <RegisterPage @switch-to-login="router.push({ name: 'login' })" />
</template>
