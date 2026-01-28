<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { authApi } from '../api'

const email = ref('')
const phone = ref('')
const password = ref('')
const loading = ref(false)
const errorMsg = ref('')
const successMsg = ref('')

const router = useRouter()

const onSubmit = async () => {
  if (!email.value && !phone.value) {
    errorMsg.value = '邮箱和手机号至少填写一个'
    return
  }
  if (!password.value) {
    errorMsg.value = '请输入密码'
    return
  }

  loading.value = true
  errorMsg.value = ''
  successMsg.value = ''
  try {
    await authApi.register({
      email: email.value || undefined,
      phone: phone.value || undefined,
      password: password.value,
    })
    successMsg.value = '注册成功，请登录'
    setTimeout(() => router.push('/login'), 800)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || e.message || '注册失败'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-card">
    <h2>注册</h2>
    <p class="sub">填写基本账号信息，开始你的旅行之旅</p>
    <form @submit.prevent="onSubmit">
      <label>
        <span class="form-label">邮箱（可选）</span>
        <input v-model="email" class="form-input" type="email" autocomplete="email" />
      </label>
      <label>
        <span class="form-label">手机号（可选）</span>
        <input v-model="phone" class="form-input" type="tel" autocomplete="tel" />
      </label>
      <label>
        <span class="form-label">密码</span>
        <input v-model="password" class="form-input" type="password" autocomplete="new-password" />
      </label>
      <p v-if="errorMsg" class="error">{{ errorMsg }}</p>
      <p v-if="successMsg" class="success">{{ successMsg }}</p>
      <button class="btn primary full" type="submit" :disabled="loading">
        {{ loading ? '注册中...' : '注册' }}
      </button>
    </form>
    <p class="switch">
      已经有账号？
      <router-link to="/login">去登录</router-link>
    </p>
  </div>
</template>

<style scoped>
.auth-card {
  width: 380px;
  max-width: 100%;
  margin: 0 auto;
  padding: 32px 28px;
  border-radius: var(--radius-lg);
  background: #ffffff;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.08);
}

h2 {
  margin-bottom: 4px;
  font-size: 22px;
}

.sub {
  margin-bottom: 20px;
  color: var(--color-text-subtle);
  font-size: 13px;
}

form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.error {
  color: #dc2626;
  font-size: 13px;
}

.success {
  color: #16a34a;
  font-size: 13px;
}

.btn.full {
  width: 100%;
  margin-top: 4px;
}

.switch {
  margin-top: 12px;
  font-size: 13px;
  text-align: center;
  color: #6b7280;
}

.switch a {
  color: #2563eb;
}
</style>

