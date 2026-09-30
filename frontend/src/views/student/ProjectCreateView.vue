<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ProjectCreatePanel from '@/components/dashboard/ProjectCreatePanel.vue'
import { getNoticeDetail } from '@/api/competition'
import { createStudentProject, searchUserOptions } from '@/api/projects'
import { getAuthState } from '@/state/auth.js'

const route = useRoute()
const router = useRouter()
const auth = getAuthState()
const noticeId = computed(() => Number(route.query.noticeId) || null)
const noticeOptions = ref([])
const userOptions = ref([])
const creating = ref(false)

async function loadOptions() {
  if (!noticeId.value) {
    router.replace('/student/notices')
    return
  }
  try {
    const [noticeResponse, teachers, students] = await Promise.all([
      getNoticeDetail(noticeId.value),
      searchUserOptions('teacher'),
      searchUserOptions('student'),
    ])
    const notice = noticeResponse.data
    if (notice.publishStatus !== 'PUBLISHED') throw new Error('该通知当前不可申报')
    noticeOptions.value = [{ value: notice.noticeId, label: notice.title, deadline: notice.deadline }]
    userOptions.value = [...teachers.data, ...students.data]
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '申报信息加载失败')
    router.replace('/student/notices')
  }
}

async function create(payload) {
  creating.value = true
  try {
    const response = await createStudentProject({ ...payload, noticeId: noticeId.value })
    ElMessage.success('项目创建成功，请继续完善材料')
    router.replace(`/student/projects/${response.data.projectId}`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || error?.message || '创建项目失败')
  } finally {
    creating.value = false
  }
}

onMounted(loadOptions)
</script>

<template>
  <ProjectCreatePanel
    :notice-options="noticeOptions"
    :user-options="userOptions"
    :current-user="auth.user"
    :initial-notice-id="noticeId"
    lock-notice
    :creating="creating"
    @create="create"
  />
</template>
