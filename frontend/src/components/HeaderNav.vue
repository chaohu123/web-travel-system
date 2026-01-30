<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore, reputationLevelLabel } from '../store'
import { useMessageStore } from '../store/message'
import { Bell } from '@element-plus/icons-vue'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()
const messageStore = useMessageStore()

const isLoggedIn = computed(() => !!auth.token)
const displayName = computed(() => auth.nickname || '旅人')
const initial = computed(() => displayName.value.charAt(0).toUpperCase())
const creditLevel = computed(() => reputationLevelLabel(auth.reputationLevel))
const totalUnread = computed(() => messageStore.totalUnread)

let refreshTimer: number | null = null

async function refreshUnread() {
  if (auth.token) {
    try {
      await messageStore.fetchOverview()
    } catch {
      // 静默失败
    }
  }
}

function logout() {
  auth.clearAuth()
  router.push('/')
}

function goTo(path: string) {
  router.push(path)
}

function goMessageCenter() {
  if (!auth.token) {
    router.push({ name: 'login', query: { redirect: '/messages' } })
  } else {
    router.push('/messages')
  }
}

// 监听登录状态变化，登录后立即刷新未读数
watch(
  () => auth.token,
  (newToken) => {
    if (newToken) {
      refreshUnread()
    } else {
      messageStore.setTotalUnread(0)
    }
  },
  { immediate: true }
)

// 监听路由变化，从消息相关页面返回时刷新未读数
watch(
  () => route.path,
  (newPath, oldPath) => {
    // 从消息中心或聊天页离开时刷新未读数
    if (oldPath && (oldPath.startsWith('/messages') || oldPath.startsWith('/chat')) && auth.token) {
      refreshUnread()
    }
  }
)

onMounted(() => {
  refreshUnread()
  // 每30秒自动刷新未读数
  refreshTimer = window.setInterval(() => {
    refreshUnread()
  }, 30000)
})

onBeforeUnmount(() => {
  if (refreshTimer != null) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<template>
  <header class="header-nav sticky top-0 z-50 bg-white/90 backdrop-blur-md border-b border-slate-200/80 shadow-sm">
    <div class="nav-inner">
      <router-link to="/" class="logo">
        <span class="logo-text">TravelMatch</span>
      </router-link>

      <nav class="nav-links">
        <router-link to="/" class="nav-link" active-class="active" exact-active-class="active">首页</router-link>
        <router-link to="/routes" class="nav-link" active-class="active">路线规划</router-link>
        <router-link to="/companion" class="nav-link" active-class="active">结伴广场</router-link>
        <router-link to="/community" class="nav-link" active-class="active">社区</router-link>
      </nav>

      <div class="nav-right">
        <el-tooltip content="消息" placement="bottom">
          <button class="icon-button" type="button" @click="goMessageCenter">
            <el-badge
              :value="totalUnread > 99 ? '99+' : totalUnread"
              :hidden="!isLoggedIn || totalUnread === 0"
              class="msg-badge"
            >
              <el-icon class="msg-icon">
                <Bell />
              </el-icon>
            </el-badge>
          </button>
        </el-tooltip>
        <template v-if="isLoggedIn">
          <el-dropdown trigger="click" placement="bottom-end" @command="(cmd: string) => cmd === 'logout' ? logout() : goTo(cmd)">
            <div class="user-trigger">
              <el-avatar :size="32" class="user-avatar">
                {{ initial }}
              </el-avatar>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="/profile">个人主页</el-dropdown-item>
                <el-dropdown-item command="/" disabled>设置</el-dropdown-item>
                <el-dropdown-item command="/" disabled>消息</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <router-link to="/login" class="btn-ghost">登录</router-link>
          <router-link to="/register" class="btn-primary">注册</router-link>
        </template>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header-nav {
  min-height: 64px;
}

.nav-inner {
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 20px;
  height: 64px;
  display: flex;
  align-items: center;
  gap: 24px;
  justify-content: space-between;
}

.logo {
  flex-shrink: 0;
  text-decoration: none;
  color: #0f172a;
}

.logo:hover {
  color: #0d9488;
}

.logo-text {
  font-size: 1.25rem;
  font-weight: 700;
  background: linear-gradient(to right, #0d9488, #6366f1);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.nav-links {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 32px;
  font-size: 14px;
  font-weight: 500;
  color: #64748b;
}

.nav-link {
  color: inherit;
  text-decoration: none;
  transition: color 0.2s;
}

.nav-link:hover,
.nav-link.active {
  color: #0d9488;
}

.nav-right {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.icon-button {
  border: none;
  background: transparent;
  padding: 4px;
  border-radius: 9999px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background-color 0.2s, transform 0.1s;
}

.icon-button:hover {
  background-color: rgba(15, 23, 42, 0.06);
}

.icon-button:active {
  transform: scale(0.95);
}

.msg-icon {
  font-size: 20px;
  color: #64748b;
}

.icon-button:hover .msg-icon {
  color: #0d9488;
}

.msg-badge :deep(.el-badge__content) {
  background-color: #ef4444;
  box-shadow: 0 0 0 1px #fff;
  transition: all 0.2s ease;
}

.user-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 8px;
  border-radius: 9999px;
  background: #f1f5f9;
  cursor: pointer;
}

.user-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #14b8a6, #6366f1);
  color: #fff;
  font-weight: 600;
}

.user-name {
  font-size: 14px;
  font-weight: 500;
  color: #334155;
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-level {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 9999px;
  background: #fef3c7;
  color: #b45309;
  font-weight: 500;
}

.btn-ghost {
  padding: 8px 16px;
  font-size: 14px;
  color: #64748b;
  text-decoration: none;
}

.btn-ghost:hover {
  color: #0d9488;
}

.btn-primary {
  padding: 8px 20px;
  font-size: 14px;
  font-weight: 500;
  color: #fff;
  background: linear-gradient(to right, #0d9488, #6366f1);
  border-radius: 9999px;
  text-decoration: none;
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.3);
}

.btn-primary:hover {
  opacity: 0.9;
  box-shadow: 0 4px 12px rgba(13, 148, 136, 0.4);
}
</style>
