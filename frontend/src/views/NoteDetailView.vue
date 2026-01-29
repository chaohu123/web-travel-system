<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { commentsApi, interactionsApi, notesApi, routesApi, userApi } from '../api'
import type { CommentItem, NoteSummary, PlanResponse, UserPublicProfile } from '../api'
import { useAuthStore, reputationLevelLabel } from '../store'

interface NoteDetail {
  id: number
  title: string
  content: string
  coverImage?: string
  relatedPlanId?: number
  destination?: string
  authorId?: number
  authorName?: string
  createdAt?: string
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const note = ref<NoteDetail | null>(null)
const loading = ref(false)

const authorProfile = ref<UserPublicProfile | null>(null)
const authorLoading = ref(false)

const relatedRoute = ref<PlanResponse | null>(null)
const relatedRouteLoading = ref(false)

const recommendedNotes = ref<NoteSummary[]>([])
const recommendedLoading = ref(false)

const comments = ref<CommentItem[]>([])
const loadingComments = ref(false)
const newContent = ref('')
const newScore = ref<number | null>(null)
const replyingTo = ref<string | null>(null)
const postingComment = ref(false)
const commentError = ref('')

// 点赞 / 收藏 / 浏览
const likeCount = ref(0)
const favoriteCount = ref(0)
const likedByMe = ref(false)
const favoritedByMe = ref(false)
const interactionLoading = ref(false)
const viewCount = ref(0)

const heroImages = computed(() => {
  const imgs: string[] = []
  if (note.value?.coverImage) imgs.push(note.value.coverImage)
  return imgs
})

const displayTitle = computed(() => note.value?.title ?? '游记详情')

const displayDestination = computed(() => note.value?.destination || '目的地未填写')

const displayAuthorName = computed(
  () => authorProfile.value?.nickname || note.value?.authorName || '旅友',
)

const createdDateStr = computed(() => {
  if (!note.value?.createdAt) return ''
  const d = new Date(note.value.createdAt)
  return d.toLocaleString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric' })
})

const createdRelative = computed(() => {
  if (!note.value?.createdAt) return ''
  const created = new Date(note.value.createdAt).getTime()
  const diff = Date.now() - created
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 30) return `${days} 天前`
  const months = Math.floor(days / 30)
  if (months < 12) return `${months} 个月前`
  const years = Math.floor(months / 12)
  return `${years} 年前`
})

const tripMonthLabel = computed(() => {
  if (!note.value?.createdAt) return '时间未填写'
  const d = new Date(note.value.createdAt)
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月`
})

const tripDaysLabel = computed(() => {
  if (!relatedRoute.value?.startDate || !relatedRoute.value?.endDate) return '天数未填写'
  const start = new Date(relatedRoute.value.startDate).getTime()
  const end = new Date(relatedRoute.value.endDate).getTime()
  const days = Math.max(1, Math.round((end - start) / (24 * 3600 * 1000)) + 1)
  return `${days} 天行程`
})

const tripTypeLabel = computed(() => {
  if (!relatedRoute.value) return '旅行方式未填写'
  const people = relatedRoute.value.peopleCount ?? 1
  if (people === 1) return '独行'
  if (people === 2) return '情侣 / 双人'
  if (people <= 4) return '结伴小团'
  return '家庭 / 多人'
})

const authorId = computed(() => note.value?.authorId)
const isSelf = computed(() => !!authorId.value && auth.userId === authorId.value)

const authorReputationText = computed(() =>
  reputationLevelLabel(authorProfile.value?.reputationLevel ?? null),
)

const isFollowed = computed(() => !!authorProfile.value?.isFollowed)

const totalComments = computed(() => comments.value.length)

const totalViewsLabel = computed(() => (viewCount.value || 0).toLocaleString('zh-CN'))

const emojiList = ['😄', '😍', '👍', '🏖️', '🚗', '📸']

const fetchDetail = async () => {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    note.value = await notesApi.getOne(Number(id))
  } finally {
    loading.value = false
  }
}

const fetchAuthorProfile = async () => {
  if (!note.value?.authorId) return
  authorLoading.value = true
  try {
    authorProfile.value = await userApi.getPublicProfile(note.value.authorId)
  } finally {
    authorLoading.value = false
  }
}

const fetchRelatedRoute = async () => {
  if (!note.value?.relatedPlanId) return
  relatedRouteLoading.value = true
  try {
    relatedRoute.value = await routesApi.getOne(note.value.relatedPlanId)
  } finally {
    relatedRouteLoading.value = false
  }
}

const fetchRecommendedNotes = async () => {
  recommendedLoading.value = true
  try {
    const list = await notesApi.list()
    const currentId = note.value?.id
    const currentDest = note.value?.destination
    recommendedNotes.value = list
      .filter((n) => n.id !== currentId && (!currentDest || n.destination === currentDest))
      .slice(0, 4)
  } finally {
    recommendedLoading.value = false
  }
}

const fetchInteractions = async () => {
  if (!note.value) return
  try {
    const summary = await interactionsApi.summary('note', note.value.id)
    likeCount.value = summary.likeCount ?? 0
    favoriteCount.value = summary.favoriteCount ?? 0
    likedByMe.value = !!summary.likedByCurrentUser
    favoritedByMe.value = !!summary.favoritedByCurrentUser
    // 当前后端暂未返回浏览量，前端基于互动情况做一个柔性的估算，增强「被看见」的感觉
    viewCount.value = Math.max(
      120,
      (summary.likeCount ?? 0) * 8 + (totalComments.value || 0) * 3,
    )
  } catch {
    likeCount.value = 0
    favoriteCount.value = 0
    if (!viewCount.value) viewCount.value = 0
  }
}

const fetchComments = async () => {
  if (!note.value) return
  loadingComments.value = true
  try {
    comments.value = await commentsApi.list('note', note.value.id)
  } finally {
    loadingComments.value = false
  }
}

const ensureLogin = () => {
  if (!auth.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}

const submitComment = async () => {
  if (!note.value) return
  if (!newContent.value.trim()) {
    commentError.value = '请输入评论内容'
    return
  }
  if (!ensureLogin()) return
  postingComment.value = true
  commentError.value = ''
  try {
    await commentsApi.create({
      targetType: 'note',
      targetId: note.value.id,
      content: newContent.value,
      score: newScore.value == null ? undefined : newScore.value,
    })
    newContent.value = ''
    newScore.value = null
    replyingTo.value = null
    await fetchComments()
  } catch (e: any) {
    commentError.value = e.response?.data?.message || '发表评论失败'
  } finally {
    postingComment.value = false
  }
}

const toggleLike = async () => {
  if (!note.value || interactionLoading.value) return
  if (!ensureLogin()) return
  interactionLoading.value = true
  try {
    if (likedByMe.value) {
      await interactionsApi.unlike('note', note.value.id)
      likedByMe.value = false
      likeCount.value = Math.max(0, likeCount.value - 1)
    } else {
      await interactionsApi.like('note', note.value.id)
      likedByMe.value = true
      likeCount.value += 1
    }
  } finally {
    interactionLoading.value = false
  }
}

const toggleFavorite = async () => {
  if (!note.value || interactionLoading.value) return
  if (!ensureLogin()) return
  interactionLoading.value = true
  try {
    if (favoritedByMe.value) {
      await interactionsApi.unfavorite('note', note.value.id)
      favoritedByMe.value = false
      favoriteCount.value = Math.max(0, favoriteCount.value - 1)
    } else {
      await interactionsApi.favorite('note', note.value.id)
      favoritedByMe.value = true
      favoriteCount.value += 1
    }
  } finally {
    interactionLoading.value = false
  }
}

const handleShare = async () => {
  const url = window.location.href
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success('链接已复制，快分享给旅友吧～')
  } catch {
    ElMessage.info('已选择当前地址栏链接，可手动复制分享')
  }
}

const handleReply = (c: CommentItem) => {
  replyingTo.value = c.userName || '旅友'
  newContent.value = `@${replyingTo.value} `
}

const appendEmoji = (emoji: string) => {
  newContent.value += emoji
}

const scrollToComments = () => {
  if (typeof document !== 'undefined') {
    const el = document.getElementById('comment-section')
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' })
      // 添加高亮效果
      el.style.transition = 'box-shadow 0.3s ease'
      el.style.boxShadow = '0 0 0 4px rgba(15, 118, 110, 0.2)'
      setTimeout(() => {
        el.style.boxShadow = ''
      }, 2000)
    }
  }
}

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'notes' })
  }
}

const goPlan = () => {
  if (!note.value?.relatedPlanId) return
  router.push({ name: 'route-detail', params: { id: note.value.relatedPlanId } })
}

const goAuthorProfile = () => {
  if (!authorId.value) return
  router.push({ name: 'user-profile', params: { id: authorId.value } })
}

const goToUserProfile = (userId: number | undefined) => {
  if (!userId) return
  router.push({ name: 'user-profile', params: { id: userId } })
}

const handleCommentAvatarClick = (comment: CommentItem) => {
  if (comment.userId) {
    goToUserProfile(comment.userId)
  }
}

const goChat = () => {
  if (!authorId.value) return
  if (!ensureLogin()) return
  router.push({ name: 'chat', params: { id: authorId.value }, query: { nickname: displayAuthorName.value } })
}

const handleFollowClick = async () => {
  if (!authorId.value) return
  if (!ensureLogin()) return
  if (!authorProfile.value) return
  try {
    if (authorProfile.value.isFollowed) {
      await userApi.unfollow(authorId.value)
      authorProfile.value.isFollowed = false
      if (authorProfile.value.followersCount != null) {
        authorProfile.value.followersCount = Math.max(0, authorProfile.value.followersCount - 1)
      }
    } else {
      await userApi.follow(authorId.value)
      authorProfile.value.isFollowed = true
      if (authorProfile.value.followersCount != null) {
        authorProfile.value.followersCount += 1
      }
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '操作失败，请稍后重试')
  }
}

const goSpotDetail = (id: number) => {
  router.push({ name: 'spot-detail', params: { id } })
}

const recommendedSpots = computed(() => {
  const dest = displayDestination.value
  return [
    {
      id: 1,
      name: `${dest} 核心景点 A`,
      image: 'https://picsum.photos/seed/spotA/320/200',
      distance: '距离市中心 1.2km',
      score: 4.8,
      description: '这里是该目的地的标志性景点，拥有悠久的历史文化和独特的自然风光，是游客必打卡的热门地点。',
    },
    {
      id: 2,
      name: `${dest} 打卡地 B`,
      image: 'https://picsum.photos/seed/spotB/320/200',
      distance: '距离酒店 800m',
      score: 4.7,
      description: '充满艺术气息的现代地标，融合了传统与现代元素，是摄影爱好者的天堂，适合悠闲漫步。',
    },
    {
      id: 3,
      name: `${dest} 小众秘境 C`,
      image: 'https://picsum.photos/seed/spotC/320/200',
      distance: '驾车约 25 分钟',
      score: 4.9,
      description: '远离喧嚣的隐秘之地，拥有原始的自然美景和宁静的氛围，是寻求心灵放松的理想去处。',
    },
  ]
})

onMounted(async () => {
  await fetchDetail()
  await Promise.all([fetchComments(), fetchInteractions(), fetchAuthorProfile(), fetchRelatedRoute()])
  await fetchRecommendedNotes()
})
</script>

<template>
  <div class="note-page-wrapper">
    <!-- 返回按钮 -->
    <div class="note-back-button">
      <el-button
        :icon="ArrowLeft"
        circle
        @click="goBack"
      >
      </el-button>
    </div>
    <!-- 加载状态 -->
    <div v-if="loading" class="note-loading">
      <el-skeleton :rows="8" animated />
    </div>
    <div v-else-if="note" class="note-page">
      <!-- 左侧主内容 -->
      <article class="note-main" itemscope itemtype="https://schema.org/Article">
        <!-- 头图 + 标题区 -->
        <header class="note-hero">
          <div class="note-hero-cover" v-if="heroImages.length">
            <el-carousel
              height="360px"
              indicator-position="outside"
              :interval="6000"
              trigger="click"
              arrow="hover"
            >
              <el-carousel-item v-for="(img, index) in heroImages" :key="index">
                <el-image
                  :src="img"
                  fit="cover"
                  :lazy="true"
                  :preview-src-list="heroImages"
                  :initial-index="index"
                  class="note-hero-image"
                />
                <div class="note-hero-gradient" />
                <div class="note-hero-title-layer">
                  <h1 class="note-title" itemprop="headline">
                    {{ displayTitle }}
                  </h1>
                  <p class="note-subtitle" v-if="note.destination">
                    {{ note.destination }} · {{ tripMonthLabel }} · {{ tripDaysLabel }} · {{ tripTypeLabel }}
                  </p>
                  <div class="note-tags">
                    <el-tag v-if="note.destination" type="success" effect="dark" round>
                      {{ note.destination }}
                    </el-tag>
                    <el-tag type="info" round>
                      {{ tripMonthLabel }}
                    </el-tag>
                    <el-tag type="warning" round>
                      {{ tripDaysLabel }}
                    </el-tag>
                    <el-tag type="primary" round>
                      {{ tripTypeLabel }}
                    </el-tag>
                  </div>
                </div>
              </el-carousel-item>
            </el-carousel>
          </div>
          <div v-else class="note-hero-plain">
            <h1 class="note-title" itemprop="headline">
              {{ displayTitle }}
            </h1>
            <p class="note-subtitle" v-if="note.destination">
              {{ note.destination }} · {{ tripMonthLabel }} · {{ tripDaysLabel }} · {{ tripTypeLabel }}
            </p>
          </div>

          <!-- 作者 & 数据 -->
          <div class="note-meta-row">
            <div
              class="note-author"
              :class="{ 'note-author-clickable': !!authorId }"
              @click="goAuthorProfile"
            >
              <el-avatar
                :size="44"
                :src="authorProfile?.avatar"
                class="note-author-avatar"
                @click.stop="goAuthorProfile"
                :style="{ cursor: authorId ? 'pointer' : 'default' }"
              >
                {{ displayAuthorName.charAt(0) }}
              </el-avatar>
              <div class="note-author-info">
                <div class="note-author-name-row">
                  <span class="note-author-name">
                    {{ displayAuthorName }}
                  </span>
                  <el-tag size="small" type="success" v-if="authorReputationText">
                    {{ authorReputationText }}
                  </el-tag>
                </div>
                <p class="note-author-meta">
                  发布于 {{ createdRelative || createdDateStr }} · 浏览 {{ totalViewsLabel }}
                </p>
              </div>
            </div>
            <div class="note-author-actions">
              <el-button
                v-if="!isSelf"
                size="small"
                :type="isFollowed ? 'default' : 'primary'"
                round
                @click.stop="handleFollowClick"
              >
                {{ isFollowed ? '已关注' : '关注作者' }}
              </el-button>
              <el-button
                v-if="!isSelf"
                size="small"
                round
                @click.stop="goChat"
              >
                私信交流
              </el-button>
              <el-button
                v-if="isSelf"
                size="small"
                type="primary"
                text
                @click="$router.push({ name: 'note-edit', params: { id: note.id } })"
              >
                编辑游记
              </el-button>
            </div>
          </div>
        </header>

        <!-- 正文内容 -->
        <section class="note-content" itemprop="articleBody">
          <div class="note-content-inner">
            <p
              v-for="(line, idx) in note.content.split(/\n+/)"
              :key="idx"
              class="note-paragraph"
            >
              <span
                v-if="/^Day\\s*\\d+|^DAY\\s*\\d+/.test(line)"
                class="note-day-title"
              >
                {{ line }}
              </span>
              <span v-else>
                {{ line }}
              </span>
            </p>
          </div>
        </section>

        <!-- 关联路线 & 景点 -->
        <section class="note-related">
          <div v-if="relatedRoute" class="note-related-route">
            <h3>关联路线</h3>
            <p class="note-related-desc">
              这篇游记基于真实出行路线，包含 {{ tripDaysLabel }}，适合想要复制行程的旅友。
            </p>
            <div class="note-related-route-card">
              <div class="note-related-route-main">
                <h4>{{ relatedRoute.title || relatedRoute.destination }}</h4>
                <p class="note-related-route-meta">
                  {{ relatedRoute.destination }}
                  · {{ tripDaysLabel }}
                  <span v-if="relatedRoute.budget"> · 预算约 {{ relatedRoute.budget }} 元/人</span>
                </p>
              </div>
              <div class="note-related-route-actions">
                <el-button type="primary" round size="small" @click="goPlan">
                  查看完整路线
                </el-button>
              </div>
            </div>
          </div>

        </section>

        <!-- 评论区 -->
        <section class="note-comments" id="comment-section">
          <h3 class="note-section-title">
            评论 · {{ totalComments }}
          </h3>

          <!-- 输入区 -->
          <div class="note-comment-editor">
            <el-input
              v-model="newContent"
              :autosize="{ minRows: 3, maxRows: 6 }"
              type="textarea"
              placeholder="写下你的旅途感受、实用建议或对作者的提问…"
            />
            <div class="note-comment-toolbar">
              <div class="note-comment-emojis">
                <span
                  v-for="e in emojiList"
                  :key="e"
                  class="note-emoji"
                  @click="appendEmoji(e)"
                >
                  {{ e }}
                </span>
              </div>
              <div class="note-comment-actions">
                <el-select
                  v-model="newScore"
                  class="note-score-select"
                  placeholder="整体评分（可选）"
                  clearable
                >
                  <el-option :value="5" label="5 分 · 强烈推荐" />
                  <el-option :value="4" label="4 分 · 体验不错" />
                  <el-option :value="3" label="3 分 · 中规中矩" />
                  <el-option :value="2" label="2 分 · 有待改善" />
                  <el-option :value="1" label="1 分 · 不太推荐" />
                </el-select>
                <el-button
                  type="primary"
                  :loading="postingComment"
                  @click="submitComment"
                >
                  {{ postingComment ? '发布中...' : '发表评论' }}
                </el-button>
              </div>
            </div>
            <p v-if="commentError" class="note-error">
              {{ commentError }}
            </p>
          </div>

          <!-- 列表 -->
          <div v-if="loadingComments" class="note-comment-loading">
            <el-skeleton :rows="3" animated />
          </div>
          <div v-else-if="!comments.length" class="note-comment-empty">
            <el-empty description="暂无评论，做第一个分享旅途体验的人吧～" :image-size="80" />
          </div>
          <div v-else class="note-comment-list">
            <el-comment
              v-for="c in comments"
              :key="c.id"
              :author="c.userName || '旅友'"
              :content="c.content"
            >
              <template #avatar>
                <el-avatar
                  size="small"
                  class="note-comment-avatar-clickable"
                  @click.stop="handleCommentAvatarClick(c)"
                  :style="{ cursor: c.userId ? 'pointer' : 'default' }"
                >
                  {{ (c.userName || '旅友').charAt(0) }}
                </el-avatar>
              </template>
              <template #datetime>
                <span class="note-comment-time">
                  {{ c.createdAt }}
                </span>
              </template>
              <template #actions>
                <span
                  class="note-comment-action"
                  @click="handleReply(c)"
                >
                  回复
                </span>
                <span v-if="c.score" class="note-comment-score">
                  评分 {{ c.score }} / 5
                </span>
              </template>
            </el-comment>
          </div>
        </section>
      </article>

      <!-- 右侧信息区（PC） -->
      <aside class="note-side">
        <!-- 作者卡片 -->
        <section
          class="note-side-card note-author-card"
          v-if="authorProfile"
          :class="{ 'note-author-card-clickable': !!authorId }"
          @click="goAuthorProfile"
        >
          <div class="note-side-author-main">
            <div class="note-side-author-header">
              <el-avatar
                :size="52"
                :src="authorProfile.avatar"
                @click.stop="goAuthorProfile"
                class="note-side-author-avatar-clickable"
              >
                {{ displayAuthorName.charAt(0) }}
              </el-avatar>
              <div class="note-side-author-info">
                <div class="note-side-author-name">
                  {{ displayAuthorName }}
                </div>
                <div class="note-side-author-tags">
                  <el-tag v-if="authorReputationText" size="small" type="success">
                    {{ authorReputationText }}
                  </el-tag>
                  <el-tag
                    v-for="tag in authorProfile.preferences?.travelStyles || []"
                    :key="tag"
                    size="small"
                    effect="plain"
                  >
                    {{ tag }}
                  </el-tag>
                </div>
              </div>
            </div>
            <p v-if="authorProfile.intro" class="note-side-author-intro">
              {{ authorProfile.intro }}
            </p>
          </div>
          <div class="note-side-author-stats" v-if="authorProfile.stats">
            <div class="note-side-stat-item">
              <div class="note-side-stat-number">
                {{ authorProfile.stats.completedRoutes }}
              </div>
              <div class="note-side-stat-label">完成路线</div>
            </div>
            <div class="note-side-stat-item">
              <div class="note-side-stat-number">
                {{ authorProfile.stats.notesCount }}
              </div>
              <div class="note-side-stat-label">发布游记</div>
            </div>
            <div class="note-side-stat-item">
              <div class="note-side-stat-number">
                {{ authorProfile.stats.likedCount }}
              </div>
              <div class="note-side-stat-label">获赞</div>
            </div>
          </div>
          <div class="note-side-author-actions">
            <el-button
              v-if="!isSelf"
              size="small"
              :type="isFollowed ? 'default' : 'primary'"
              round
              @click="handleFollowClick"
            >
              {{ isFollowed ? '已关注' : '关注作者' }}
            </el-button>
            <el-button
              v-if="!isSelf"
              size="small"
              round
              @click="goChat"
            >
              私信交流
            </el-button>
          </div>
        </section>

        <!-- 关联行程 -->
        <section v-if="relatedRoute" class="note-side-card">
          <h3 class="note-side-title">本游记对应路线</h3>
          <p class="note-side-subtitle">
            想复制这趟旅程？直接查看完整路线规划。
          </p>
          <div class="note-side-route-brief">
            <div class="note-side-route-name">
              {{ relatedRoute.title || relatedRoute.destination }}
            </div>
            <div class="note-side-route-meta">
              {{ relatedRoute.destination }} · {{ tripDaysLabel }}
            </div>
          </div>
          <el-button type="primary" round size="small" class="note-side-route-btn" @click="goPlan">
            查看路线详情
          </el-button>
        </section>

        <!-- 相关景点推荐 -->
        <section class="note-side-card note-side-spots">
          <h3 class="note-side-title">相关景点推荐</h3>
          <p class="note-side-subtitle">
            围绕本次旅程目的地，为你推荐附近热门景点。
          </p>
          <ul class="note-side-spots-list">
            <li
              v-for="(s, index) in recommendedSpots"
              :key="s.id"
              class="note-side-spot-item"
              @click="goSpotDetail(s.id)"
            >
              <div class="note-side-spot-image-wrapper">
                <el-image
                  :src="s.image"
                  fit="cover"
                  :lazy="true"
                  class="note-side-spot-image"
                  :preview-src-list="[s.image]"
                />
                <span class="note-side-spot-number">景点{{ index + 1 }}</span>
              </div>
              <div class="note-side-spot-content">
                <h4 class="note-side-spot-name">{{ s.name }}</h4>
                <p class="note-side-spot-description" v-if="s.description">
                  {{ s.description }}
                </p>
                <div class="note-side-spot-meta" v-if="s.distance || s.score">
                  <span v-if="s.distance" class="note-side-spot-distance">{{ s.distance }}</span>
                  <el-tag v-if="s.score" size="small" type="warning" effect="plain" class="note-side-spot-score">
                    ⭐ {{ s.score.toFixed(1) }}
                  </el-tag>
                </div>
              </div>
            </li>
          </ul>
        </section>

        <!-- 推荐游记 -->
        <section v-if="recommendedNotes.length" class="note-side-card">
          <h3 class="note-side-title">推荐游记</h3>
          <p class="note-side-subtitle">
            与当前目的地或路线相似的真实游记。
          </p>
          <ul class="note-side-notes">
            <li
              v-for="n in recommendedNotes"
              :key="n.id"
              class="note-side-note-item"
              @click="$router.push({ name: 'note-detail', params: { id: n.id } })"
            >
              <div class="note-side-note-cover">
                <el-image
                  :src="n.coverImage || 'https://picsum.photos/seed/reco' + n.id + '/160/100'"
                  fit="cover"
                  :lazy="true"
                />
              </div>
              <div class="note-side-note-body">
                <div class="note-side-note-title">
                  {{ n.title }}
                </div>
                <div class="note-side-note-meta">
                  {{ n.destination || '目的地未填写' }}
                </div>
              </div>
            </li>
          </ul>
        </section>
      </aside>

      <!-- 底部/悬浮操作条 -->
      <div class="note-floating-actions">
        <div class="note-floating-inner">
          <div class="note-floating-left">
            <span class="note-floating-text">
              {{ displayDestination }} · {{ tripMonthLabel }}
            </span>
          </div>
          <div class="note-floating-right">
            <el-button
              text
              :class="{ 'note-like-active': likedByMe }"
              @click="toggleLike"
              :disabled="interactionLoading"
            >
              <span class="note-action-icon">👍</span>
              <span class="note-action-count">{{ likeCount || 0 }}</span>
            </el-button>
            <el-button
              text
              @click="scrollToComments"
            >
              <span class="note-action-icon">💬</span>
              <span class="note-action-count">{{ totalComments || 0 }}</span>
            </el-button>
            <el-button
              text
              :class="{ 'note-fav-active': favoritedByMe }"
              @click="toggleFavorite"
              :disabled="interactionLoading"
            >
              <span class="note-action-icon">⭐</span>
              <span class="note-action-count">{{ favoriteCount || 0 }}</span>
            </el-button>
            <el-button text @click="handleShare">
              <span class="note-action-icon">🔗</span>
              <span>分享</span>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="note-empty">
      <el-empty description="未找到游记内容，可能已被删除或下架">
        <el-button type="primary" @click="$router.push({ name: 'notes' })">
          返回游记列表
        </el-button>
      </el-empty>
    </div>
  </div>
</template>

<style scoped>
.note-page-wrapper {
  display: flex;
  justify-content: center;
  padding: 20px 16px 100px;
  background: linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%);
  min-height: calc(100vh - 60px);
  position: relative;
}

.note-back-button {
  position: fixed;
  top: 80px;
  left: 20px;
  z-index: 200;
  animation: fadeInLeft 0.5s ease-out;
}

@keyframes fadeInLeft {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.note-back-button :deep(.el-button) {
  width: 44px;
  height: 44px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.1);
  transition: all 0.3s ease;
}

.note-back-button :deep(.el-button:hover) {
  background: #0f766e;
  border-color: #0f766e;
  color: #ffffff;
  transform: translateX(-4px);
  box-shadow: 0 6px 20px rgba(15, 118, 110, 0.3);
}

.note-loading {
  width: 1120px;
  max-width: 100%;
  background: #ffffff;
  border-radius: 20px;
  padding: 24px;
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.08);
}

.note-page {
  width: 1120px;
  max-width: 100%;
  display: grid;
  grid-template-columns: minmax(0, 2.5fr) minmax(280px, 1fr);
  gap: 20px;
  align-items: flex-start;
}

.note-main {
  background: #ffffff;
  border-radius: 24px;
  padding: 24px 28px 32px;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.08), 0 0 0 1px rgba(15, 23, 42, 0.04);
  transition: box-shadow 0.3s ease;
  animation: fadeInUp 0.6s ease-out;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.note-hero {
  margin-bottom: 24px;
}

.note-hero-cover {
  border-radius: 20px;
  overflow: hidden;
  position: relative;
  box-shadow: 0 8px 32px rgba(15, 23, 42, 0.12);
  transition: transform 0.3s ease, box-shadow 0.3s ease;
}

.note-hero-cover:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.16);
}

.note-hero-image {
  width: 100%;
  height: 100%;
  transition: transform 0.6s ease;
}

.note-hero-cover:hover .note-hero-image {
  transform: scale(1.05);
}

.note-hero-gradient {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    180deg,
    rgba(15, 23, 42, 0.1) 0%,
    rgba(15, 23, 42, 0.3) 40%,
    rgba(15, 23, 42, 0.75) 75%,
    rgba(15, 23, 42, 0.95) 100%
  );
  pointer-events: none;
  z-index: 1;
}

.note-hero-title-layer {
  position: absolute;
  left: 32px;
  right: 32px;
  bottom: 24px;
  color: #f9fafb;
  z-index: 2;
  animation: fadeInUp 0.8s ease-out 0.2s both;
}

.note-title {
  margin: 0 0 8px;
  font-size: 32px;
  font-weight: 700;
  letter-spacing: -0.01em;
  line-height: 1.3;
  text-shadow: 0 2px 12px rgba(0, 0, 0, 0.3);
}

.note-subtitle {
  margin: 0 0 12px;
  font-size: 15px;
  color: #e2e8f0;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
}

.note-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 12px;
}

.note-tags :deep(.el-tag) {
  font-weight: 500;
  padding: 6px 14px;
  font-size: 13px;
  backdrop-filter: blur(8px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.15);
}

.note-hero-plain .note-title {
  font-size: 26px;
}

.note-meta-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14px;
  gap: 12px;
}

.note-author {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px;
  margin: -8px;
  border-radius: 12px;
  transition: background-color 0.2s ease, transform 0.2s ease;
}

.note-author-clickable {
  cursor: pointer;
}

.note-author-clickable:hover {
  background-color: #f8fafc;
  transform: translateX(4px);
}

.note-author:not(.note-author-clickable) {
  opacity: 0.7;
  cursor: not-allowed;
}

.note-author-avatar {
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.15);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.note-author-clickable .note-author-avatar {
  cursor: pointer;
}

.note-author-clickable:hover .note-author-avatar {
  transform: scale(1.05);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.2);
}

.note-author-clickable .note-author-avatar:hover {
  transform: scale(1.1);
  box-shadow: 0 8px 20px rgba(15, 118, 110, 0.25);
}

.note-author-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.note-author-name-row {
  display: flex;
  gap: 6px;
  align-items: center;
}

.note-author-name {
  font-size: 16px;
  font-weight: 600;
  color: #111827;
  transition: color 0.2s ease;
}

.note-author:hover .note-author-name {
  color: #0f766e;
}

.note-author-meta {
  font-size: 13px;
  color: #64748b;
  margin-top: 2px;
}

.note-author-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.note-author-actions :deep(.el-button) {
  transition: all 0.2s ease;
}

.note-author-actions :deep(.el-button:hover) {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(15, 118, 110, 0.2);
}

.note-content {
  margin-top: 32px;
  font-size: 16px;
  line-height: 1.9;
  color: #1e293b;
  letter-spacing: 0.01em;
}

.note-content-inner {
  position: relative;
}

.note-paragraph {
  margin: 0 0 20px;
  text-indent: 2em;
  transition: color 0.2s ease;
}

.note-paragraph:last-child {
  margin-bottom: 0;
}

.note-day-title {
  display: block;
  font-weight: 700;
  font-size: 20px;
  color: #0f766e;
  text-indent: 0;
  margin: 32px 0 16px -2em;
  padding: 8px 0;
  border-bottom: 2px solid #e0f2fe;
  position: relative;
}

.note-day-title::before {
  content: '';
  position: absolute;
  left: 0;
  bottom: -2px;
  width: 60px;
  height: 2px;
  background: linear-gradient(90deg, #0f766e, transparent);
}

.note-related {
  margin-top: 40px;
  border-top: 2px solid #f1f5f9;
  padding-top: 28px;
}

.note-related-route h3,
.note-related-spots-header h3 {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 600;
  color: #111827;
}

.note-related-desc {
  margin: 0 0 10px;
  font-size: 13px;
  color: #6b7280;
}

.note-related-route-card {
  border-radius: 16px;
  background: linear-gradient(135deg, #ecfeff 0%, #e0f2fe 50%, #dbeafe 100%);
  padding: 16px 20px;
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
  border: 1px solid rgba(15, 118, 110, 0.1);
  box-shadow: 0 4px 16px rgba(15, 118, 110, 0.08);
  transition: all 0.3s ease;
}

.note-related-route-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(15, 118, 110, 0.12);
}

.note-related-route-main h4 {
  margin: 0 0 4px;
  font-size: 15px;
}

.note-related-route-meta {
  margin: 0;
  font-size: 13px;
  color: #4b5563;
}

.note-related-spots {
  margin-top: 16px;
}

.note-related-spots-scroll {
  display: flex;
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 8px;
  scroll-behavior: smooth;
  scrollbar-width: thin;
  scrollbar-color: rgba(15, 118, 110, 0.3) transparent;
}

.note-related-spots-scroll::-webkit-scrollbar {
  height: 6px;
}

.note-related-spots-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.note-related-spots-scroll::-webkit-scrollbar-thumb {
  background: rgba(15, 118, 110, 0.3);
  border-radius: 3px;
}

.note-related-spots-scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(15, 118, 110, 0.5);
}

.note-spot-card {
  flex: 0 0 220px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.08);
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid rgba(15, 23, 42, 0.06);
}

.note-spot-card:hover {
  transform: translateY(-4px) scale(1.02);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.15);
  border-color: rgba(15, 118, 110, 0.2);
}

.note-spot-image {
  width: 100%;
  height: 120px;
  transition: transform 0.4s ease;
}

.note-spot-card:hover .note-spot-image {
  transform: scale(1.1);
}

.note-spot-body {
  padding: 8px 10px 9px;
}

.note-spot-body h4 {
  margin: 0 0 2px;
  font-size: 14px;
}

.note-spot-distance {
  margin: 0 0 4px;
  font-size: 12px;
  color: #6b7280;
}

.note-spot-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.note-spot-link {
  font-size: 12px;
  color: #0f766e;
}

.note-comments {
  margin-top: 40px;
  border-top: 2px solid #f1f5f9;
  padding-top: 28px;
  scroll-margin-top: 80px;
  transition: box-shadow 0.3s ease;
}

.note-section-title {
  margin: 0 0 20px;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 8px;
}

.note-section-title::before {
  content: '';
  width: 4px;
  height: 20px;
  background: linear-gradient(180deg, #0f766e, #14b8a6);
  border-radius: 2px;
}

.note-comment-editor {
  background: linear-gradient(180deg, #f8fafc, #f1f5f9);
  border-radius: 18px;
  padding: 16px 18px 18px;
  border: 1px solid #e2e8f0;
  transition: all 0.3s ease;
}

.note-comment-editor:focus-within {
  border-color: #0f766e;
  box-shadow: 0 0 0 3px rgba(15, 118, 110, 0.1);
  background: #ffffff;
}

.note-comment-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
  gap: 8px;
}

.note-comment-emojis {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 20px;
}

.note-emoji {
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 8px;
  transition: all 0.2s ease;
  user-select: none;
}

.note-emoji:hover {
  background: rgba(15, 118, 110, 0.1);
  transform: scale(1.2);
}

.note-emoji:active {
  transform: scale(1.1);
}

.note-comment-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.note-score-select {
  width: 180px;
}

.note-error {
  margin-top: 4px;
  font-size: 12px;
  color: #dc2626;
}

.note-comment-empty {
  margin-top: 20px;
  padding: 40px 20px;
}

.note-comment-loading {
  margin-top: 20px;
  padding: 20px;
  background: #f8fafc;
  border-radius: 12px;
}

.note-comment-list {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.note-comment-list :deep(.el-comment) {
  padding: 16px;
  border-radius: 12px;
  background: #f8fafc;
  transition: all 0.2s ease;
}

.note-comment-list :deep(.el-comment:hover) {
  background: #f1f5f9;
  transform: translateX(4px);
}

.note-comment-time {
  font-size: 12px;
  color: #94a3b8;
}

.note-comment-action {
  font-size: 13px;
  color: #0f766e;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: all 0.2s ease;
}

.note-comment-action:hover {
  background: rgba(15, 118, 110, 0.1);
  color: #0d9488;
}

.note-comment-score {
  font-size: 12px;
  color: #f97316;
  margin-left: 8px;
  font-weight: 500;
}

.note-comment-avatar-clickable {
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.note-comment-avatar-clickable:hover {
  transform: scale(1.15);
  box-shadow: 0 2px 8px rgba(15, 118, 110, 0.3);
}

.note-side {
  display: flex;
  flex-direction: column;
  gap: 14px;
  align-self: flex-start;
}

.note-side-card {
  background: #ffffff;
  border-radius: 20px;
  padding: 20px;
  box-shadow: 0 4px 20px rgba(15, 23, 42, 0.08), 0 0 0 1px rgba(15, 23, 42, 0.04);
  transition: all 0.3s ease;
  animation: fadeInRight 0.6s ease-out;
}

.note-side-card:hover {
  box-shadow: 0 8px 28px rgba(15, 23, 42, 0.12), 0 0 0 1px rgba(15, 23, 42, 0.06);
  transform: translateY(-2px);
}

@keyframes fadeInRight {
  from {
    opacity: 0;
    transform: translateX(20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.note-author-card {
  background: linear-gradient(145deg, #ecfeff 0%, #e0f2fe 50%, #dbeafe 100%);
  border: 1px solid rgba(15, 118, 110, 0.1);
  transition: all 0.3s ease;
}

.note-author-card-clickable {
  cursor: pointer;
}

.note-author-card-clickable:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 32px rgba(15, 118, 110, 0.15);
  border-color: rgba(15, 118, 110, 0.2);
}

.note-author-card:not(.note-author-card-clickable) {
  opacity: 0.8;
  cursor: not-allowed;
}

.note-side-author-header {
  display: flex;
  gap: 10px;
  align-items: center;
}

.note-side-author-avatar-clickable {
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.note-side-author-avatar-clickable:hover {
  transform: scale(1.1);
  box-shadow: 0 4px 12px rgba(15, 118, 110, 0.3);
}

.note-side-author-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.note-side-author-name {
  font-size: 15px;
  font-weight: 600;
}

.note-side-author-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.note-side-author-intro {
  margin: 8px 0 0;
  font-size: 13px;
  color: #374151;
}

.note-side-author-stats {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
}

.note-side-stat-item {
  text-align: center;
}

.note-side-stat-number {
  font-size: 16px;
  font-weight: 600;
}

.note-side-stat-label {
  font-size: 12px;
  color: #6b7280;
}

.note-side-author-actions {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.note-side-title {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 600;
}

.note-side-subtitle {
  margin: 0 0 10px;
  font-size: 12px;
  color: #6b7280;
}

.note-side-route-brief {
  margin-bottom: 8px;
}

.note-side-route-name {
  font-size: 14px;
  font-weight: 500;
}

.note-side-route-meta {
  font-size: 12px;
  color: #6b7280;
}

.note-side-route-btn {
  width: 100%;
  margin-top: 4px;
}

.note-side-notes {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.note-side-note-item {
  display: flex;
  gap: 10px;
  cursor: pointer;
  padding: 8px;
  margin: -8px;
  border-radius: 12px;
  transition: all 0.2s ease;
}

.note-side-note-item:hover {
  background: #f8fafc;
  transform: translateX(4px);
}

.note-side-note-cover {
  flex: 0 0 72px;
  border-radius: 10px;
  overflow: hidden;
  transition: transform 0.2s ease;
}

.note-side-note-item:hover .note-side-note-cover {
  transform: scale(1.05);
}

.note-side-note-body {
  flex: 1;
}

.note-side-note-title {
  font-size: 13px;
  font-weight: 500;
  color: #111827;
  transition: color 0.2s ease;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note-side-note-item:hover .note-side-note-title {
  color: #0f766e;
}

.note-side-note-meta {
  margin-top: 2px;
  font-size: 12px;
  color: #6b7280;
}

/* 相关景点列表形式 - 带图片和简介 */
.note-side-spots {
  margin-top: 0;
}

.note-side-spots-list {
  list-style: none;
  padding: 0;
  margin: 12px 0 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.note-side-spot-item {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.3s ease;
  border: 1px solid #e2e8f0;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(15, 23, 42, 0.06);
}

.note-side-spot-item:hover {
  border-color: rgba(15, 118, 110, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(15, 23, 42, 0.12);
}

.note-side-spot-image-wrapper {
  position: relative;
  width: 100%;
  height: 120px;
  overflow: hidden;
  background: #f1f5f9;
}

.note-side-spot-image {
  width: 100%;
  height: 100%;
  transition: transform 0.4s ease;
}

.note-side-spot-item:hover .note-side-spot-image {
  transform: scale(1.08);
}

.note-side-spot-number {
  position: absolute;
  top: 8px;
  left: 8px;
  background: rgba(15, 118, 110, 0.9);
  color: #ffffff;
  font-size: 11px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 6px;
  backdrop-filter: blur(4px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
  z-index: 1;
}

.note-side-spot-content {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.note-side-spot-name {
  margin: 0;
  font-size: 14px;
  color: #111827;
  font-weight: 600;
  transition: color 0.2s ease;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note-side-spot-item:hover .note-side-spot-name {
  color: #0f766e;
}

.note-side-spot-description {
  margin: 0;
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note-side-spot-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 4px;
}

.note-side-spot-distance {
  font-size: 11px;
  color: #94a3b8;
  flex: 1;
}

.note-side-spot-score {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
}

.note-floating-actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 0 16px 16px;
  pointer-events: none;
  z-index: 100;
  animation: slideUp 0.4s ease-out;
}

@keyframes slideUp {
  from {
    opacity: 0;
    transform: translateY(100%);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.note-floating-inner {
  max-width: 1120px;
  margin: 0 auto;
  background: rgba(15, 23, 42, 0.98);
  backdrop-filter: blur(20px);
  border-radius: 999px;
  padding: 10px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  color: #e5e7eb;
  pointer-events: auto;
  box-shadow: 0 -4px 24px rgba(15, 23, 42, 0.3), 0 0 0 1px rgba(255, 255, 255, 0.1);
  transition: all 0.3s ease;
}

.note-floating-inner:hover {
  box-shadow: 0 -6px 32px rgba(15, 23, 42, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.15);
}

.note-floating-left {
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.note-floating-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.note-floating-right :deep(.el-button) {
  color: #cbd5e1;
  transition: all 0.2s ease;
  padding: 8px 12px;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.note-floating-right :deep(.el-button:hover:not(:disabled)) {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.1);
  transform: translateY(-2px);
}

.note-floating-right :deep(.el-button:disabled) {
  opacity: 0.5;
  cursor: not-allowed;
}

.note-action-icon {
  font-size: 16px;
  display: inline-block;
  transition: transform 0.2s ease;
}

.note-floating-right :deep(.el-button:hover:not(:disabled)) .note-action-icon {
  transform: scale(1.15);
}

.note-action-count {
  font-weight: 500;
  min-width: 20px;
  text-align: center;
}

.note-floating-text {
  opacity: 0.85;
  font-weight: 500;
}

.note-like-active,
.note-fav-active {
  color: #fbbf24 !important;
  animation: bounce 0.5s ease;
}

@keyframes bounce {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-4px) scale(1.1);
  }
}

.note-empty {
  width: 100%;
  max-width: 600px;
  margin: 0 auto;
  padding: 80px 20px;
  background: #ffffff;
  border-radius: 24px;
  box-shadow: 0 4px 24px rgba(15, 23, 42, 0.08);
}

/* 响应式优化 */
@media (max-width: 1200px) {
  .note-page {
    width: 100%;
    gap: 16px;
  }
}

@media (max-width: 900px) {
  .note-page-wrapper {
    padding: 12px 12px 90px;
  }

  .note-back-button {
    top: 70px;
    left: 12px;
  }

  .note-back-button :deep(.el-button) {
    width: 40px;
    height: 40px;
  }

  .note-loading {
    width: 100%;
    padding: 16px;
  }

  .note-page {
    grid-template-columns: minmax(0, 1fr);
    gap: 16px;
  }

  .note-side {
    display: none;
  }

  .note-main {
    padding: 16px 16px 24px;
    border-radius: 16px;
  }

  .note-title {
    font-size: 24px;
  }

  .note-hero-title-layer {
    left: 20px;
    right: 20px;
    bottom: 20px;
  }

  .note-hero-cover :deep(.el-carousel__container) {
    height: 280px !important;
  }

  .note-meta-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .note-author-actions {
    width: 100%;
    justify-content: flex-start;
  }

  .note-author-actions :deep(.el-button) {
    flex: 1;
  }

  .note-content {
    font-size: 15px;
    line-height: 1.8;
  }

  .note-day-title {
    font-size: 18px;
    margin-left: 0;
  }

  .note-spot-card {
    flex: 0 0 180px;
  }

  .note-floating-inner {
    padding: 8px 16px;
    gap: 12px;
  }

  .note-floating-left {
    display: none;
  }

  .note-floating-right {
    width: 100%;
    justify-content: space-around;
  }
}

@media (max-width: 640px) {
  .note-page-wrapper {
    padding: 8px 8px 90px;
  }

  .note-back-button {
    top: 60px;
    left: 8px;
  }

  .note-back-button :deep(.el-button) {
    width: 36px;
    height: 36px;
  }

  .note-main {
    padding: 12px 12px 20px;
  }

  .note-title {
    font-size: 20px;
  }

  .note-hero-cover :deep(.el-carousel__container) {
    height: 240px !important;
  }

  .note-tags {
    gap: 6px;
  }

  .note-tags :deep(.el-tag) {
    font-size: 12px;
    padding: 4px 10px;
  }

  .note-comment-editor {
    padding: 12px 14px 14px;
  }

  .note-comment-toolbar {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }

  .note-comment-actions {
    flex-direction: column;
    gap: 8px;
  }

  .note-score-select {
    width: 100%;
  }
}
</style>

