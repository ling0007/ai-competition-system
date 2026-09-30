<script setup>
import { useRouter } from 'vue-router'
import NoticeListPanel from '@/components/dashboard/NoticeListPanel.vue'

const router = useRouter()

function openNotice(notice) {
  router.push(`/student/notices/${notice.noticeId}`)
}

function applyForNotice(notice) {
  router.push({ path: '/student/projects/new', query: { noticeId: notice.noticeId } })
}
</script>

<template>
  <NoticeListPanel
    role="student"
    @select-notice="openNotice"
    @create-project="applyForNotice"
  />
</template>
