<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { authApi, userApi } from '../api'
import { useAuthStore } from '../store'

const mode = ref<'login' | 'register'>('login')
const tab = ref<'phone' | 'email'>('phone')

const phone = ref('')
const email = ref('')
const password = ref('')
const passwordVisible = ref(false)

const loading = ref(false)
const errorMsg = ref('')
const fieldError = ref('') // 单字段校验（失焦）
const agreed = ref(false)

const router = useRouter()
const auth = useAuthStore()

/** 智能识别：输入内容自动切换手机/邮箱 Tab */
function detectTabFromValue(val: string) {
  const v = (val || '').trim()
  if (/@/.test(v)) tab.value = 'email'
  else if (/^\d+$/.test(v) || v.length >= 4) tab.value = 'phone'
}

watch(phone, (v) => { if (tab.value === 'phone') detectTabFromValue(v) })
watch(email, (v) => { if (tab.value === 'email') detectTabFromValue(v) })

const usernameComputed = () => (tab.value === 'phone' ? phone.value : email.value)

/** 友好错误文案 */
function friendlyMessage(raw: string, isLogin: boolean): string {
  const s = (raw || '').toLowerCase()
  if (s.includes('密码') || s.includes('password') || s.includes('错误') || s.includes('invalid')) return '密码好像不对，再试试吧~'
  if (s.includes('用户') || s.includes('账号') || s.includes('not found')) return '账号不存在，检查一下手机号/邮箱哦'
  if (s.includes('验证') || s.includes('格式')) return raw || '请检查输入格式'
  return isLogin ? '登录失败了，请稍后重试' : '注册失败了，请检查信息后重试'
}

/** 失焦校验 */
const phoneRegex = /^1[3-9]\d{9}$/
const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function onAccountBlur() {
  fieldError.value = ''
  const v = (tab.value === 'phone' ? phone.value : email.value).trim()
  if (!v) return
  if (tab.value === 'phone') {
    if (!/^\d+$/.test(v)) fieldError.value = '请输入有效的手机号'
    else if (v.length !== 11) fieldError.value = '手机号需要 11 位数字'
    else if (!phoneRegex.test(v)) fieldError.value = '手机号格式不正确'
  } else {
    if (!emailRegex.test(v)) fieldError.value = '请输入有效的邮箱地址'
  }
}

function onPasswordBlur() {
  if (fieldError.value && !password.value) return
  const v = password.value
  if (!v) return
  if (v.length < 6) fieldError.value = '密码至少 6 位哦'
  else fieldError.value = ''
}

const handleSubmit = async () => {
  fieldError.value = ''
  errorMsg.value = ''
  const username = usernameComputed().trim()
  if (!username || !password.value) {
    errorMsg.value = '请输入账号和密码'
    return
  }
  if (mode.value === 'register' && !agreed.value) {
    errorMsg.value = '请先阅读并同意用户协议与隐私政策'
    return
  }
  if (tab.value === 'phone' && !phoneRegex.test(username)) {
    fieldError.value = '请填写正确的 11 位手机号'
    return
  }
  if (tab.value === 'email' && !emailRegex.test(username)) {
    fieldError.value = '请填写正确的邮箱地址'
    return
  }
  if (password.value.length < 6) {
    fieldError.value = '密码至少 6 位哦'
    return
  }
  loading.value = true
  try {
    if (mode.value === 'login') {
      const data = await authApi.login({ username, password: password.value })
      auth.setAuth(data.token, data.userId)
      try {
        const me = await userApi.meDetail()
        auth.setProfile(me.nickname ?? null, me.reputationLevel ?? null)
      } catch {
        /* ignore */
      }
      const redirect = (router.currentRoute.value.query.redirect as string) || '/'
      router.push(redirect)
    } else {
      await authApi.register({
        email: tab.value === 'email' ? email.value : undefined,
        phone: tab.value === 'phone' ? phone.value : undefined,
        password: password.value,
      })
      mode.value = 'login'
      errorMsg.value = '注册成功，请登录'
    }
  } catch (e: any) {
    const raw = e.response?.data?.message || e.message || ''
    errorMsg.value = friendlyMessage(raw, mode.value === 'login')
  } finally {
    loading.value = false
  }
}

const guestExplore = () => router.push('/')

/** 根据时间问候（前端本地） */
const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 12) return '上午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const greetingLine = computed(() =>
  mode.value === 'login' ? `${greeting.value}，旅途达人来啦 👋` : '开启你的旅行计划'
)
</script>

<template>
  <div class="auth-page" role="main" aria-label="登录或注册">
    <section class="brand-panel" aria-hidden="true">
      <div class="brand-panel-bg" />
      <div class="brand-inner">
        <div class="brand-logo">TravelMatch</div>
        <div class="brand-content">
          <h1>找到路线，也找到<span class="highlight">同路的人</span></h1>
          <p class="tagline-desc">
            基于你的旅行偏好，一键规划专属行程，并遇见值得信赖的旅伴。
          </p>
          <p class="safety-row">
            <span class="safety-item"><span class="safety-icon" aria-hidden="true">✓</span> 实名认证</span>
            <span class="safety-item"><span class="safety-icon" aria-hidden="true">✓</span> 旅友互评</span>
          </p>
          <p class="social-proof">已有 10万+ 旅友通过 TravelMatch 找到完美旅伴</p>
        </div>
      </div>
    </section>

    <section class="auth-panel">
      <div class="auth-card card">
        <header class="auth-header">
          <h2>{{ greetingLine }}</h2>
          <p class="sub">登录后即可规划路线并寻找旅友</p>
        </header>

        <button
          type="button"
          class="guest-entry"
          aria-label="先随便看看路线，无需登录"
          @click="guestExplore"
        >
          先随便看看路线，无需登录
        </button>

        <div class="mode-toggle" role="tablist" aria-label="登录或注册">
          <button
            type="button"
            role="tab"
            class="mode-btn"
            :class="{ active: mode === 'login' }"
            :aria-selected="mode === 'login' ? 'true' : 'false'"
            @click="mode = 'login'"
          >
            登录
          </button>
          <button
            type="button"
            role="tab"
            class="mode-btn"
            :class="{ active: mode === 'register' }"
            :aria-selected="mode === 'register' ? 'true' : 'false'"
            @click="mode = 'register'"
          >
            注册
          </button>
        </div>

        <div class="tabs-wrap" role="tablist" aria-label="手机或邮箱登录">
          <div class="tabs">
            <button
              type="button"
              role="tab"
              class="tab"
              :class="{ active: tab === 'phone' }"
              :aria-selected="tab === 'phone' ? 'true' : 'false'"
              @click="tab = 'phone'"
            >
              手机号
            </button>
            <button
              type="button"
              role="tab"
              class="tab"
              :class="{ active: tab === 'email' }"
              :aria-selected="tab === 'email' ? 'true' : 'false'"
              @click="tab = 'email'"
            >
              邮箱
            </button>
          </div>
          <span class="tab-slider" :class="tab" aria-hidden="true" />
        </div>

        <form class="form" @submit.prevent="handleSubmit" novalidate>
          <label v-if="tab === 'phone'" class="form-group">
            <span class="form-label">手机号</span>
            <input
              v-model="phone"
              class="form-input"
              type="tel"
              inputmode="numeric"
              placeholder="请输入11位手机号"
              maxlength="11"
              autocomplete="tel"
              :aria-invalid="fieldError ? 'true' : undefined"
              :aria-describedby="fieldError ? 'field-err' : undefined"
              @blur="onAccountBlur"
            />
          </label>
          <label v-else class="form-group">
            <span class="form-label">邮箱</span>
            <input
              v-model="email"
              class="form-input"
              type="email"
              placeholder="请输入邮箱地址"
              autocomplete="email"
              :aria-invalid="fieldError ? 'true' : undefined"
              :aria-describedby="fieldError ? 'field-err' : undefined"
              @blur="onAccountBlur"
            />
          </label>
          <div class="form-group">
            <span class="form-label">密码</span>
            <div class="input-with-eye">
              <input
                v-model="password"
                class="form-input"
                :type="passwordVisible ? 'text' : 'password'"
                placeholder="至少 6 位密码"
                autocomplete="current-password"
                aria-describedby="forgot-pwd"
                @blur="onPasswordBlur"
              />
              <button
                type="button"
                class="eye-btn"
                :aria-label="passwordVisible ? '隐藏密码' : '显示密码'"
                @click="passwordVisible = !passwordVisible"
              >
                {{ passwordVisible ? '隐藏' : '显示' }}
              </button>
            </div>
            <div v-if="mode === 'login'" class="extras" id="forgot-pwd">
              <a href="#" class="forgot-link" @click.prevent>忘记密码？</a>
            </div>
          </div>

          <p v-if="fieldError || errorMsg" id="field-err" class="error" role="alert">
            {{ fieldError || errorMsg }}
          </p>

          <div v-if="mode === 'register'" class="agree-checkbox">
            <input
              id="agree"
              v-model="agreed"
              type="checkbox"
              class="agree-input"
              aria-describedby="agree-text"
            />
            <label for="agree" id="agree-text" class="agree-label">
              我已阅读并同意 <a href="#" class="link" @click.prevent>用户协议</a> 与 <a href="#" class="link" @click.prevent>隐私政策</a>
            </label>
          </div>

          <button
            class="btn primary full cta"
            type="submit"
            :disabled="loading"
            :aria-busy="loading"
            aria-live="polite"
          >
            <span v-if="loading" class="btn-loading">
              <span class="spinner" aria-hidden="true" /> {{ mode === 'login' ? '登录中...' : '注册中...' }}
            </span>
            <span v-else>{{ mode === 'login' ? '登录并继续' : '注册并开始规划' }}</span>
          </button>
        </form>

        <div class="divider">
          <span />
          <p>或使用以下方式登录</p>
          <span />
        </div>

        <div class="social-row">
          <button type="button" class="social-btn wechat" aria-label="微信登录" title="微信登录">
            <span class="social-icon">微</span>
            <span class="social-label">微信</span>
          </button>
          <button type="button" class="social-btn weibo" aria-label="微博登录" title="微博登录">
            <span class="social-icon">博</span>
            <span class="social-label">微博</span>
          </button>
          <button type="button" class="social-btn google" aria-label="Google 登录" title="Google 登录">
            <span class="social-icon">G</span>
            <span class="social-label">Google</span>
          </button>
        </div>

        <p class="agree-footer">
          <a href="#" class="link" @click.prevent>用户协议</a> · <a href="#" class="link" @click.prevent>隐私政策</a>
        </p>
      </div>
    </section>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 360px) 1fr;
}

.brand-panel {
  position: relative;
  overflow: hidden;
  max-width: 360px;
}

.brand-panel-bg {
  position: absolute;
  inset: 0;
  background-image: url('https://images.pexels.com/photos/346885/pexels-photo-346885.jpeg');
  background-size: cover;
  background-position: center;
  animation: brand-bg-drift 25s ease-in-out infinite;
}

.brand-panel-bg::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(135deg, rgba(15, 23, 42, 0.68) 0%, rgba(15, 23, 42, 0.35) 50%, rgba(13, 148, 136, 0.12) 100%);
  animation: overlay-pulse 12s ease-in-out infinite;
}

@keyframes brand-bg-drift {
  0%, 100% { transform: scale(1.02); }
  50% { transform: scale(1.06); }
}

@keyframes overlay-pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.92; }
}

.brand-inner {
  position: relative;
  z-index: 1;
  height: 100%;
  padding: 28px 24px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  color: #f9fafb;
}

.brand-logo {
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 0.1em;
}

.brand-content h1 {
  font-size: 24px;
  line-height: 1.4;
  margin-bottom: 14px;
  letter-spacing: 0.02em;
}

.brand-content h1 .highlight {
  color: #5eead4;
  font-weight: 700;
  text-shadow: 0 0 20px rgba(94, 234, 212, 0.35);
}

.brand-content .tagline-desc {
  max-width: 100%;
  line-height: 1.55;
  font-size: 13px;
  opacity: 0.95;
  margin-bottom: 16px;
}

.brand-content .safety-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 16px;
  margin: 0 0 14px;
  font-size: 12px;
  opacity: 0.92;
}

.brand-content .safety-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.brand-content .safety-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: rgba(94, 234, 212, 0.25);
  color: #5eead4;
  font-size: 11px;
  font-weight: 700;
}

.brand-content .social-proof {
  font-size: 12px;
  opacity: 0.88;
  line-height: 1.45;
}

.auth-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 20px;
  overflow-y: auto;
}

.auth-card {
  width: 100%;
  max-width: 380px;
  padding: 28px 32px 24px;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 8px 32px rgba(15, 23, 42, 0.06);
}

.auth-card :deep(.btn) {
  border-radius: 12px;
  border: none;
  cursor: pointer;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  transition: opacity 0.2s, box-shadow 0.2s;
}

.auth-card :deep(.btn.primary) {
  background: linear-gradient(135deg, #0d9488 0%, #0f766e 50%, #0d9488 100%);
  background-size: 200% 100%;
  color: #fff;
  box-shadow: 0 4px 16px rgba(13, 148, 136, 0.4);
}

.auth-card :deep(.btn.primary:hover:not(:disabled)) {
  opacity: 0.98;
  box-shadow: 0 6px 22px rgba(13, 148, 136, 0.45);
}

.auth-card :deep(.btn.primary:disabled) {
  opacity: 0.75;
  cursor: not-allowed;
}

.auth-card :deep(.btn.full) {
  width: 100%;
}

.auth-header {
  margin-bottom: 12px;
}

.auth-header h2 {
  margin: 0 0 4px;
  font-size: 20px;
  font-weight: 600;
  color: #111827;
}

.auth-header .sub {
  margin: 0;
  font-size: 13px;
  color: #6b7280;
}

.guest-entry {
  display: block;
  width: 100%;
  padding: 10px 16px;
  margin-bottom: 18px;
  font-size: 13px;
  color: #6b7280;
  background: transparent;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  cursor: pointer;
  transition: border-color 0.2s, background 0.2s, color 0.2s;
}

.guest-entry:hover {
  border-color: #0d9488;
  color: #0d9488;
  background: rgba(13, 148, 136, 0.06);
}

.mode-toggle {
  display: inline-flex;
  border-radius: 999px;
  background: #f3f4f6;
  padding: 2px;
  margin-bottom: 18px;
}

.mode-btn {
  border: none;
  background: transparent;
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  cursor: pointer;
  color: #6b7280;
}

.mode-btn.active {
  background: #ffffff;
  color: var(--color-primary, #0d9488);
  box-shadow: 0 2px 6px rgba(15, 23, 42, 0.1);
}

.tabs-wrap {
  position: relative;
  margin-bottom: 18px;
}

.tabs {
  display: flex;
  border-bottom: 1px solid #e5e7eb;
}

.tab {
  flex: 1;
  padding: 10px 0;
  border: none;
  background: transparent;
  font-size: 14px;
  cursor: pointer;
  color: #6b7280;
  position: relative;
  z-index: 1;
  transition: color 0.2s;
}

.tab.active {
  color: #0d9488;
  font-weight: 600;
}

.tab-slider {
  position: absolute;
  bottom: 0;
  left: 0;
  width: 50%;
  height: 3px;
  background: linear-gradient(90deg, #0d9488, #14b8a6);
  border-radius: 3px 3px 0 0;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

.tab-slider.email {
  transform: translateX(100%);
}

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 0;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-label {
  font-size: 13px;
  font-weight: 500;
  color: #374151;
}

.form-input {
  width: 100%;
  padding: 10px 12px;
  font-size: 14px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #fff;
  color: #111827;
  outline: none;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.form-input::placeholder {
  color: #9ca3af;
}

.form-input:hover {
  border-color: #d1d5db;
}

.form-input:focus {
  border-color: #0d9488;
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.15);
}

.form-input[aria-invalid="true"] {
  border-color: #f87171;
}

.form-input[aria-invalid="true"]:focus {
  box-shadow: 0 0 0 3px rgba(248, 113, 113, 0.2);
}

.input-with-eye {
  display: flex;
  align-items: center;
  gap: 0;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.input-with-eye:focus-within {
  border-color: #0d9488;
  box-shadow: 0 0 0 3px rgba(13, 148, 136, 0.15);
}

.input-with-eye .form-input {
  border: none;
  box-shadow: none;
  flex: 1;
}

.input-with-eye .form-input:focus {
  box-shadow: none;
}

.eye-btn {
  flex-shrink: 0;
  padding: 8px 12px;
  font-size: 12px;
  color: #6b7280;
  background: #f9fafb;
  border: none;
  border-left: 1px solid #e5e7eb;
  cursor: pointer;
  transition: color 0.2s, background 0.2s;
}

.eye-btn:hover {
  color: #0d9488;
  background: #f0fdfa;
}

.extras {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
}

.forgot-link {
  font-size: 13px;
  color: #0d9488;
  text-decoration: none;
}

.forgot-link:hover {
  text-decoration: underline;
}

.error {
  color: #dc2626;
  font-size: 13px;
  margin-top: -2px;
  margin-bottom: 0;
}

.agree-checkbox {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-top: 4px;
}

.agree-input {
  width: 18px;
  height: 18px;
  margin-top: 2px;
  accent-color: #0d9488;
  cursor: pointer;
}

.agree-label {
  font-size: 12px;
  color: #6b7280;
  line-height: 1.5;
  cursor: pointer;
}

.agree-label .link {
  color: #0d9488;
  text-decoration: none;
}

.agree-label .link:hover {
  text-decoration: underline;
}

.cta {
  margin-top: 8px;
  padding: 14px 20px;
  font-size: 15px;
  font-weight: 600;
}

.btn-loading {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 20px 0 14px;
}

.divider span {
  flex: 1;
  height: 1px;
  background: #e5e7eb;
}

.divider p {
  margin: 0;
  padding: 0 4px;
  color: #9ca3af;
  font-size: 12px;
}

.social-row {
  display: flex;
  justify-content: center;
  gap: 16px;
  margin-bottom: 18px;
}

.social-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 14px 10px;
  min-width: 72px;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
  background: #fff;
  font-size: 12px;
  cursor: pointer;
  color: #374151;
  transition: transform 0.2s, border-color 0.2s, box-shadow 0.2s;
}

.social-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.08);
}

.social-btn .social-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  font-weight: 600;
}

.social-btn.wechat .social-icon {
  background: #07c160;
  color: #fff;
}

.social-btn.weibo .social-icon {
  background: #e6162d;
  color: #fff;
}

.social-btn.google .social-icon {
  background: #4285f4;
  color: #fff;
}

.social-btn .social-label {
  font-size: 11px;
  color: #6b7280;
}

.agree-footer {
  margin: 0;
  padding-top: 12px;
  border-top: 1px solid #f3f4f6;
  text-align: center;
  line-height: 1.5;
  font-size: 12px;
  color: #9ca3af;
}

.agree-footer .link {
  color: #0d9488;
  text-decoration: none;
}

.agree-footer .link:hover {
  text-decoration: underline;
}

@media (max-width: 768px) {
  .auth-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    max-width: none;
    height: 120px;
    min-height: 120px;
  }

  .brand-panel-bg {
    animation-duration: 20s;
  }

  .brand-inner {
    padding: 14px 16px;
    justify-content: flex-end;
  }

  .brand-logo {
    font-size: 16px;
  }

  .brand-content h1 {
    font-size: 16px;
    margin-bottom: 0;
  }

  .brand-content .tagline-desc,
  .brand-content .safety-row,
  .brand-content .social-proof {
    display: none;
  }

  .auth-panel {
    padding: 20px 16px 28px;
    align-items: flex-start;
  }

  .auth-card {
    padding: 22px 20px 20px;
    max-width: 100%;
  }

  .guest-entry {
    padding: 12px 16px;
    font-size: 14px;
    min-height: 44px;
  }

  .form-input {
    min-height: 44px;
    padding: 12px 14px;
  }

  .social-row {
    flex-wrap: wrap;
    justify-content: center;
    gap: 12px;
  }

  .social-btn {
    min-width: 64px;
    padding: 10px 8px 8px;
  }
}
</style>

