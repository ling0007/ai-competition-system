<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, UserPlus } from 'lucide-vue-next'
import {
  getAdminUserList,
  updateUserRole,
  createUser,
  deleteUser,
} from '@/api/user'

const emit = defineEmits(['logout'])

const users = ref([])
const loading = ref(false)
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

// Role edit dialog
const roleDialogVisible = ref(false)
const roleDialogUser = ref(null)
const roleDialogForm = reactive({ role: '' })
const roleDialogLoading = ref(false)

// Add user dialog
const addDialogVisible = ref(false)
const addFormRef = ref(null)
const addForm = reactive({
  username: '',
  password: '',
  realName: '',
  role: '',
  phone: '',
})
const addDialogLoading = ref(false)

const roleTagType = {
  admin: 'danger',
  teacher: 'warning',
  student: '',
}

const roleLabelMap = {
  admin: '管理员',
  teacher: '教师',
  student: '学生',
}

const addFormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度需在3-50个字符之间', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '手机号格式不正确',
      trigger: 'blur',
    },
  ],
}

const filteredUsers = computed(() => {
  if (!searchKeyword.value.trim()) {
    return users.value
  }
  const kw = searchKeyword.value.trim().toLowerCase()
  return users.value.filter(
    (u) =>
      u.username.toLowerCase().includes(kw) ||
      u.realName.toLowerCase().includes(kw),
  )
})

const pagedUsers = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredUsers.value.slice(start, start + pageSize.value)
})

const totalFiltered = computed(() => filteredUsers.value.length)

function resolveErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

async function loadUsers() {
  loading.value = true
  try {
    const response = await getAdminUserList(searchKeyword.value)
    if (response.code === 200) {
      users.value = response.data || []
    } else {
      ElMessage.error(response.message || '获取用户列表失败')
    }
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '获取用户列表失败'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadUsers()
}

function openRoleDialog(user) {
  roleDialogUser.value = user
  roleDialogForm.role = user.role
  roleDialogVisible.value = true
}

async function handleUpdateRole() {
  if (!roleDialogUser.value) return
  roleDialogLoading.value = true
  try {
    const response = await updateUserRole(
      roleDialogUser.value.userId,
      roleDialogForm.role,
    )
    if (response.code !== 200) {
      ElMessage.error(response.message || '角色更新失败')
      return
    }
    ElMessage.success(response.message || '角色更新成功')
    roleDialogVisible.value = false
    await loadUsers()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '角色更新失败'))
  } finally {
    roleDialogLoading.value = false
  }
}

async function handleDeleteUser(user) {
  try {
    await ElMessageBox.confirm(
      `确定要删除用户「${user.realName}」（${user.username}）吗？此操作不可撤销。`,
      '确认删除',
      {
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    )
    const response = await deleteUser(user.userId)
    if (response.code !== 200) {
      ElMessage.error(response.message || '删除失败')
      return
    }
    ElMessage.success(response.message || '用户删除成功')
    await loadUsers()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(resolveErrorMessage(error, '删除用户失败'))
  }
}

function openAddDialog() {
  addForm.username = ''
  addForm.password = ''
  addForm.realName = ''
  addForm.role = ''
  addForm.phone = ''
  addDialogVisible.value = true
  addFormRef.value?.clearValidate()
}

async function handleAddUser() {
  const valid = await addFormRef.value.validate().catch(() => false)
  if (!valid) return

  addDialogLoading.value = true
  try {
    const response = await createUser({
      username: addForm.username,
      password: addForm.password,
      realName: addForm.realName,
      role: addForm.role,
      phone: addForm.phone,
    })
    if (response.code !== 200) {
      ElMessage.error(response.message || '创建用户失败')
      return
    }
    ElMessage.success(response.message || '用户创建成功')
    addDialogVisible.value = false
    await loadUsers()
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '创建用户失败'))
  } finally {
    addDialogLoading.value = false
  }
}

onMounted(() => {
  loadUsers()
})
</script>

<template>
  <div class="admin-dashboard">
    <!-- Header -->
    <header class="admin-header">
      <div class="admin-header__left">
        <h1 class="admin-header__title">AI 竞赛助手</h1>
        <span class="admin-header__subtitle">角色管理</span>
      </div>
      <div class="admin-header__right">
        <el-button class="admin-header__logout" @click="emit('logout')">
          退出登录
        </el-button>
      </div>
    </header>

    <!-- Content -->
    <main class="admin-content">
      <!-- Toolbar -->
      <div class="admin-toolbar">
        <div class="admin-toolbar__search">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索用户名或真实姓名..."
            size="large"
            clearable
            :prefix-icon="Search"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-button type="primary" size="large" @click="handleSearch">
            搜索
          </el-button>
        </div>
        <el-button
          type="primary"
          size="large"
          :icon="UserPlus"
          @click="openAddDialog"
        >
          添加用户
        </el-button>
      </div>

      <!-- User Table -->
      <div class="admin-table-wrapper">
        <el-table
          :data="pagedUsers"
          v-loading="loading"
          stripe
          style="width: 100%"
          size="large"
          empty-text="暂无用户数据"
        >
          <el-table-column prop="userId" label="用户ID" width="100" align="center" />
          <el-table-column prop="username" label="用户名" min-width="140" />
          <el-table-column prop="realName" label="真实姓名" min-width="120" />
          <el-table-column label="角色" width="120" align="center">
            <template #default="{ row }">
              <el-tag :type="roleTagType[row.role]" size="large">
                {{ roleLabelMap[row.role] || row.role }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="phone" label="手机号" width="150" />
          <el-table-column label="操作" width="220" align="center" fixed="right">
            <template #default="{ row }">
              <div class="admin-table__actions">
                <el-button
                  type="primary"
                  size="default"
                  @click="openRoleDialog(row)"
                >
                  编辑角色
                </el-button>
                <el-button
                  type="danger"
                  size="default"
                  @click="handleDeleteUser(row)"
                >
                  删除
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <!-- Pagination -->
      <div v-if="totalFiltered > pageSize" class="admin-pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :total="totalFiltered"
          layout="total, prev, pager, next"
          background
        />
      </div>
    </main>

    <!-- Edit Role Dialog -->
    <el-dialog
      v-model="roleDialogVisible"
      title="编辑用户角色"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form v-if="roleDialogUser" label-position="top">
        <el-form-item label="用户">
          <el-input
            :model-value="`${roleDialogUser.realName}（${roleDialogUser.username}）`"
            disabled
            size="large"
          />
        </el-form-item>
        <el-form-item label="角色">
          <el-select
            v-model="roleDialogForm.role"
            placeholder="请选择角色"
            size="large"
            style="width: 100%"
          >
            <el-option label="学生" value="student" />
            <el-option label="教师" value="teacher" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="large" @click="roleDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          size="large"
          :loading="roleDialogLoading"
          @click="handleUpdateRole"
        >
          确认修改
        </el-button>
      </template>
    </el-dialog>

    <!-- Add User Dialog -->
    <el-dialog
      v-model="addDialogVisible"
      title="添加用户"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="addFormRef"
        :model="addForm"
        :rules="addFormRules"
        label-position="top"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="addForm.username"
            placeholder="请输入用户名"
            size="large"
            maxlength="50"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="addForm.password"
            type="password"
            placeholder="请输入密码（至少6位）"
            size="large"
            show-password
          />
        </el-form-item>
        <el-form-item label="真实姓名" prop="realName">
          <el-input
            v-model="addForm.realName"
            placeholder="请输入真实姓名"
            size="large"
            maxlength="50"
          />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select
            v-model="addForm.role"
            placeholder="请选择角色"
            size="large"
            style="width: 100%"
          >
            <el-option label="学生" value="student" />
            <el-option label="教师" value="teacher" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="addForm.phone"
            placeholder="请输入手机号（选填）"
            size="large"
            maxlength="11"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="large" @click="addDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          size="large"
          :loading="addDialogLoading"
          @click="handleAddUser"
        >
          确认添加
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.admin-dashboard {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: var(--app-bg, #f8fafc);
  font-size: 15px;
}

.admin-header {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 80px;
  padding: 0 37px;
  border-bottom: 1px solid #e5e7eb;
  background: #ffffff;
}

.admin-header__left {
  display: flex;
  align-items: baseline;
  gap: 21px;
}

.admin-header__title {
  margin: 0;
  font-size: 29px;
  font-weight: 700;
  color: var(--app-text-primary, #111827);
}

.admin-header__subtitle {
  font-size: 19px;
  color: var(--app-text-muted, #6b7280);
  font-weight: 500;
}

.admin-header__logout {
  color: var(--app-text-secondary, #4b5563);
  font-size: 16px;
}

.admin-content {
  flex: 1;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 37px;
  font-size: 15px;
}

.admin-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 21px;
  margin-bottom: 32px;
}

.admin-toolbar__search {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  max-width: 480px;

  .el-input {
    flex: 1;
  }
}

.admin-table-wrapper {
  border: 1px solid var(--app-border, #e5e7eb);
  border-radius: 8px;
  background: #ffffff;
  overflow: hidden;
  box-shadow: var(--app-shadow-sm);
  font-size: 16px;

  :deep(.el-table__header-wrapper th),
  :deep(.el-table__body-wrapper td) {
    font-size: 16px;
  }

  :deep(.el-table__empty-text) {
    font-size: 16px;
  }
}

.admin-pagination {
  display: flex;
  justify-content: center;
  margin-top: 32px;
}

.admin-table__actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}
</style>
