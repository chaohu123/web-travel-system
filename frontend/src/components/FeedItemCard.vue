<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElCard, ElImage, ElMessage, ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'
import HeartIcon from './HeartIcon.vue'
import { ChatDotRound, Star, StarFilled } from '@element-plus/icons-vue'
import type { FeedItem } from '../api'
import { commentsApi, interactionsApi } from '../api'
import { useAuthStore } from '../store'

const props = defineProps<{
  item: FeedItem
  parseImages: (item: FeedItem) => string[]
  formatTime: (t: string) => string
}>()

const emit = defineEmits<{ refresh: [] }>()

const router = useRouter()
const auth = useAuthStore()

const expanded = ref(false)
const liked = ref(false)
const favorited = ref(false)
const likeCount = ref(0)
const commentCount = ref(0)
const favoriteCount = ref(0)

const LINE_CLAMP = 3
const lineHeight = 1.7
const fontSize = 15

const images = computed(() => props.parseImages(props.item))

const needExpand = computed(() => {
  const c = props.item.content || ''
  const lines = c.split(/\n/).length
  return lines > LINE_CLAMP || c.length > 120
})

const displayContent = computed(() => {
  const c = props.item.content || ''
  if (!expanded.value && needExpand.value) {
    const lines = c.split(/\n/)
    if (lines.length > LINE_CLAMP) return lines.slice(0, LINE_CLAMP).join('\n') + '...'
    return c.length > 120 ? c.slice(0, 120) + '...' : c
  }
  return c
})

const imageLayout = computed(() => {
  const n = images.value.length
  if (n === 0) return 'none'
  if (n === 1) return 'single'
  if (n <= 3) return 'row'
  return 'grid'
})

function ensureLogin(cb: () => void) {
  if (!auth.token) {
    ElMessage.warning('请先登录后再操作')
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  cb()
}

async function loadSummary() {
  try {
    const s = await interactionsApi.summary('feed', props.item.id)
    likeCount.value = s.likeCount ?? 0
    favoriteCount.value = s.favoriteCount ?? 0
    liked.value = s.likedByCurrentUser ?? false
    favorited.value = s.favoritedByCurrentUser ?? false
  } catch {
    // ignore
  }
}

async function loadCommentCount() {
  try {
    const list = await commentsApi.list('feed', props.item.id)
    commentCount.value = list?.length ?? 0
  } catch {
    commentCount.value = 0
  }
}

function toggleLike() {
  ensureLogin(async () => {
    try {
      if (liked.value) {
        await interactionsApi.unlike('feed', props.item.id)
        likeCount.value = Math.max(0, likeCount.value - 1)
      } else {
        await interactionsApi.like('feed', props.item.id)
        likeCount.value += 1
      }
      liked.value = !liked.value
    } catch (e: any) {
      ElMessage.error(e.message || '操作失败')
    }
  })
}

function toggleFavorite() {
  ensureLogin(async () => {
    try {
      if (favorited.value) {
        await interactionsApi.unfavorite('feed', props.item.id)
        favoriteCount.value = Math.max(0, favoriteCount.value - 1)
      } else {
        await interactionsApi.favorite('feed', props.item.id)
        favoriteCount.value += 1
      }
      favorited.value = !favorited.value
    } catch (e: any) {
      ElMessage.error(e.message || '操作失败')
    }
  })
}

function handleMore(cmd: string) {
  ensureLogin(() => {
    if (cmd === 'report') ElMessage.info('已提交举报')
    if (cmd === 'block') ElMessage.info('已加入屏蔽列表')
  })
}

onMounted(() => {
  loadSummary()
  loadCommentCount()
})

watch(() => props.item.id, () => {
  loadSummary()
  loadCommentCount()
})
</script>

<template>
  <el-card class="feed-item-card" shadow="hover">
    <!-- 用户信息区 -->
    <div class="card-user">
      <div class="user-left">
        <div class="user-avatar">
          {{ (item.authorName || '旅友').charAt(0).toUpperCase() }}
        </div>
        <div class="user-info">
          <span class="user-name">{{ item.authorName || '旅友' }}</span>
          <span class="user-time">{{ formatTime(item.createdAt) }}</span>
        </div>
      </div>
      <div class="user-right">
        <el-dropdown trigger="click" @command="handleMore">
          <button type="button" class="btn-more" aria-label="更多">···</button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="report">举报</el-dropdown-item>
              <el-dropdown-item command="block">屏蔽</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 动态文本 -->
    <div class="card-body">
      <p class="card-text" :class="{ expanded }">{{ displayContent }}</p>
      <button v-if="needExpand" type="button" class="btn-expand" @click="expanded = !expanded">
        {{ expanded ? '收起' : '展开全文' }}
      </button>
    </div>

    <!-- 图片区：1 大图 / 2-3 横排 / 4+ 九宫格 -->
    <div v-if="images.length" class="card-images" :class="`layout-${imageLayout}`">
      <template v-if="imageLayout === 'single'">
        <el-image
          :src="images[0]"
          fit="cover"
          class="img-single"
          lazy
          :preview-src-list="images"
        />
      </template>
      <template v-else-if="imageLayout === 'row'">
        <el-image
          v-for="(url, i) in images"
          :key="i"
          :src="url"
          fit="cover"
          class="img-row"
          lazy
          :preview-src-list="images"
        />
      </template>
      <template v-else>
        <el-image
          v-for="(url, i) in images"
          :key="i"
          :src="url"
          fit="cover"
          class="img-grid"
          lazy
          :preview-src-list="images"
        />
      </template>
    </div>

    <!-- 互动操作区 -->
    <div class="card-actions">
      <button
        type="button"
        class="action-btn"
        :class="{ active: liked, liked: liked }"
        @click="toggleLike"
      >
        <HeartIcon class="icon" :filled="liked" />
        <span class="count">{{ likeCount > 0 ? likeCount : '点赞' }}</span>
      </button>
      <button type="button" class="action-btn">
        <el-icon class="icon"><ChatDotRound /></el-icon>
        <span class="count">{{ commentCount > 0 ? commentCount : '评论' }}</span>
      </button>
      <button
        type="button"
        class="action-btn"
        :class="{ active: favorited }"
        @click="toggleFavorite"
      >
        <el-icon class="icon"><component :is="favorited ? StarFilled : Star" /></el-icon>
        <span class="count">{{ favorited ? '已收藏' : '收藏' }}</span>
      </button>
    </div>
  </el-card>
</template>

<style scoped>
.feed-item-card {
  border-radius: 14px;
  border: none;
  overflow: hidden;
  transition: box-shadow 0.25s ease, transform 0.2s ease;
}

.feed-item-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.feed-item-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.card-user {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.user-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
}

.user-time {
  font-size: 12px;
  color: #909399;
}

.user-right {
  flex-shrink: 0;
}

.btn-more {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  color: #909399;
  font-size: 18px;
  cursor: pointer;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s, color 0.2s;
}

.btn-more:hover {
  background: #f5f7fa;
  color: #606266;
}

.card-body {
  margin-bottom: 12px;
}

.card-text {
  margin: 0;
  font-size: 15px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.card-text:not(.expanded) {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.btn-expand {
  margin-top: 6px;
  padding: 0;
  font-size: 14px;
  color: #0d9488;
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.2s;
}

.btn-expand:hover {
  color: #0f766e;
}

/* 图片布局 */
.card-images {
  margin-bottom: 14px;
  border-radius: 10px;
  overflow: hidden;
  background: #f5f7fa;
}

.layout-single .img-single {
  width: 100%;
  max-height: 400px;
  display: block;
  border-radius: 10px;
}

.layout-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.layout-row .img-row {
  flex: 1;
  min-width: 0;
  min-height: 160px;
  max-height: 220px;
  border-radius: 10px;
  transition: transform 0.25s ease;
}

.layout-row .img-row:hover {
  transform: scale(1.02);
}

.layout-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.layout-grid .img-grid {
  aspect-ratio: 1;
  border-radius: 10px;
  transition: transform 0.25s ease;
}

.layout-grid .img-grid:hover {
  transform: scale(1.03);
}

.card-images :deep(.el-image) {
  display: block;
  cursor: pointer;
}

.card-images :deep(.el-image__inner) {
  border-radius: 10px;
}

/* 互动区 */
.card-actions {
  display: flex;
  align-items: center;
  gap: 28px;
  padding-top: 12px;
  border-top: 1px solid #f0f2f5;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  font-size: 14px;
  color: #909399;
  background: none;
  border: none;
  cursor: pointer;
  transition: color 0.2s;
}

.action-btn .count {
  transition: opacity 0.2s;
}

.action-btn:hover,
.action-btn.active {
  color: #0d9488;
}

.action-btn.liked .icon {
  animation: like-bounce 0.35s ease;
}

@keyframes like-bounce {
  0% { transform: scale(1); }
  40% { transform: scale(1.3); }
  70% { transform: scale(0.95); }
  100% { transform: scale(1); }
}
</style>
