<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserProfile, updateProfile, changePassword } from '@/api/user'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  currentUser: {
    type: Object,
    default: () => ({}),
  },
})

const emit = defineEmits(['close', 'updated'])

const activeTab = ref('profile')
const profileLoading = ref(false)
const passwordLoading = ref(false)

const profileFormRef = ref(null)
const passwordFormRef = ref(null)

const profileForm = reactive({
  username: '',
  realName: '',
  phone: '',
})

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const profileRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度需在3-50个字符之间', trigger: 'blur' },
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [
    {
      pattern: /^1[3-9]\d{9}$/,
      message: '手机号格式不正确',
      trigger: 'blur',
    },
  ],
}

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== passwordForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

function resolveErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

watch(
  () => props.visible,
  async (val) => {
    if (val) {
      activeTab.value = 'profile'
      passwordForm.oldPassword = ''
      passwordForm.newPassword = ''
      passwordForm.confirmPassword = ''
      profileFormRef.value?.clearValidate()
      passwordFormRef.value?.clearValidate()

      // Fetch latest profile from API
      try {
        const response = await getUserProfile()
        if (response.code === 200 && response.data) {
          profileForm.username = response.data.username || ''
          profileForm.realName = response.data.realName || ''
          profileForm.phone = response.data.phone || ''
        } else {
          // Fallback to props
          profileForm.username = props.currentUser?.username || ''
          profileForm.realName = props.currentUser?.realName || ''
          profileForm.phone = props.currentUser?.phone || ''
        }
      } catch {
        // Fallback to props on error
        profileForm.username = props.currentUser?.username || ''
        profileForm.realName = props.currentUser?.realName || ''
        profileForm.phone = props.currentUser?.phone || ''
      }
    }
  },
)

async function handleUpdateProfile() {
  const valid = await profileFormRef.value.validate().catch(() => false)
  if (!valid) return

  profileLoading.value = true
  try {
    const response = await updateProfile({
      username: profileForm.username,
      realName: profileForm.realName,
      phone: profileForm.phone,
    })
    if (response.code !== 200) {
      ElMessage.error(response.message || '更新失败')
      return
    }
    ElMessage.success(response.message || '个人信息更新成功')
    emit('updated', {
      username: profileForm.username,
      realName: profileForm.realName,
      phone: profileForm.phone,
    })
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '更新个人信息失败'))
  } finally {
    profileLoading.value = false
  }
}

async function handleChangePassword() {
  const valid = await passwordFormRef.value.validate().catch(() => false)
  if (!valid) return

  passwordLoading.value = true
  try {
    const response = await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    if (response.code !== 200) {
      ElMessage.error(response.message || '修改密码失败')
      return
    }
    ElMessage.success(response.message || '密码修改成功')
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '修改密码失败'))
  } finally {
    passwordLoading.value = false
  }
}
</script>

<template>
  <el-dialog
    :model-value="visible"
    title="个人信息维护"
    width="580px"
    :close-on-click-modal="false"
    @update:model-value="emit('close')"
  >
    <el-tabs v-model="activeTab" class="profile-tabs">
      <el-tab-pane label="个人信息" name="profile">
        <el-form
          ref="profileFormRef"
          :model="profileForm"
          :rules="profileRules"
          label-position="top"
          class="profile-form"
        >
          <el-form-item label="用户名" prop="username">
            <el-input
              v-model="profileForm.username"
              placeholder="请输入用户名"
              size="large"
              maxlength="50"
            />
          </el-form-item>

          <el-form-item label="真实姓名" prop="realName">
            <el-input
              v-model="profileForm.realName"
              placeholder="请输入真实姓名"
              size="large"
              maxlength="50"
            />
          </el-form-item>

          <el-form-item label="手机号" prop="phone">
            <el-input
              v-model="profileForm.phone"
              placeholder="请输入手机号"
              size="large"
              maxlength="11"
            />
          </el-form-item>

          <el-form-item label="角色">
            <el-input
              :model-value="{
                student: '学生',
                teacher: '教师',
                admin: '管理员',
              }[currentUser?.role] || currentUser?.role"
              disabled
              size="large"
            />
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            :loading="profileLoading"
            class="profile-form__submit"
            @click="handleUpdateProfile"
          >
            保存修改
          </el-button>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="修改密码" name="password">
        <el-form
          ref="passwordFormRef"
          :model="passwordForm"
          :rules="passwordRules"
          label-position="top"
          class="profile-form"
        >
          <el-form-item label="原密码" prop="oldPassword">
            <el-input
              v-model="passwordForm.oldPassword"
              type="password"
              placeholder="请输入原密码"
              size="large"
              show-password
            />
          </el-form-item>

          <el-form-item label="新密码" prop="newPassword">
            <el-input
              v-model="passwordForm.newPassword"
              type="password"
              placeholder="请输入新密码（至少6位）"
              size="large"
              show-password
            />
          </el-form-item>

          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input
              v-model="passwordForm.confirmPassword"
              type="password"
              placeholder="请再次输入新密码"
              size="large"
              show-password
              @keyup.enter="handleChangePassword"
            />
          </el-form-item>

          <el-button
            type="primary"
            size="large"
            :loading="passwordLoading"
            class="profile-form__submit"
            @click="handleChangePassword"
          >
            修改密码
          </el-button>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<style scoped lang="scss">
.profile-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 8px;
  }

  :deep(.el-tabs__item) {
    font-size: 18px;
    font-weight: 500;
  }
}

.profile-form {
  padding-top: 8px;

  :deep(.el-form-item__label) {
    font-weight: 500;
    font-size: 18px;
    color: var(--app-text-primary, #374151);
  }

  :deep(.el-input.is-disabled .el-input__wrapper) {
    background-color: #f3f4f6;
    box-shadow: 0 0 0 1px #e5e7eb inset;
  }

  :deep(.el-input__inner) {
    font-size: 16px;
  }

  :deep(.el-form-item__error) {
    color: #dc2626;
    font-size: 13px;
  }
}

.profile-form__submit {
  width: 100%;
  margin-top: 4px;
  height: 56px;
  font-size: 18px;
}
</style>
