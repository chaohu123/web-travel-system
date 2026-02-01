<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus'
import { CircleCheck, CircleCheckFilled, ChatDotRound, Star, StarFilled, Share } from '@element-plus/icons-vue'
import HeartIcon from './HeartIcon.vue'
import type { UnifiedDynamicItem } from '../api/types'
import type { CommentItem } from '../api'
import { commentsApi, interactionsApi, userApi } from '../api'
import { useAuthStore, reputationLevelLabel } from '../store'

const props = defineProps<{
  item: UnifiedDynamicItem
  /** 是否已删除/审核中，用于展示占位样式 */
  disabled?: boolean
}>()

const router = useRouter()
const auth = useAuthStore()

const liked = ref(false)
const favorited = ref(false)
const likeCount = ref(0)
const favoriteCount = ref(0)
const comments = ref<CommentItem[]>([])
const commentCount = ref(0)
const commentLoading = ref(false)
const showCommentPreview = ref(false)
const expanded = ref(false)

const targetType = computed(() => {
  const t = props.item.type
  return t === 'feed' ? 'feed' : t === 'note' ? 'note' : t === 'route' ? 'route' : 'companion'
})

const targetId = computed(() => props.item.id)

const badgeLabel = computed(() =>
  reputationLevelLabel(props.item.reputationLevel ?? props.item.companion?.creatorReputationLevel ?? undefined)
)

/** 结伴状态展示：open/OPEN → 报名中，后端可能返回小写 */
const companionStatusLabel = computed(() => {
  const s = (props.item.companion?.status ?? '').trim().toLowerCase()
  if (s === 'open') return '报名中'
  if (s === 'locked') return '已锁定'
  if (s === 'closed') return '已结束'
  return s || '结伴'
})
const companionStatusType = computed(() => {
  const s = (props.item.companion?.status ?? '').trim().toLowerCase()
  if (s === 'open') return 'success'
  if (s === 'closed') return 'info'
  return 'info'
})

const previewComments = computed(() => comments.value.slice(0, 2))

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
    const s = await interactionsApi.summary(targetType.value, targetId.value)
    likeCount.value = s.likeCount ?? 0
    favoriteCount.value = s.favoriteCount ?? 0
    liked.value = s.likedByCurrentUser ?? false
    favorited.value = s.favoritedByCurrentUser ?? false
  } catch {
    // 未登录或接口不支持时忽略
  }
}

async function loadComments() {
  commentLoading.value = true
  try {
    const list = await commentsApi.list(targetType.value, targetId.value)
    comments.value = list || []
    commentCount.value = comments.value.length
  } catch {
    comments.value = []
    commentCount.value = 0
  } finally {
    commentLoading.value = false
  }
}

function toggleLike() {
  ensureLogin(async () => {
    try {
      if (liked.value) {
        await interactionsApi.unlike(targetType.value, targetId.value)
        likeCount.value = Math.max(0, likeCount.value - 1)
      } else {
        await interactionsApi.like(targetType.value, targetId.value)
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
        await interactionsApi.unfavorite(targetType.value, targetId.value)
        favoriteCount.value = Math.max(0, favoriteCount.value - 1)
      } else {
        await interactionsApi.favorite(targetType.value, targetId.value)
        favoriteCount.value += 1
      }
      favorited.value = !favorited.value
    } catch (e: any) {
      ElMessage.error(e.message || '操作失败')
    }
  })
}

function doShare() {
  ensureLogin(() => {
    const route = detailRoute.value
    const href = router.resolve(route).href
    const url = typeof window !== 'undefined' ? window.location.origin + href : ''
    if (navigator.clipboard?.writeText) {
      navigator.clipboard.writeText(url).then(() => ElMessage.success('链接已复制'))
    } else {
      ElMessage.info('请手动复制链接分享')
    }
  })
}

const detailRoute = computed(() => {
  switch (props.item.type) {
    case 'note':
      return { name: 'note-detail', params: { id: String(props.item.id) } }
    case 'route':
      return { name: 'route-detail', params: { id: String(props.item.id) } }
    case 'companion':
      return { name: 'companion-detail', params: { id: String(props.item.id) } }
    default:
      return { path: '/community' }
  }
})

const feedImages = computed(() => {
  if (props.item.type !== 'feed' || !props.item.feed?.imageUrlsJson) return []
  try {
    const arr = JSON.parse(props.item.feed.imageUrlsJson)
    return Array.isArray(arr) ? arr.slice(0, 3) : []
  } catch {
    return []
  }
})

function goDetail() {
  if (props.disabled) return
  switch (props.item.type) {
    case 'note':
      router.push({ name: 'note-detail', params: { id: String(props.item.id) } })
      break
    case 'route':
      router.push({ name: 'route-detail', params: { id: String(props.item.id) } })
      break
    case 'companion':
      router.push({ name: 'companion-detail', params: { id: String(props.item.id) } })
      break
    case 'feed':
      break
  }
}

function toggleCommentPreview() {
  showCommentPreview.value = !showCommentPreview.value
  if (showCommentPreview.value && comments.value.length === 0) loadComments()
}

function goUserProfile() {
  if (props.item.authorId) {
    router.push({ name: 'user-profile', params: { id: String(props.item.authorId) } })
  }
}

const followLoading = ref(false)
const isFollowed = computed(() => {
  const aid = props.item.authorId
  if (aid == null) return false
  return auth.followedSessionIds.value.has(aid)
})

async function handleFollow() {
  const aid = props.item.authorId
  if (aid == null) return
  ensureLogin(async () => {
    followLoading.value = true
    try {
      if (isFollowed.value) {
        await userApi.unfollow(aid)
        auth.removeFollowedSession(aid)
        ElMessage.success('已取消关注')
      } else {
        await userApi.follow(aid)
        auth.addFollowedSession(aid)
        ElMessage.success('已关注')
      }
    } catch (e: any) {
      ElMessage.error(e?.message ?? (isFollowed.value ? '取消关注失败' : '关注失败'))
    } finally {
      followLoading.value = false
    }
  })
}

function handleReport() {
  ensureLogin(() => ElMessage.info('已提交举报'))
}

function handleBlock() {
  ensureLogin(() => ElMessage.info('已加入屏蔽列表'))
}

function handleMenuCommand(cmd: string) {
  if (cmd === 'report') handleReport()
  else if (cmd === 'block') handleBlock()
}

onMounted(loadSummary)
watch(
  () => props.item.id,
  () => {
    loadSummary()
    comments.value = []
    commentCount.value = 0
  }
)
</script>

<template>
  <article
    class="dynamic-card"
    :class="{ disabled: disabled }"
  >
    <!-- 用户信息区 -->
    <div class="card-user">
      <el-avatar
        :size="44"
        class="user-avatar"
        @click="goUserProfile"
      >
        <img v-if="item.authorAvatar" :src="item.authorAvatar" :alt="item.authorName" />
        <span v-else>{{ (item.authorName || item.feed?.authorName || item.note?.authorName || item.companion?.creatorNickname || '旅友').charAt(0) }}</span>
      </el-avatar>
      <div class="user-info">
        <span class="user-name" @click="goUserProfile">{{ item.authorName || item.feed?.authorName || item.note?.authorName || item.companion?.creatorNickname || '旅友' }}</span>
        <el-tag v-if="badgeLabel" size="small" type="warning" effect="plain" class="badge">{{ badgeLabel }}</el-tag>
        <span class="user-time">{{ formatTime(item.createdAt) }}</span>
      </div>
      <div class="user-actions">
        <el-button
          v-if="item.authorId != null && auth.userId != null && auth.userId !== item.authorId"
          size="small"
          :type="isFollowed ? 'default' : 'primary'"
          plain
          :loading="followLoading"
          @click="handleFollow"
        >
          {{ isFollowed ? '已关注' : '关注' }}
        </el-button>
        <el-dropdown trigger="click" @command="handleMenuCommand">
          <el-button size="small" text>更多</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="report">举报</el-dropdown-item>
              <el-dropdown-item command="block">屏蔽</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 动态内容展示区（按类型） -->
    <div v-if="!disabled" class="card-body" @click="goDetail">
      <!-- 游记：左文右图小图展示 -->
      <template v-if="item.type === 'note' && item.note">
        <div class="body-note-row">
          <div class="body-note-text">
            <h3 class="body-title">{{ item.note.title }}</h3>
            <p class="body-summary">{{ item.note.destination || '旅行游记' }}</p>
            <el-button type="primary" text class="btn-full">查看全文</el-button>
          </div>
          <div v-if="item.note.coverImage" class="body-note-thumb">
            <el-image
              :src="item.note.coverImage"
              fit="cover"
              lazy
              :preview-src-list="[item.note.coverImage]"
            />
          </div>
        </div>
      </template>

      <!-- 路线 -->
      <template v-else-if="item.type === 'route' && item.route">
        <h3 class="body-title">{{ item.route.title }}</h3>
        <p class="body-meta">{{ item.route.destination }} · {{ item.route.days?.length ?? 0 }} 天</p>
        <div v-if="item.route.days?.length" class="body-tags">
          <el-tag v-for="(d, i) in (item.route.days?.slice(0, 4) || [])" :key="i" size="small" effect="plain">第{{ d.dayIndex }}天</el-tag>
        </div>
        <el-button type="primary" text class="btn-full">查看路线</el-button>
      </template>

      <!-- 打卡 -->
      <template v-else-if="item.type === 'feed' && item.feed">
        <p class="body-text">{{ item.feed.content }}</p>
        <div v-if="feedImages.length" class="body-images body-images--multi">
          <el-image
            v-for="(url, i) in feedImages"
            :key="i"
            :src="url"
            fit="cover"
            lazy
            class="body-img"
            :preview-src-list="feedImages"
          />
        </div>
      </template>

      <!-- 结伴 -->
      <template v-else-if="item.type === 'companion' && item.companion">
        <h3 class="body-title">{{ item.companion.destination }} 结伴</h3>
        <p class="body-meta">出发 {{ item.companion.startDate }} · 人数 {{ item.companion.minPeople ?? '?' }}-{{ item.companion.maxPeople ?? '?' }} 人</p>
        <p v-if="item.companion.budgetMin != null || item.companion.budgetMax != null" class="body-meta">
          预算 ¥{{ item.companion.budgetMin ?? 0 }} - ¥{{ item.companion.budgetMax ?? 0 }}
        </p>
        <el-tag size="small" :type="companionStatusType" effect="plain">{{ companionStatusLabel }}</el-tag>
        <el-button type="primary" text class="btn-full">查看详情</el-button>
      </template>
    </div>

    <div v-else class="card-body card-body--disabled">
      <p class="text-subtle">该内容不可用或正在审核中</p>
    </div>

    <!-- 互动操作区 -->
    <div class="card-actions">
      <button type="button" class="action-btn" :class="{ active: liked, liked: liked }" @click="toggleLike">
        <HeartIcon class="icon" :filled="liked" />
        <span>{{ likeCount > 0 ? likeCount : '点赞' }}</span>
      </button>
      <button type="button" class="action-btn" @click="toggleCommentPreview">
        <el-icon class="icon"><ChatDotRound /></el-icon>
        <span>评论 {{ commentCount > 0 ? commentCount : '' }}</span>
      </button>
      <button type="button" class="action-btn" :class="{ active: favorited }" @click="toggleFavorite">
        <el-icon class="icon"><component :is="favorited ? StarFilled : Star" /></el-icon>
        <span>收藏</span>
      </button>
      <button type="button" class="action-btn" @click="doShare">
        <el-icon class="icon"><Share /></el-icon>
        <span>分享</span>
      </button>
    </div>

    <!-- 评论快捷展开 -->
    <div v-if="showCommentPreview" class="card-comments">
      <div v-if="commentLoading" class="comment-loading">加载中...</div>
      <template v-else>
        <div v-for="c in previewComments" :key="c.id" class="comment-item">
          <strong>{{ c.userName }}</strong>: {{ c.content }}
          <span class="comment-time">{{ formatTime(c.createdAt) }}</span>
        </div>
        <el-button v-if="item.type === 'note' || item.type === 'route' || item.type === 'companion'" type="primary" text size="small" @click="goDetail">
          查看全部评论
        </el-button>
      </template>
    </div>
  </article>
</template>

<style scoped>
.dynamic-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 20px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.dynamic-card:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.dynamic-card.disabled .card-body {
  opacity: 0.6;
}

.card-user {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.user-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}

.user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.user-name {
  font-weight: 600;
  color: #1e293b;
  cursor: pointer;
}

.badge {
  font-size: 11px;
}

.user-time {
  font-size: 12px;
  color: #94a3b8;
}

.user-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.card-body {
  margin-bottom: 16px;
  cursor: pointer;
}

.card-body--disabled {
  cursor: default;
}

.body-title {
  margin: 0 0 8px;
  font-size: 17px;
  font-weight: 600;
  color: #1e293b;
}

.body-summary,
.body-meta,
.body-text {
  margin: 0 0 12px;
  font-size: 14px;
  color: #64748b;
  line-height: 1.5;
}

.body-text {
  color: #334155;
  white-space: pre-wrap;
}

.body-note-row {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  margin-bottom: 4px;
}

.body-note-text {
  flex: 1;
  min-width: 0;
}

.body-note-thumb {
  flex-shrink: 0;
  width: 60px;
  height: 45px;
  border-radius: 10px;
  overflow: hidden;
  background: #f1f5f9;
}

.body-note-thumb :deep(.el-image) {
  width: 100%;
  height: 100%;
  display: block;
}

.body-note-thumb :deep(.el-image__inner) {
  object-fit: cover;
}

.body-images {
  margin-bottom: 12px;
  border-radius: 12px;
  overflow: hidden;
  background: #f1f5f9;
}

.body-images--single :deep(.el-image) {
  width: 100%;
  max-height: 280px;
  display: block;
}

.body-images--multi {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.body-img {
  width: 120px;
  height: 120px;
  border-radius: 8px;
}

.body-tags {
  margin-bottom: 12px;
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.btn-full {
  padding: 0;
  font-size: 14px;
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
  transition: color 0.2s;
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

.card-comments {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f1f5f9;
}

.comment-loading {
  font-size: 14px;
  color: #94a3b8;
}

.comment-item {
  padding: 6px 0;
  font-size: 14px;
  color: #475569;
}

.comment-time {
  margin-left: 8px;
  font-size: 12px;
  color: #94a3b8;
}

.text-subtle {
  color: #94a3b8;
  font-size: 14px;
}
</style>
