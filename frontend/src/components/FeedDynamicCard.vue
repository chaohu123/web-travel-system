<script setup lang="ts">
import { ref, computed, nextTick, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElCard, ElAvatar, ElImage, ElDropdown, ElDropdownMenu, ElDropdownItem, ElMessage, ElInput, ElButton } from 'element-plus'
import { ChatDotRound, Star, StarFilled, Share } from '@element-plus/icons-vue'
import HeartIcon from './HeartIcon.vue'
import type { UnifiedDynamicItem } from '../api/types'
import type { CommentItem } from '../api'
import { commentsApi, interactionsApi, userApi } from '../api'
import { useAuthStore, reputationLevelLabel } from '../store'

const props = defineProps<{
  item: UnifiedDynamicItem
  disabled?: boolean
}>()

const emit = defineEmits<{
  share: [payload: { url: string; title: string; image?: string; type: 'note' | 'route' | 'companion' | 'feed' }]
}>()

const router = useRouter()
const auth = useAuthStore()

const liked = ref(false)
const favorited = ref(false)
const likeCount = ref(0)
const favoriteCount = ref(0)
const commentCount = ref(0)
const expanded = ref(false)
const showComments = ref(false)
const comments = ref<CommentItem[]>([])
const commentLoading = ref(false)
const newComment = ref('')
const submittingComment = ref(false)
const commentInputRef = ref<{ focus: () => void } | null>(null)

const LINE_CLAMP = 3

const targetType = computed(() =>
  props.item.type === 'feed' ? 'feed' : props.item.type === 'note' ? 'note' : props.item.type === 'route' ? 'route' : 'companion'
)
const targetId = computed(() => props.item.id)

const authorName = computed(
  () =>
    props.item.authorName ||
    props.item.feed?.authorName ||
    props.item.note?.authorName ||
    props.item.companion?.creatorNickname ||
    '旅友'
)
const badgeLabel = computed(() =>
  reputationLevelLabel(
    props.item.reputationLevel ?? props.item.companion?.creatorReputationLevel ?? undefined
  )
)

/** 结伴状态展示：open/OPEN → 报名中，后端可能返回小写 */
const companionStatusLabel = computed(() => {
  const s = (props.item.companion?.status ?? '').trim().toLowerCase()
  if (s === 'open') return '报名中'
  if (s === 'locked') return '已锁定'
  if (s === 'closed') return '已结束'
  return s || '结伴'
})
const companionStatusTagType = computed(() => {
  const s = (props.item.companion?.status ?? '').trim().toLowerCase()
  if (s === 'open') return 'success'
  if (s === 'closed') return 'info'
  return 'info'
})

const feedContent = computed(() => props.item.feed?.content ?? '')
const needExpand = computed(() => {
  const c = feedContent.value
  if (!c) return false
  const lines = c.split(/\n/).length
  return lines > LINE_CLAMP || c.length > 150
})
const displayContent = computed(() => {
  const c = feedContent.value
  if (!expanded.value && needExpand.value) {
    const lines = c.split(/\n/)
    if (lines.length > LINE_CLAMP) return lines.slice(0, LINE_CLAMP).join('\n') + '...'
    return c.length > 150 ? c.slice(0, 150) + '...' : c
  }
  return c
})

const feedImages = computed(() => {
  if (props.item.type !== 'feed' || !props.item.feed?.imageUrlsJson) return []
  try {
    const arr = JSON.parse(props.item.feed.imageUrlsJson)
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
})
const imageLayout = computed(() => {
  const n = feedImages.value.length
  if (n === 0) return 'none'
  if (n === 1) return 'single'
  if (n <= 3) return 'row'
  return 'grid'
})

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
    // ignore
  }
}

async function loadCommentCount() {
  try {
    const list = await commentsApi.list(targetType.value, targetId.value)
    commentCount.value = list?.length ?? 0
  } catch {
    commentCount.value = 0
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
  } finally {
    commentLoading.value = false
  }
}

function openCommentPanel() {
  showComments.value = true
  loadComments()
  nextTick(() => commentInputRef.value?.focus())
}

async function submitComment() {
  const content = newComment.value.trim()
  if (!content) return
  ensureLogin(async () => {
    submittingComment.value = true
    try {
      await commentsApi.create({
        targetType: targetType.value,
        targetId: targetId.value,
        content,
        score: 5,
      })
      newComment.value = ''
      await loadComments()
      commentCount.value = comments.value.length
    } catch (e: unknown) {
      ElMessage.error((e as { message?: string })?.message || '评论失败')
    } finally {
      submittingComment.value = false
    }
  })
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
    } catch (e: unknown) {
      ElMessage.error((e as { message?: string })?.message || '操作失败')
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
    } catch (e: unknown) {
      ElMessage.error((e as { message?: string })?.message || '操作失败')
    }
  })
}

function doShare() {
  ensureLogin(() => {
    const route = detailRoute.value
    const href = router.resolve(route).href
    const url = typeof window !== 'undefined' ? window.location.origin + href : ''
    const title = (props.item.feed?.content?.slice(0, 50) || props.item.note?.title || props.item.route?.title || props.item.companion?.destination || '动态') + (props.item.feed?.content && props.item.feed.content.length > 50 ? '…' : '')
    let image: string | undefined
    if (props.item.feed?.imageUrlsJson) {
      try {
        const arr = JSON.parse(props.item.feed.imageUrlsJson)
        if (Array.isArray(arr) && arr[0]) image = arr[0]
      } catch {}
    } else if (props.item.note?.coverImage) {
      image = props.item.note.coverImage
    }
    emit('share', { url, title, image, type: props.item.type })
  })
}

function handleMore(cmd: string) {
  ensureLogin(() => {
    if (cmd === 'report') ElMessage.info('已提交举报')
    if (cmd === 'disinterest') ElMessage.info('已标记不感兴趣')
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
    case 'feed':
      return { name: 'feed', query: { scrollTo: `feed-${props.item.id}` } }
    default:
      return { name: 'feed' }
  }
})

function goDetail() {
  if (props.disabled) return
  if (props.item.type === 'feed') return
  router.push(detailRoute.value)
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
  if (!auth.token) {
    ElMessage.warning('请先登录后再操作')
    router.push({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    return
  }
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
}

onMounted(() => {
  loadSummary()
  loadCommentCount()
})
watch(
  () => props.item.id,
  () => {
    loadSummary()
    loadCommentCount()
    comments.value = []
  }
)
</script>

<template>
  <el-card class="feed-dynamic-card" shadow="hover">
    <!-- 用户信息区（Card Header） -->
    <div class="card-user">
      <div class="user-left">
        <el-avatar :size="40" class="user-avatar" @click="goUserProfile">
          <img v-if="item.authorAvatar" :src="item.authorAvatar" :alt="authorName" />
          <span v-else>{{ authorName.charAt(0).toUpperCase() }}</span>
        </el-avatar>
        <div class="user-meta">
          <span class="user-name" @click="goUserProfile">{{ authorName }}</span>
          <span v-if="badgeLabel" class="user-badge">{{ badgeLabel }}</span>
          <span class="user-time">{{ formatTime(item.createdAt) }}</span>
        </div>
      </div>
      <div class="user-right">
        <el-button
          v-if="item.authorId != null && auth.userId != null && auth.userId !== item.authorId"
          size="small"
          :type="isFollowed ? 'default' : 'primary'"
          plain
          :loading="followLoading"
          class="btn-follow"
          @click="handleFollow"
        >
          {{ isFollowed ? '已关注' : '关注' }}
        </el-button>
        <el-dropdown trigger="click" @command="handleMore">
          <button type="button" class="btn-more" aria-label="更多">···</button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="report">举报</el-dropdown-item>
              <el-dropdown-item command="disinterest">不感兴趣</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <!-- 动态内容区（按类型差异化） -->
    <div v-if="!disabled" class="card-body" @click="goDetail">
      <!-- 游记 -->
      <template v-if="item.type === 'note' && item.note">
        <h3 class="body-title">{{ item.note.title }}</h3>
        <p class="body-summary">{{ item.note.destination || '' }}</p>
        <div v-if="item.note.coverImage" class="media-wrap media-single">
          <el-image
            :src="item.note.coverImage"
            fit="cover"
            lazy
            class="media-img"
            :preview-src-list="[item.note.coverImage]"
          />
        </div>
        <el-button type="primary" text class="btn-link">查看全文</el-button>
      </template>

      <!-- 路线 -->
      <template v-else-if="item.type === 'route' && item.route">
        <div class="route-embed">
          <h3 class="body-title">{{ item.route.title }}</h3>
          <p class="body-meta">{{ item.route.destination }} · {{ item.route.days?.length ?? 0 }} 天</p>
          <div v-if="item.route.days?.length" class="route-days">
            <span v-for="(d, i) in item.route.days.slice(0, 3)" :key="i" class="day-tag">第{{ d.dayIndex }}天</span>
          </div>
        </div>
        <el-button type="primary" text class="btn-link">查看路线</el-button>
      </template>

      <!-- 打卡 -->
      <template v-else-if="item.type === 'feed' && item.feed">
        <p class="body-text" :class="{ expanded }">{{ displayContent }}</p>
        <button v-if="needExpand" type="button" class="btn-expand" @click.stop="expanded = !expanded">
          {{ expanded ? '收起' : '展开全文' }}
        </button>
        <div v-if="feedImages.length" class="media-wrap" :class="`media-${imageLayout}`">
          <template v-if="imageLayout === 'single'">
            <el-image
              :src="feedImages[0]"
              fit="cover"
              class="media-img media-single-img"
              lazy
              :preview-src-list="feedImages"
            />
          </template>
          <template v-else-if="imageLayout === 'row'">
            <el-image
              v-for="(url, i) in feedImages"
              :key="i"
              :src="url"
              fit="cover"
              class="media-img media-row-img"
              lazy
              :preview-src-list="feedImages"
            />
          </template>
          <template v-else-if="imageLayout === 'grid'">
            <el-image
              v-for="(url, i) in feedImages"
              :key="i"
              :src="url"
              fit="cover"
              class="media-img media-grid-img"
              lazy
              :preview-src-list="feedImages"
            />
          </template>
        </div>
      </template>

      <!-- 结伴 -->
      <template v-else-if="item.type === 'companion' && item.companion">
        <div class="companion-embed">
          <h3 class="body-title">{{ item.companion.destination }} 结伴</h3>
          <p class="body-meta">出发 {{ item.companion.startDate }} · {{ item.companion.minPeople ?? '?' }}-{{ item.companion.maxPeople ?? '?' }} 人</p>
          <el-tag size="small" :type="companionStatusTagType" effect="plain" class="status-tag">{{ companionStatusLabel }}</el-tag>
        </div>
        <el-button type="primary" text class="btn-link">查看详情</el-button>
      </template>
    </div>

    <div v-else class="card-body card-body--disabled">
      <p class="text-subtle">该内容不可用或正在审核中</p>
    </div>

    <!-- 互动操作区（Card Footer） -->
    <div class="card-actions" @click.stop>
      <button
        type="button"
        class="action-btn"
        :class="{ active: liked, liked: liked }"
        @click="toggleLike"
      >
        <HeartIcon class="icon" :filled="liked" />
        <span class="count">{{ likeCount > 0 ? likeCount : '点赞' }}</span>
      </button>
      <button type="button" class="action-btn" @click="openCommentPanel">
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
      <button type="button" class="action-btn" @click="doShare">
        <el-icon class="icon"><Share /></el-icon>
        <span class="count">分享</span>
      </button>
    </div>

    <!-- 评论展开（朋友圈式：评论列表 + 输入框） -->
    <div v-if="showComments" class="card-comments" @click.stop>
      <div v-if="commentLoading" class="comment-loading">加载中...</div>
      <template v-else>
        <div v-for="c in comments" :key="c.id" class="comment-item">
          <strong>{{ c.userName }}</strong>: {{ c.content }}
          <span class="comment-time">{{ formatTime(c.createdAt) }}</span>
        </div>
      </template>
      <div class="comment-input-row">
        <el-input
          ref="commentInputRef"
          v-model="newComment"
          type="textarea"
          :rows="2"
          placeholder="写评论..."
          maxlength="500"
          show-word-limit
          class="comment-input"
          @keydown.ctrl.enter="submitComment"
        />
        <el-button
          type="primary"
          size="small"
          :loading="submittingComment"
          :disabled="!newComment.trim()"
          class="comment-send-btn"
          @click="submitComment"
        >
          发送
        </el-button>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.feed-dynamic-card {
  border-radius: var(--feed-card-radius, 14px);
  border: none;
  overflow: hidden;
  transition: box-shadow 0.25s ease;
}

.feed-dynamic-card:hover {
  box-shadow: var(--feed-shadow-hover, 0 8px 24px rgba(0, 0, 0, 0.08));
}

.feed-dynamic-card :deep(.el-card__body) {
  padding: var(--feed-card-padding, 16px) 20px;
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
  min-width: 0;
}

.user-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-weight: 600;
  cursor: pointer;
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  min-width: 0;
}

.user-name {
  font-weight: 600;
  font-size: 15px;
  color: #303133;
  cursor: pointer;
}

.user-badge {
  font-size: 12px;
  color: #909399;
}

.user-time {
  font-size: 12px;
  color: #909399;
}

.user-right {
  display: flex;
  align-items: center;
  gap: 8px;
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
  cursor: pointer;
}

.card-body--disabled {
  cursor: default;
  opacity: 0.6;
}

.body-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.body-summary,
.body-meta {
  margin: 0 0 8px;
  font-size: 14px;
  color: #606266;
  line-height: 1.5;
}

.body-text {
  margin: 0 0 8px;
  font-size: var(--feed-content-font-size, 15px);
  line-height: var(--feed-content-line-height, 1.7);
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.body-text:not(.expanded) {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.btn-expand {
  margin-top: 4px;
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

.btn-link {
  padding: 0;
  font-size: 14px;
  margin-top: 4px;
}

.media-wrap {
  margin-top: 12px;
  margin-bottom: 8px;
  border-radius: var(--feed-img-radius, 10px);
  overflow: hidden;
  background: #f5f7fa;
}

.media-single .media-single-img {
  width: 100%;
  max-height: 400px;
  display: block;
  border-radius: var(--feed-img-radius, 10px);
}

.media-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.media-row .media-row-img {
  flex: 1;
  min-width: 0;
  min-height: 160px;
  max-height: 220px;
  border-radius: var(--feed-img-radius, 10px);
  transition: transform 0.25s ease;
}

.media-row .media-row-img:hover {
  transform: scale(1.02);
}

.media-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}

.media-grid .media-grid-img {
  aspect-ratio: 1;
  border-radius: var(--feed-img-radius, 10px);
  transition: transform 0.25s ease;
}

.media-grid .media-grid-img:hover {
  transform: scale(1.03);
}

.media-wrap :deep(.el-image) {
  display: block;
  cursor: pointer;
}

.media-wrap :deep(.el-image__inner) {
  border-radius: var(--feed-img-radius, 10px);
}

.route-embed,
.companion-embed {
  margin-bottom: 4px;
}

.route-days {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 8px;
}

.day-tag {
  font-size: 12px;
  color: #606266;
  padding: 2px 8px;
  background: #f0f2f5;
  border-radius: 6px;
}

.status-tag {
  margin-top: 4px;
}

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

.card-comments {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #f0f2f5;
}

.comment-loading {
  font-size: 14px;
  color: #909399;
  padding: 8px 0;
}

.comment-item {
  padding: 6px 0;
  font-size: 14px;
  color: #606266;
}

.comment-time {
  margin-left: 8px;
  font-size: 12px;
  color: #909399;
}

.comment-input-row {
  margin-top: 12px;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.comment-input {
  flex: 1;
  min-width: 0;
}

.comment-input :deep(.el-textarea__inner) {
  border-radius: 8px;
  resize: none;
}

.comment-send-btn {
  flex-shrink: 0;
}

.text-subtle {
  color: #909399;
  font-size: 14px;
}
</style>
