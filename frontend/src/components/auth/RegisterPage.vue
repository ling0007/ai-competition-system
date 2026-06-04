<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { register } from '@/api/auth'

const emit = defineEmits(['switch-to-login'])

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  realName: '',
  phone: '',
  role: '',
  password: '',
  confirmPassword: '',
})

const validateConfirmPassword = (rule, value, callback) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const validatePhone = (rule, value, callback) => {
  if (!value || value.trim() === '') {
    callback(new Error('请输入手机号'))
    return
  }
  if (!/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('手机号格式不正确'))
    return
  }
  callback()
}

const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度需在3-50个字符之间', trigger: 'blur' },
  ],
  realName: [{ required: true, message: '请输入真实姓名', trigger: 'blur' }],
  phone: [
    { validator: validatePhone, trigger: 'blur' },
  ],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
}

function resolveErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

async function handleRegister() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const response = await register({
      username: form.username,
      password: form.password,
      confirmPassword: form.confirmPassword,
      realName: form.realName,
      role: form.role,
      phone: form.phone,
    })
    if (response.code !== 200) {
      ElMessage.error(response.message || '注册失败')
      return
    }
    ElMessage.success('注册成功，请登录')
    emit('switch-to-login')
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '注册失败'))
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-card__brand">
        <h1 class="auth-card__title">AI 竞赛助手</h1>
        <p class="auth-card__subtitle">竞赛申报管理系统</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        :hide-required-asterisk="true"
        class="auth-card__form"
        @submit.prevent="handleRegister"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            size="large"
            maxlength="50"
          />
        </el-form-item>

        <el-form-item label="真实姓名" prop="realName">
          <el-input
            v-model="form.realName"
            placeholder="请输入真实姓名"
            size="large"
            maxlength="50"
          />
        </el-form-item>

        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="请输入手机号"
            size="large"
            maxlength="11"
          />
        </el-form-item>

        <el-form-item label="角色" prop="role">
          <el-select
            v-model="form.role"
            placeholder="请选择角色"
            size="large"
            class="auth-card__select"
          >
            <el-option label="学生" value="student" />
            <el-option label="教师" value="teacher" />
          </el-select>
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="请再次输入密码"
            size="large"
            show-password
            @keyup.enter="handleRegister"
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          :loading="loading"
          class="auth-card__submit"
          @click="handleRegister"
        >
          注册
        </el-button>
      </el-form>

      <p class="auth-card__switch">
        已有账号？
        <a href="javascript:void(0)" @click="emit('switch-to-login')">立即登录</a>
      </p>
    </div>
  </div>
</template>

<style scoped lang="scss">
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: var(--app-bg, #f8fafc);
}

.auth-card {
  width: 100%;
  max-width: 560px;
  padding: 48px 56px 40px;
  border: 1px solid var(--app-border, #e5e7eb);
  border-radius: 12px;
  background: var(--app-surface, #ffffff);
  box-shadow: 0 2px 8px rgba(17, 24, 39, 0.06), 0 8px 32px rgba(17, 24, 39, 0.08);
}

.auth-card__brand {
  text-align: center;
  margin-bottom: 36px;
}

.auth-card__title {
  margin: 0 0 12px;
  font-size: 32px;
  font-weight: 700;
  color: var(--app-text-primary, #111827);
  letter-spacing: -0.02em;
}

.auth-card__subtitle {
  margin: 0;
  font-size: 16px;
  color: var(--app-text-muted, #6b7280);
}

.auth-card__form {
  :deep(.el-form-item__label) {
    font-weight: 500;
    font-size: 15px;
    color: var(--app-text-primary, #374151);
  }

  :deep(.el-input__inner) {
    font-size: 15px;
  }

  :deep(.el-form-item__error) {
    color: #dc2626;
    font-size: 13px;
  }

  :deep(.el-form-item.is-error .el-input__wrapper) {
    box-shadow: 0 0 0 1px #dc2626 inset;
  }

  :deep(.el-select .el-input.is-focus .el-input__wrapper) {
    box-shadow: 0 0 0 1px var(--app-primary, #3b82f6) inset;
  }
}

.auth-card__select {
  width: 100%;
}

.auth-card__submit {
  width: 100%;
  margin-top: 8px;
  height: 48px;
  font-size: 16px;
}

.auth-card__switch {
  margin: 26px 0 0;
  text-align: center;
  font-size: 15px;
  color: var(--app-text-muted, #6b7280);

  a {
    color: var(--app-primary, #3b82f6);
    text-decoration: none;
    font-weight: 500;
    cursor: pointer;

    &:hover {
      text-decoration: underline;
    }
  }
}
</style>
