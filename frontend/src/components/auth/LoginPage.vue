<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { login } from '@/api/auth'

const emit = defineEmits(['login-success', 'switch-to-register'])

const formRef = ref(null)
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' },
  ],
}

function resolveErrorMessage(error, fallback) {
  return error?.response?.data?.message || error?.message || fallback
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const response = await login({ username: form.username, password: form.password })
    if (response.code !== 200) {
      ElMessage.error(response.message || '登录失败')
      return
    }
    ElMessage.success(response.message || '登录成功')
    emit('login-success', response.data)
  } catch (error) {
    ElMessage.error(resolveErrorMessage(error, '登录失败'))
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
        class="auth-card__form"
        @submit.prevent="handleLogin"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="form.username"
            placeholder="请输入用户名"
            size="large"
            maxlength="50"
          />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-button
          type="primary"
          size="large"
          :loading="loading"
          class="auth-card__submit"
          @click="handleLogin"
        >
          登录
        </el-button>
      </el-form>

      <p class="auth-card__switch">
        还没有账号？
        <a href="javascript:void(0)" @click="emit('switch-to-register')">立即注册</a>
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
  padding: 56px 56px 48px;
  border: 1px solid var(--app-border, #e5e7eb);
  border-radius: 12px;
  background: var(--app-surface, #ffffff);
  box-shadow: 0 2px 8px rgba(17, 24, 39, 0.06), 0 8px 32px rgba(17, 24, 39, 0.08);
}

.auth-card__brand {
  text-align: center;
  margin-bottom: 44px;
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
}

.auth-card__submit {
  width: 100%;
  margin-top: 12px;
  height: 48px;
  font-size: 16px;
}

.auth-card__switch {
  margin: 28px 0 0;
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
