<script setup>
import { useRouter } from 'vue-router'
import NoticeListPanel from '@/components/dashboard/NoticeListPanel.vue'

const router = useRouter()
</script>

<template>
  <NoticeListPanel
    role="admin"
    @create-notice="router.push('/admin/notices/new')"
    @select-notice="(notice) => router.push(`/admin/notices/${notice.noticeId}`)"
  />
</template>
