<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '@/components/layout/AppShell.vue'
import UserProfileDialog from '@/components/user/UserProfileDialog.vue'
import { getMenuItemsByRole } from '@/config/menu.js'
import { getAuthState, logout, updateProfile } from '@/state/auth.js'
import { countUnreadNotifications } from '@/api/notifications.js'

const route = useRoute()
const router = useRouter()
const auth = getAuthState()
const collapsed = ref(false)
const profileVisible = ref(false)
const unreadCount = ref(0)

const currentUser = computed(() => auth.user)
const menuItems = computed(() => getMenuItemsByRole(currentUser.value?.role))
const activeMenu = computed(() => route.meta.menuKey ?? '')

async function loadUnreadCount() {
  if (currentUser.value?.role !== 'student' || !currentUser.value?.userId) return
  try {
    const response = await countUnreadNotifications()
    unreadCount.value = response.data ?? 0
  } catch {
    unreadCount.value = 0
  }
}

function navigate(menuKey) {
  const target = menuItems.value.find((item) => item.key === menuKey)
  if (target) router.push(target.route)
}

function handleLogout() {
  logout()
  router.replace({ name: 'login' })
}

function handleProfileUpdated(profile) {
  updateProfile(profile)
}

onMounted(loadUnreadCount)
onMounted(() => window.addEventListener('messages-changed', loadUnreadCount))
onUnmounted(() => window.removeEventListener('messages-changed', loadUnreadCount))
</script>

<template>
  <AppShell
    :collapsed="collapsed"
    :active-menu="activeMenu"
    :menu-items="menuItems"
    :unread-count="unreadCount"
    :current-user="currentUser"
    @toggle-sidebar="collapsed = !collapsed"
    @select-menu="navigate"
    @open-profile="profileVisible = true"
    @logout="handleLogout"
  >
    <router-view />
  </AppShell>

  <UserProfileDialog
    :visible="profileVisible"
    :current-user="currentUser"
    @close="profileVisible = false"
    @updated="handleProfileUpdated"
    @password-changed="handleLogout"
  />
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
}
</style>
