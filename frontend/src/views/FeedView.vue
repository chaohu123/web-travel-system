<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElCard, ElEmpty, ElSkeleton, ElMessage } from 'element-plus'
import FeedItemCard from '../components/FeedItemCard.vue'
import { feedsApi } from '../api'
import type { FeedItem } from '../api'
import { useAuthStore } from '../store'

const router = useRouter()
const auth = useAuthStore()

const content = ref('')
const imageUrlsJson = ref('')
const posting = ref(false)
const errorMsg = ref('')

const loadingList = ref(false)
const feeds = ref<FeedItem[]>([])

const canPublish = computed(() => content.value.trim().length > 0)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

async function fetchFeeds() {
  loadingList.value = true
  try {
    const list = await feedsApi.list()
    feeds.value = list || []
  } catch {
    feeds.value = []
  } finally {
    loadingList.value = false
  }
}

async function submit() {
  if (!content.value.trim()) {
    errorMsg.value = '请输入动态内容'
    return
  }
  posting.value = true
  errorMsg.value = ''
  try {
    let imageJson: string | undefined
    const raw = imageUrlsJson.value.trim()
    if (raw) {
      if (raw.startsWith('[')) {
        imageJson = raw
      } else {
        imageJson = JSON.stringify(raw.split(/[,，]/).map((s) => s.trim()).filter(Boolean))
      }
    }
    await feedsApi.create({
      content: content.value.trim(),
      imageUrlsJson: imageJson,
    })
    content.value = ''
    imageUrlsJson.value = ''
    await fetchFeeds()
    ElMessage.success('发布成功')
  } catch (e: any) {
    errorMsg.value = e.message || e.response?.data?.message || '发布失败'
    ElMessage.error(errorMsg.value)
  } finally {
    posting.value = false
  }
}

function parseImages(item: FeedItem): string[] {
  if (!item.imageUrlsJson) return []
  try {
    const arr = JSON.parse(item.imageUrlsJson)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function formatTime(t: string) {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  return d.toLocaleDateString('zh-CN')
}

onMounted(fetchFeeds)
</script>

<template>
  <div class="feed-page">
    <div class="feed-layout">
      <!-- 左侧主内容区 约 70% -->
      <main class="main-flow">
        <!-- 发布动态区 -->
        <el-card class="publish-card" shadow="never">
          <textarea
            ref="textareaRef"
            v-model="content"
            class="publish-textarea"
            rows="3"
            placeholder="记录此刻的旅行心情…"
            maxlength="2000"
          />
          <div v-if="content.length > 0" class="word-count">{{ content.length }}/2000</div>
          <div class="publish-toolbar">
            <div class="toolbar-left">
              <span class="upload-label">
                <span class="upload-icon">📷</span>
                <span class="upload-text">图片</span>
              </span>
              <input
                v-model="imageUrlsJson"
                type="text"
                class="upload-input-visible"
                placeholder='图片 URL，多个用逗号分隔'
              />
            </div>
            <div class="toolbar-right">
              <el-button
                type="primary"
                :disabled="!canPublish || posting"
                :loading="posting"
                class="publish-btn"
                @click="submit"
              >
                {{ posting ? '发布中…' : '发布' }}
              </el-button>
            </div>
          </div>
          <p v-if="errorMsg" class="publish-error">{{ errorMsg }}</p>
        </el-card>

        <!-- 动态列表：骨架屏 / 空状态 / 卡片列表 -->
        <div v-if="loadingList" class="skeleton-wrap">
          <el-card v-for="i in 4" :key="i" class="feed-card skeleton-card" shadow="never">
            <el-skeleton :rows="5" animated />
          </el-card>
        </div>

        <div v-else-if="feeds.length === 0" class="empty-wrap">
          <el-empty description="还没有动态，去发布第一条旅行分享吧">
            <template #image>
              <div class="empty-illus">✈️</div>
            </template>
            <el-button type="primary" @click="textareaRef?.focus()">
              去发布
            </el-button>
          </el-empty>
        </div>

        <div v-else class="feed-list">
          <FeedItemCard
            v-for="item in feeds"
            :key="item.id"
            :item="item"
            :parse-images="parseImages"
            :format-time="formatTime"
            @refresh="fetchFeeds"
          />
        </div>
      </main>

      <!-- 右侧辅助区 约 30%（PC） -->
      <aside class="sidebar">
        <el-card v-if="auth.token" class="sidebar-card user-card" shadow="never">
          <div class="user-inner" @click="router.push({ name: 'profile' })">
            <div class="user-avatar-wrap">
              {{ (auth.nickname || '我').charAt(0) }}
            </div>
            <div class="user-meta">
              <span class="user-name">{{ auth.nickname || '旅友' }}</span>
              <span class="user-desc">记录每一次出发</span>
            </div>
            <el-button type="primary" size="small" plain class="go-profile">个人主页</el-button>
          </div>
        </el-card>
        <el-card class="sidebar-card" shadow="never">
          <template #header>
            <span class="sidebar-title">推荐</span>
          </template>
          <div class="rec-links">
            <router-link to="/community" class="rec-link">推荐旅友</router-link>
            <router-link to="/notes" class="rec-link">热门游记</router-link>
            <router-link to="/companion" class="rec-link">结伴出行</router-link>
          </div>
        </el-card>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.feed-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 24px 20px 48px;
}

.feed-layout {
  max-width: 1200px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 20px;
  align-items: start;
}

@media (max-width: 1024px) {
  .feed-layout {
    grid-template-columns: 1fr;
  }
}

.main-flow {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 发布区 */
.publish-card {
  border-radius: 12px;
  padding: 16px;
  border: none;
}

.publish-card :deep(.el-card__body) {
  padding: 0;
}

.publish-textarea {
  width: 100%;
  min-height: 80px;
  padding: 12px 0;
  border: none;
  border-bottom: 1px solid #ebeef5;
  font-size: 15px;
  line-height: 1.6;
  color: #303133;
  resize: none;
  outline: none;
  transition: border-color 0.2s;
}

.publish-textarea::placeholder {
  color: #c0c4cc;
}

.publish-textarea:focus {
  border-bottom-color: #0d9488;
}

.word-count {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  text-align: right;
}

.publish-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 12px;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.upload-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #606266;
  font-size: 14px;
}

.upload-icon {
  font-size: 18px;
}

.upload-input-visible {
  margin-left: 8px;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 13px;
  color: #303133;
  width: 220px;
  max-width: 100%;
  transition: border-color 0.2s;
}

.upload-input-visible::placeholder {
  color: #c0c4cc;
}

.upload-input-visible:focus {
  outline: none;
  border-color: #0d9488;
}

.publish-btn {
  border-radius: 8px;
}

.publish-error {
  margin: 8px 0 0;
  font-size: 13px;
  color: #f56c6c;
}

/* 骨架屏 */
.skeleton-wrap {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.skeleton-card {
  border-radius: 14px;
}

.skeleton-card :deep(.el-card__body) {
  padding: 20px;
}

/* 空状态 */
.empty-wrap {
  background: #fff;
  border-radius: 14px;
  padding: 48px 24px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.empty-illus {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.9;
}

/* 动态列表 */
.feed-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 右侧栏 */
.sidebar {
  position: sticky;
  top: 88px;
}

.sidebar-card {
  border-radius: 12px;
  margin-bottom: 20px;
  border: none;
}

.sidebar-card :deep(.el-card__header) {
  padding: 14px 16px;
  font-weight: 600;
  color: #303133;
  font-size: 15px;
}

.sidebar-card :deep(.el-card__body) {
  padding: 16px;
}

.sidebar-title {
  font-size: 15px;
}

.user-inner {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.user-avatar-wrap {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-meta {
  text-align: center;
}

.user-name {
  display: block;
  font-weight: 600;
  color: #303133;
  font-size: 15px;
}

.user-desc {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.go-profile {
  width: 100%;
  border-radius: 8px;
}

.rec-links {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rec-link {
  display: block;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 14px;
  color: #606266;
  text-decoration: none;
  transition: background 0.2s, color 0.2s;
}

.rec-link:hover {
  background: #f5f7fa;
  color: #0d9488;
}
</style>
