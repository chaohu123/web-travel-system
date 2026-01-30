<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { CircleCheck, CircleCheckFilled, ChatDotRound, Star, StarFilled } from '@element-plus/icons-vue'
import type { FeedItem } from '../api'
import { commentsApi } from '../api'
import type { CommentItem } from '../api'
import { useAuthStore, reputationLevelLabel } from '../store'

const props = defineProps<{
  item: FeedItem
  typeTag?: string
}>()

const router = useRouter()
const auth = useAuthStore()
const expanded = ref(false)
const liked = ref(false)
const collected = ref(false)
const likeCount = ref(0)
const commentCount = ref(0)
const comments = ref<CommentItem[]>([])
const commentLoading = ref(false)
const showComments = ref(false)
const newComment = ref('')
const submittingComment = ref(false)

const previewLength = 120
const needExpand = computed(() => (props.item.content || '').length > previewLength)
const displayContent = computed(() => {
  const c = props.item.content || ''
  if (!expanded.value && needExpand.value) return c.slice(0, previewLength) + '...'
  return c
})

const images = computed(() => {
  if (!props.item.imageUrlsJson) return []
  try {
    const arr = JSON.parse(props.item.imageUrlsJson)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
})

const levelLabel = computed(() => reputationLevelLabel(null))

function toggleLike() {
  liked.value = !liked.value
  likeCount.value += liked.value ? 1 : -1
}

function toggleCollect() {
  collected.value = !collected.value
}

async function loadComments() {
  if (commentLoading.value) return
  commentLoading.value = true
  try {
    comments.value = await commentsApi.list('feed', props.item.id)
  } catch {
    comments.value = []
  } finally {
    commentLoading.value = false
  }
}

function toggleCommentPanel() {
  showComments.value = !showComments.value
  if (showComments.value && comments.value.length === 0) loadComments()
}

async function submitComment() {
  if (!newComment.value.trim()) return
  if (!auth.token) {
    ElMessage.warning('请先登录后再评论')
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
  submittingComment.value = true
  try {
    await commentsApi.create({
      targetType: 'feed',
      targetId: props.item.id,
      content: newComment.value.trim(),
      score: 5,
    })
    newComment.value = ''
    await loadComments()
    commentCount.value = comments.value.length
  } catch (e: any) {
    ElMessage.error(e.message || '评论失败')
  } finally {
    submittingComment.value = false
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
</script>

<template>
  <article class="feed-card">
    <div class="card-head">
      <el-avatar :size="40" class="author-avatar">
        {{ (item.authorName || 'U').charAt(0).toUpperCase() }}
      </el-avatar>
      <div class="head-info">
        <span class="author-name">{{ item.authorName || '旅友' }}</span>
        <el-tag v-if="typeTag" size="small" type="info" effect="plain" class="type-tag">{{ typeTag }}</el-tag>
        <span class="reputation">{{ levelLabel }}</span>
        <span class="time">{{ formatTime(item.createdAt) }}</span>
      </div>
    </div>
    <div class="card-body">
      <p class="content">{{ displayContent }}</p>
      <button v-if="needExpand" class="btn-more" @click="expanded = true">
        {{ expanded ? '收起' : '查看更多' }}
      </button>
    </div>
    <div v-if="images.length" class="card-images">
      <div
        v-for="(url, i) in images"
        :key="i"
        class="img-wrap"
        @click="() => {}"
      >
        <img :src="url" :alt="`图片${i + 1}`" loading="lazy" />
      </div>
    </div>
    <div class="card-actions">
      <button type="button" class="action-btn" :class="{ active: liked }" @click="toggleLike">
        <HeartIcon class="icon" :filled="liked" />
        <span>{{ likeCount > 0 ? likeCount : '点赞' }}</span>
      </button>
      <button type="button" class="action-btn" @click="toggleCommentPanel">
        <el-icon class="icon"><ChatDotRound /></el-icon>
        <span>评论 {{ commentCount > 0 ? commentCount : '' }}</span>
      </button>
      <button type="button" class="action-btn" :class="{ active: collected }" @click="toggleCollect">
        <el-icon class="icon"><component :is="collected ? StarFilled : Star" /></el-icon>
        <span>收藏</span>
      </button>
    </div>
    <div v-if="showComments" class="comment-panel">
      <div v-if="commentLoading" class="comment-loading">加载中...</div>
      <ul v-else class="comment-list">
        <li v-for="c in comments" :key="c.id" class="comment-item">
          <strong>{{ c.userName || '用户' }}</strong>: {{ c.content }}
          <span class="comment-time">{{ formatTime(c.createdAt) }}</span>
        </li>
      </ul>
      <div v-if="auth.token" class="comment-form">
        <el-input v-model="newComment" type="textarea" :rows="2" placeholder="写下你的评论..." />
        <el-button type="primary" size="small" :loading="submittingComment" @click="submitComment">
          发送
        </el-button>
      </div>
    </div>
  </article>
</template>

<style scoped>
.feed-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 20px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.feed-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.card-head {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.author-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-weight: 600;
}

.head-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.author-name {
  font-weight: 600;
  color: #1e293b;
}

.type-tag {
  font-size: 11px;
}

.reputation {
  font-size: 12px;
  color: #64748b;
}

.time {
  font-size: 12px;
  color: #94a3b8;
}

.card-body {
  margin-bottom: 12px;
}

.content {
  margin: 0;
  font-size: 15px;
  line-height: 1.6;
  color: #334155;
}

.btn-more {
  margin-top: 6px;
  padding: 0;
  font-size: 14px;
  color: #0d9488;
  background: none;
  border: none;
  cursor: pointer;
}

.card-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.img-wrap {
  width: 120px;
  height: 120px;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  background: #f1f5f9;
}

.img-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.card-actions {
  display: flex;
  gap: 24px;
  padding-top: 12px;
  border-top: 1px solid #f1f5f9;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0;
  font-size: 14px;
  color: #64748b;
  background: none;
  border: none;
  cursor: pointer;
}

.action-btn:hover,
.action-btn.active {
  color: #0d9488;
}

.comment-panel {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
}

.comment-loading {
  font-size: 14px;
  color: #94a3b8;
}

.comment-list {
  list-style: none;
  padding: 0;
  margin: 0 0 12px;
}

.comment-item {
  padding: 8px 0;
  font-size: 14px;
  color: #475569;
}

.comment-time {
  margin-left: 8px;
  font-size: 12px;
  color: #94a3b8;
}

.comment-form {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}
</style>
