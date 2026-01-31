<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import EditProfileDialog from '../components/EditProfileDialog.vue'
import { useProfileStore, type ProfileTab } from '../store/profile'
import { useAuthStore, reputationLevelLabel } from '../store'
import { useSpotStore } from '../store/spot'
import { userApi, routesApi, companionApi, feedsApi, interactionsApi, notesApi } from '../api'
import type { FavoriteItem } from '../store/profile'
import type { MeDetail, UserPublicProfile, FollowingItem, FollowerItem } from '../api'
import { formatDate, formatDateTime } from '../utils/format'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft,
  ChatDotRound,
  ChatLineRound,
  Delete,
  Document,
  Setting,
  StarFilled,
  Tickets,
  UserFilled,
} from '@element-plus/icons-vue'
import HeartIcon from '../components/HeartIcon.vue'
import RouteCard from '../components/RouteCard.vue'

const router = useRouter()
const auth = useAuthStore()
const store = useProfileStore()
const spotStore = useSpotStore()

const PAGE_SIZE = 8

const activeTab = ref<ProfileTab>('feeds')
const editDialogVisible = ref(false)
const publicProfile = ref<UserPublicProfile | null>(null)

const followDialogVisible = ref(false)
const followDialogType = ref<'following' | 'followers'>('following')
const followingList = ref<FollowingItem[]>([])
const followersList = ref<FollowerItem[]>([])
const followListLoading = ref(false)

const feedStats = ref<Record<number, { likeCount: number; commentCount: number }>>({})

const feedPage = ref(1)
const notePage = ref(1)
const routePage = ref(1)
const companionPage = ref(1)
const favoritePage = ref(1)

const feedKeyword = ref('')
const noteKeyword = ref('')
const routeKeyword = ref('')
const routeStatusFilter = ref('')
const companionKeyword = ref('')
const companionStatusFilter = ref('')
const favoriteTypeFilter = ref('')
const favoriteKeyword = ref('')

const menuItems: { key: ProfileTab; label: string; icon: any }[] = [
  { key: 'feeds', label: '我的动态', icon: ChatLineRound },
  { key: 'notes', label: '我的游记', icon: Document },
  { key: 'routes', label: '我的路线', icon: Tickets },
  { key: 'companion', label: '我的结伴', icon: UserFilled },
  { key: 'favorites', label: '我的收藏', icon: StarFilled },
  { key: 'security', label: '账号与安全', icon: Setting },
]

const levelLabel = computed(() => reputationLevelLabel(store.me?.reputationLevel))

const stats = computed(() => ({
  following: publicProfile.value?.followingCount ?? 0,
  followers: publicProfile.value?.followersCount ?? 0,
  liked: publicProfile.value?.stats?.likedCount ?? 0,
  publishCount:
    (publicProfile.value?.stats?.notesCount ?? 0) +
    (store.myRoutes.length || 0) +
    (store.myCompanion.length || 0) +
    (store.myFeeds.length || 0),
}))

const filteredFeeds = computed(() => {
  const k = feedKeyword.value.trim().toLowerCase()
  if (!k) return store.myFeeds
  return store.myFeeds.filter((f) => (f.content || '').toLowerCase().includes(k))
})

const filteredNotes = computed(() => {
  const k = noteKeyword.value.trim().toLowerCase()
  let list = store.myNotes
  if (k) list = list.filter((n) => (n.title || '').toLowerCase().includes(k) || (n.destination || '').toLowerCase().includes(k))
  return list
})

const filteredRoutes = computed(() => {
  const k = routeKeyword.value.trim().toLowerCase()
  const status = routeStatusFilter.value
  let list = store.myRoutes
  if (k) list = list.filter((r) => (r.title || '').toLowerCase().includes(k) || (r.destination || '').toLowerCase().includes(k))
  if (status) list = list.filter((r) => routeStatus(r) === status)
  return list
})

/** 结伴状态：按结束日期判断，超过结束日期为已过期 */
function companionStatus(p: { endDate?: string }) {
  if (!p?.endDate) return '招募中'
  const end = new Date(p.endDate)
  const now = new Date()
  return now > end ? '已过期' : '招募中'
}

/** 生成结伴标签（与结伴广场一致） */
function companionTags(p: { startDate?: string; endDate?: string; budgetMin?: number; budgetMax?: number }): string[] {
  const tags: string[] = []
  const days = daysBetween(p.startDate || '', p.endDate || '')
  if (days >= 7) tags.push('长线')
  else if (days <= 3) tags.push('短途')
  if (p.budgetMax != null && p.budgetMax < 3000) tags.push('穷游')
  if (p.budgetMin != null && p.budgetMin >= 5000) tags.push('品质')
  if (!tags.length) tags.push('自由行')
  return tags
}

const filteredCompanion = computed(() => {
  const k = companionKeyword.value.trim().toLowerCase()
  const status = companionStatusFilter.value
  let list = store.myCompanion
  if (k) list = list.filter((p) => (p.destination || '').toLowerCase().includes(k))
  if (status) list = list.filter((p) => companionStatus(p) === status)
  return list
})

const filteredFavorites = computed(() => {
  const type = favoriteTypeFilter.value
  const k = favoriteKeyword.value.trim().toLowerCase()
  let list = store.favorites
  if (type) list = list.filter((f) => f.type === type)
  if (k) list = list.filter((f) => (f.title || f.destination || f.nickname || favoriteTypeLabel(f.type) + ' #' + f.id).toLowerCase().includes(k))
  return list
})

const feedTotal = computed(() => filteredFeeds.value.length)
const noteTotal = computed(() => filteredNotes.value.length)
const routeTotal = computed(() => filteredRoutes.value.length)
const companionTotal = computed(() => filteredCompanion.value.length)
const favoriteTotal = computed(() => filteredFavorites.value.length)

const paginatedFeeds = computed(() => {
  const start = (feedPage.value - 1) * PAGE_SIZE
  return filteredFeeds.value.slice(start, start + PAGE_SIZE)
})

const paginatedNotes = computed(() => {
  const start = (notePage.value - 1) * PAGE_SIZE
  return filteredNotes.value.slice(start, start + PAGE_SIZE)
})

const paginatedRoutes = computed(() => {
  const start = (routePage.value - 1) * PAGE_SIZE
  return filteredRoutes.value.slice(start, start + PAGE_SIZE)
})

const paginatedCompanion = computed(() => {
  const start = (companionPage.value - 1) * PAGE_SIZE
  return filteredCompanion.value.slice(start, start + PAGE_SIZE)
})

const paginatedFavorites = computed(() => {
  const start = (favoritePage.value - 1) * PAGE_SIZE
  return filteredFavorites.value.slice(start, start + PAGE_SIZE)
})

async function loadMe() {
  try {
    const me = await userApi.meDetail()
    store.setMe(me)
    auth.setProfile(me.nickname ?? null, me.reputationLevel ?? null)
    if (me?.id) {
      try {
        const profile = await userApi.getPublicProfile(me.id)
        publicProfile.value = profile
      } catch {
        publicProfile.value = null
      }
    } else {
      publicProfile.value = null
    }
  } catch {
    store.setMe(null)
    publicProfile.value = null
  }
}

async function loadRoutes() {
  try {
    const list = await routesApi.myPlans()
    store.setMyRoutes(list || [])
  } catch {
    store.setMyRoutes([])
  }
}

async function deleteRoute(r: { id: number; title?: string; destination?: string }) {
  try {
    await ElMessageBox.confirm(
      `确定要删除路线「${r.title || r.destination || '未命名'}」吗？删除后不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await routesApi.delete(r.id)
    ElMessage.success('已删除')
    await loadRoutes()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '删除失败')
  }
}

async function loadCompanion() {
  try {
    const list = await companionApi.myPosts()
    store.setMyCompanion(list || [])
  } catch {
    store.setMyCompanion([])
  }
}

async function deleteCompanion(p: { id: number; destination?: string }) {
  try {
    await ElMessageBox.confirm(
      `确定要删除结伴「${p.destination || '未命名'}」吗？关联小队及聊天记录将一并删除，不可恢复。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await companionApi.deletePost(p.id)
    ElMessage.success('已删除')
    await loadCompanion()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '删除失败')
  }
}

async function loadFeeds() {
  try {
    const list = await feedsApi.list()
    const email = store.me?.email
    const nickname = auth.nickname
    const mine = (list || []).filter(
      (f) => f.authorName === email || f.authorName === nickname
    )
    store.setMyFeeds(mine)
    const stats: Record<number, { likeCount: number; commentCount: number }> = {}
    await Promise.all(
      mine.map(async (f) => {
        try {
          const summary = await interactionsApi.summary('feed', f.id)
          stats[f.id] = { likeCount: summary.likeCount ?? 0, commentCount: 0 }
        } catch {
          stats[f.id] = { likeCount: 0, commentCount: 0 }
        }
      })
    )
    feedStats.value = stats
  } catch {
    store.setMyFeeds([])
    feedStats.value = {}
  }
}

async function loadNotes() {
  const meId = store.me?.id
  if (!meId) {
    store.setMyNotes([])
    return
  }
  try {
    const list = await userApi.userNotes(meId)
    store.setMyNotes(list || [])
  } catch {
    store.setMyNotes([])
  }
}

async function loadFavorites() {
  try {
    const list = await interactionsApi.myFavorites()
    const feedIds = (list || [])
      .filter((item) => (item.targetType?.toLowerCase?.() || item.targetType) === 'feed')
      .map((item) => item.targetId)
    let feedMap: Map<number, { content?: string; imageUrlsJson?: string | null; authorName?: string }> = new Map()
    if (feedIds.length > 0) {
      try {
        const { feedsApi } = await import('../api')
        const feeds = await feedsApi.list()
        ;(feeds || []).forEach((f) =>
          feedMap.set(f.id, { content: f.content, imageUrlsJson: f.imageUrlsJson, authorName: f.authorName })
        )
      } catch {
        // ignore
      }
    }
    const items: FavoriteItem[] = (list || []).map((item) => {
      const type = (item.targetType?.toLowerCase?.() || item.targetType) as FavoriteItem['type']
      const id = item.targetId
      const feed = type === 'feed' ? feedMap.get(id) : undefined
      let title: string | undefined
      let coverImage: string | undefined
      let authorName: string | undefined
      if (feed) {
        title = feed.content ? (feed.content.length > 60 ? feed.content.slice(0, 60) + '…' : feed.content) : undefined
        if (feed.imageUrlsJson) {
          try {
            const arr = JSON.parse(feed.imageUrlsJson)
            if (Array.isArray(arr) && arr[0]) coverImage = arr[0]
          } catch {}
        }
        authorName = feed.authorName
      }
      return {
        type,
        id,
        title,
        destination: undefined,
        coverImage,
        authorName,
        nickname: authorName,
      }
    })
    store.setFavorites(items)
  } catch {
    store.setFavorites([])
  }
}

onMounted(async () => {
  await loadMe()
  loadFeeds()
  loadRoutes()
  loadCompanion()
  loadNotes()
  loadFavorites()
})

watch(activeTab, (tab) => {
  if (tab === 'routes') loadRoutes()
  if (tab === 'companion') loadCompanion()
  if (tab === 'feeds') loadFeeds()
  if (tab === 'notes') loadNotes()
  if (tab === 'favorites') loadFavorites()
})

watch(feedKeyword, () => { feedPage.value = 1 })
watch(noteKeyword, () => { notePage.value = 1 })
watch([routeKeyword, routeStatusFilter], () => { routePage.value = 1 })
watch([companionKeyword, companionStatusFilter], () => { companionPage.value = 1 })
watch([favoriteTypeFilter, favoriteKeyword], () => { favoritePage.value = 1 })

function handleMenuSelect(key: string) {
  activeTab.value = key as ProfileTab
}

function onProfileUpdated(profile: MeDetail) {
  store.setMe(profile)
  auth.setProfile(profile.nickname ?? null, profile.reputationLevel ?? null)
  if (profile?.id) {
    userApi.getPublicProfile(profile.id).then((p) => (publicProfile.value = p)).catch(() => (publicProfile.value = null))
  }
}

function goToSettings() {
  activeTab.value = 'security'
}

function goToHomepage() {
  if (store.me?.id) router.push({ name: 'user-profile', params: { id: String(store.me.id) } })
}

function goBack() {
  router.push('/')
}

async function openFollowList(type: 'following' | 'followers') {
  followDialogType.value = type
  followDialogVisible.value = true
  followListLoading.value = true
  followingList.value = []
  followersList.value = []
  try {
    if (type === 'following') {
      const list = await userApi.myFollowing()
      followingList.value = list || []
    } else {
      const list = await userApi.myFollowers()
      followersList.value = list || []
    }
  } catch {
    ElMessage.error('加载失败，请稍后重试')
  } finally {
    followListLoading.value = false
  }
}

function goUserProfile(userId: number) {
  router.push({ name: 'user-profile', params: { id: String(userId) } })
}

function daysBetween(start: string, end: string) {
  if (!start || !end) return 0
  return Math.max(1, Math.round((new Date(end).getTime() - new Date(start).getTime()) / (24 * 3600 * 1000)) + 1)
}

function routeStatus(plan: { startDate: string; endDate: string }) {
  const end = new Date(plan.endDate)
  const now = new Date()
  if (now > end) return '已完成'
  if (now >= new Date(plan.startDate) && now <= end) return '进行中'
  return '未出行'
}

function goRoute(id: number) {
  router.push(`/routes/${id}`)
}

function goCompanion(id: number) {
  router.push(`/companion/${id}`)
}

function goNote(id: number) {
  router.push({ name: 'note-detail', params: { id: String(id) } })
}

function goNoteEdit(id: number) {
  router.push({ name: 'note-edit', params: { id: String(id) } })
}

function handleDeleteFeed(id: number) {
  ElMessageBox.confirm('确定删除这条动态吗？', '提示', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      store.removeFeed(id)
      ElMessage.success('已删除')
    })
    .catch(() => {})
}

const deletingNoteId = ref<number | null>(null)

async function handleDeleteNote(id: number) {
  const confirmed = await ElMessageBox.confirm('确定删除这篇游记吗？删除后无法恢复。', '提示', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).catch(() => false)
  if (!confirmed) return
  deletingNoteId.value = id
  try {
    await notesApi.delete(id)
    store.removeNote(id)
    ElMessage.success('已删除')
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败，请稍后重试')
  } finally {
    deletingNoteId.value = null
  }
}

function onFeedPageChange(p: number) {
  feedPage.value = p
}

function onNotePageChange(p: number) {
  notePage.value = p
}

function onRoutePageChange(p: number) {
  routePage.value = p
}

function onCompanionPageChange(p: number) {
  companionPage.value = p
}

function onFavoritePageChange(p: number) {
  favoritePage.value = p
}

function handleDeactivate() {
  ElMessageBox.confirm('账号注销后数据无法恢复，确定要继续吗？', '注销账号', {
    confirmButtonText: '确认注销',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      ElMessage.info('账号注销功能需后端支持，请联系管理员')
    })
    .catch(() => {})
}

const removingFavoriteId = ref<string | null>(null)

async function handleRemoveFavorite(f: FavoriteItem) {
  const key = `${f.type}-${f.id}`
  if (removingFavoriteId.value === key) return
  removingFavoriteId.value = key
  try {
    await interactionsApi.unfavorite(f.type, f.id)
    store.removeFavorite(f.type, f.id)
    ElMessage.success('已取消收藏')
  } catch (e: any) {
    ElMessage.error(e?.message || '取消收藏失败，请稍后重试')
  } finally {
    removingFavoriteId.value = null
  }
}

function favoriteTypeLabel(type: string) {
  const map: Record<string, string> = {
    note: '游记',
    route: '路线',
    companion: '结伴',
    feed: '动态',
    spot: '景点',
  }
  return map[type] || type
}

function goToFavorite(f: FavoriteItem) {
  const id = String(f.id)
  if (f.type === 'note') router.push({ name: 'note-detail', params: { id } })
  else if (f.type === 'route') router.push({ name: 'route-detail', params: { id } })
  else if (f.type === 'companion') router.push({ name: 'companion-detail', params: { id } })
  else if (f.type === 'feed') router.push({ name: 'feed', query: { highlight: f.id } })
  else if (f.type === 'spot') router.push({ name: 'spot-detail', params: { id } })
}

function noteStatusLabel() {
  return '已发布'
}

function getFeedCover(f: { imageUrlsJson?: string | null; id: number }): string {
  if (f.imageUrlsJson) {
    try {
      const arr = JSON.parse(f.imageUrlsJson)
      if (Array.isArray(arr) && arr[0]) return arr[0]
    } catch {}
  }
  return `https://picsum.photos/seed/feed${f.id}/400/250`
}

function getFeedTitle(content: string, maxLen = 40): string {
  if (!content) return '无标题'
  return content.length > maxLen ? content.slice(0, maxLen) + '…' : content
}

function getFavoriteCover(f: FavoriteItem): string {
  if (f.coverImage) return f.coverImage
  if (f.type === 'spot') return spotStore.getSpotBrief(f.id).imageUrl
  const seeds: Record<string, string> = { note: 'n', route: 'r', companion: 'c', feed: 'f', spot: 's' }
  return `https://picsum.photos/seed/${seeds[f.type] ?? 'fav'}${f.id}/400/250`
}

function getFavoriteTitle(f: FavoriteItem): string {
  if (f.title || f.destination || f.nickname) return f.title || f.destination || f.nickname || ''
  if (f.type === 'spot') return spotStore.getSpotBrief(f.id).name
  return `${favoriteTypeLabel(f.type)} #${f.id}`
}
</script>

<template>
  <div class="profile-page">
    <div class="profile-topbar">
      <el-button :icon="ArrowLeft" circle class="back-btn" @click="goBack" />
    </div>
    <div class="profile-layout">
      <!-- 左侧：个人信息头部 + 功能导航 -->
      <aside class="profile-left">
        <header class="profile-header">
          <div class="header-row-1">
            <div class="header-avatar-wrap">
              <el-avatar :size="72" class="header-avatar">
                <img v-if="store.me?.avatar" :src="store.me.avatar" alt="avatar" />
                <span v-else>{{ (store.me?.nickname || auth.nickname || '我').charAt(0) }}</span>
              </el-avatar>
            </div>
            <div class="header-name-wrap">
              <h1 class="header-name">{{ store.me?.nickname || auth.nickname || '旅友' }}</h1>
              <el-tag type="warning" effect="plain" size="small" class="header-badge">{{ levelLabel }}</el-tag>
            </div>
          </div>
          <p v-if="store.me?.slogan" class="header-slogan">「{{ store.me.slogan }}」</p>
          <p v-else class="header-slogan placeholder">写下你的旅行宣言</p>

          <div class="header-stats">
            <div class="stat-item stat-item-clickable" @click="openFollowList('following')">
              <span class="stat-value">{{ stats.following }}</span>
              <span class="stat-label">关注</span>
            </div>
            <div class="stat-item stat-item-clickable" @click="openFollowList('followers')">
              <span class="stat-value">{{ stats.followers }}</span>
              <span class="stat-label">粉丝</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ stats.liked }}</span>
              <span class="stat-label">获赞</span>
            </div>
            <div class="stat-item">
              <span class="stat-value">{{ stats.publishCount }}</span>
              <span class="stat-label">发布</span>
            </div>
          </div>

          <div class="header-actions">
            <el-button type="primary" size="small" @click="editDialogVisible = true">编辑资料</el-button>
            <el-button :icon="Setting" size="small" @click="goToSettings">设置</el-button>
            <el-button size="small" @click="goToHomepage">我的主页</el-button>
          </div>
        </header>

        <el-menu
          :default-active="activeTab"
          class="profile-menu"
          @select="handleMenuSelect"
        >
          <el-menu-item
            v-for="item in menuItems"
            :key="item.key"
            :index="item.key"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <span>{{ item.label }}</span>
          </el-menu-item>
        </el-menu>
      </aside>

      <!-- 右侧：内容展示区 -->
      <main class="profile-right">
        <!-- 我的动态（默认） -->
        <section v-show="activeTab === 'feeds'" class="content-panel">
          <div class="panel-head">
            <h2>我的动态</h2>
            <el-button type="primary" @click="router.push('/community')">去社区发布</el-button>
          </div>
          <div v-if="store.myFeeds.length > 0" class="query-row">
            <el-input
              v-model="feedKeyword"
              placeholder="搜索动态内容"
              clearable
              class="query-input"
              style="max-width: 240px"
            />
          </div>
          <div v-if="store.myFeeds.length === 0" class="empty">
            <el-empty description="还没有发布动态">
              <template #image>
                <span class="empty-icon">✨</span>
              </template>
              <el-button type="primary" @click="router.push('/community')">去社区发布</el-button>
            </el-empty>
          </div>
          <template v-else>
            <div v-if="filteredFeeds.length === 0" class="empty">
              <el-empty description="没有匹配的动态" />
            </div>
            <template v-else>
              <p v-if="feedTotal > 0" class="list-summary">共 {{ feedTotal }} 条</p>
              <ul class="unified-content-list">
                <li v-for="f in paginatedFeeds" :key="f.id" class="unified-content-row">
                  <div class="unified-content-image">
                    <img :src="getFeedCover(f)" :alt="getFeedTitle(f.content)" />
                  </div>
                  <div class="unified-content-body">
                    <h4 class="unified-content-title">{{ getFeedTitle(f.content, 60) }}</h4>
                    <div class="unified-content-stats">
                      <span v-if="(feedStats[f.id]?.likeCount ?? 0) > 0" class="stat-item-inline">点赞 {{ feedStats[f.id]?.likeCount }}</span>
                      <span v-if="(feedStats[f.id]?.commentCount ?? 0) > 0" class="stat-item-inline">评论 {{ feedStats[f.id]?.commentCount }}</span>
                    </div>
                    <div class="unified-content-actions">
                      <el-button link type="primary" size="small" @click.stop="router.push('/community')">查看</el-button>
                      <el-button link type="danger" size="small" @click.stop="handleDeleteFeed(f.id)">删除</el-button>
                    </div>
                  </div>
                </li>
              </ul>
              <div v-if="feedTotal > PAGE_SIZE" class="pagination-wrap">
                <el-pagination
                  :current-page="feedPage"
                  :page-size="PAGE_SIZE"
                  :total="feedTotal"
                  layout="prev, pager, next"
                  @current-change="onFeedPageChange"
                />
              </div>
            </template>
          </template>
        </section>

        <!-- 我的游记 -->
        <section v-show="activeTab === 'notes'" class="content-panel">
          <div class="panel-head">
            <h2>我的游记</h2>
            <el-button type="primary" @click="router.push('/notes/create')">写游记</el-button>
          </div>
          <div v-if="store.myNotes.length > 0" class="query-row">
            <el-input
              v-model="noteKeyword"
              placeholder="搜索标题或目的地"
              clearable
              class="query-input"
              style="max-width: 240px"
            />
          </div>
          <div v-if="store.myNotes.length === 0" class="empty">
            <el-empty description="还没有写过游记">
              <template #image>
                <span class="empty-icon">📝</span>
              </template>
              <el-button type="primary" @click="router.push('/notes/create')">写游记</el-button>
            </el-empty>
          </div>
          <template v-else>
            <div v-if="filteredNotes.length === 0" class="empty">
              <el-empty description="没有匹配的游记" />
            </div>
            <div v-else>
              <div class="note-card-grid">
                <article
                  v-for="n in paginatedNotes"
                  :key="n.id"
                  class="note-card-unified"
                  @click="goNote(n.id)"
                >
                  <div class="note-card-cover">
                    <img :src="(n.coverImage && String(n.coverImage)) || `https://picsum.photos/seed/note${n.id ?? 0}/400/250`" :alt="n.title" />
                  </div>
                  <div class="note-card-body">
                    <div class="note-card-author">
                      <el-avatar :size="28" class="note-author-avatar">
                        <img v-if="store.me?.avatar" :src="store.me.avatar" alt="" />
                        <span v-else>{{ (n.authorName || store.me?.nickname || '我').charAt(0) }}</span>
                      </el-avatar>
                      <span class="note-author-name">{{ n.authorName || store.me?.nickname || '我' }}</span>
                    </div>
                    <h4 class="note-card-title">{{ n.title }}</h4>
                    <div class="note-card-footer">
                      <div class="note-card-stats">
                        <span v-if="(n.likeCount ?? 0) > 0" class="stat-with-icon">
                          <HeartIcon :filled="false" class="stat-icon" />
                          {{ n.likeCount }}
                        </span>
                        <span v-if="(n.commentCount ?? 0) > 0" class="stat-with-icon">
                          <el-icon class="stat-icon"><ChatDotRound /></el-icon>
                          {{ n.commentCount }}
                        </span>
                      </div>
                      <div class="note-card-actions">
                        <el-button link type="primary" size="small" @click.stop="goNoteEdit(n.id)">编辑</el-button>
                        <el-button link type="danger" size="small" :loading="deletingNoteId === n.id" @click.stop="handleDeleteNote(n.id)">删除</el-button>
                      </div>
                    </div>
                  </div>
                </article>
              </div>
              <div v-if="noteTotal > PAGE_SIZE" class="pagination-wrap">
                <el-pagination
                  :current-page="notePage"
                  :page-size="PAGE_SIZE"
                  :total="noteTotal"
                  layout="prev, pager, next"
                  @current-change="onNotePageChange"
                />
              </div>
            </div>
          </template>
        </section>

        <!-- 我的路线 -->
        <section v-show="activeTab === 'routes'" class="content-panel">
          <div class="panel-head">
            <h2>我的路线</h2>
            <el-button type="primary" @click="router.push('/routes/create')">去规划</el-button>
          </div>
          <div v-if="store.myRoutes.length > 0" class="query-row">
            <el-input
              v-model="routeKeyword"
              placeholder="搜索标题或目的地"
              clearable
              class="query-input"
              style="max-width: 200px"
            />
            <el-select v-model="routeStatusFilter" placeholder="状态" clearable class="query-select" style="width: 120px">
              <el-option label="全部" value="" />
              <el-option label="未出行" value="未出行" />
              <el-option label="进行中" value="进行中" />
              <el-option label="已完成" value="已完成" />
            </el-select>
          </div>
          <div v-if="store.myRoutes.length === 0" class="empty">
            <el-empty description="还没有规划过路线">
              <template #image>
                <span class="empty-icon">🗺️</span>
              </template>
              <el-button type="primary" @click="router.push('/routes/create')">去规划</el-button>
            </el-empty>
          </div>
          <template v-else>
            <div v-if="filteredRoutes.length === 0" class="empty">
              <el-empty description="没有匹配的路线" />
            </div>
            <div v-else>
              <div class="route-card-grid">
                <div v-for="r in paginatedRoutes" :key="r.id" class="route-card-wrap">
                  <div class="route-card-status">
                    <el-tag size="small" :type="routeStatus(r) === '已完成' ? 'success' : routeStatus(r) === '进行中' ? 'warning' : 'info'">
                      {{ routeStatus(r) }}
                    </el-tag>
                  </div>
                  <el-button
                    type="danger"
                    size="small"
                    circle
                    class="route-delete-btn"
                    title="删除路线"
                    @click.stop="deleteRoute(r)"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                  <RouteCard
                    :cover="`https://picsum.photos/seed/r${r.id}/400/250`"
                    :title="r.title || r.destination"
                    :tags="[r.destination].filter(Boolean)"
                    :days="daysBetween(r.startDate, r.endDate)"
                    :budget-min="r.budget ?? 0"
                    :budget-max="r.budget ?? 0"
                    :route-id="r.id"
                  />
                </div>
              </div>
              <div v-if="routeTotal > PAGE_SIZE" class="pagination-wrap">
                <el-pagination
                  :current-page="routePage"
                  :page-size="PAGE_SIZE"
                  :total="routeTotal"
                  layout="prev, pager, next"
                  @current-change="onRoutePageChange"
                />
              </div>
            </div>
          </template>
        </section>

        <!-- 我的结伴 -->
        <section v-show="activeTab === 'companion'" class="content-panel">
          <div class="panel-head">
            <h2>我的结伴</h2>
            <el-button type="primary" @click="router.push('/companion/create')">发布结伴</el-button>
          </div>
          <div v-if="store.myCompanion.length > 0" class="query-row">
            <el-input
              v-model="companionKeyword"
              placeholder="搜索目的地"
              clearable
              class="query-input"
              style="max-width: 200px"
            />
            <el-select v-model="companionStatusFilter" placeholder="状态" clearable class="query-select" style="width: 120px">
              <el-option label="全部" value="" />
              <el-option label="招募中" value="招募中" />
              <el-option label="已过期" value="已过期" />
            </el-select>
          </div>
          <div v-if="store.myCompanion.length === 0" class="empty">
            <el-empty description="还没有发布结伴">
              <template #image>
                <span class="empty-icon">👋</span>
              </template>
              <el-button type="primary" @click="router.push('/companion/create')">发布结伴</el-button>
            </el-empty>
          </div>
          <template v-else>
            <div v-if="filteredCompanion.length === 0" class="empty">
              <el-empty description="没有匹配的结伴" />
            </div>
            <div v-else>
              <div class="companion-card-grid">
                <article
                  v-for="p in paginatedCompanion"
                  :key="p.id"
                  class="companion-card"
                  @click="goCompanion(p.id)"
                >
                  <div class="card-top">
                    <div class="user-cell">
                      <div class="avatar-wrap">
                        <img v-if="store.me?.avatar" :src="store.me.avatar" :alt="store.me?.nickname" class="avatar" />
                        <span v-else class="avatar-placeholder">{{ (store.me?.nickname || '我').charAt(0) }}</span>
                      </div>
                      <div class="user-meta">
                        <span class="nickname">{{ store.me?.nickname || '旅友' }}</span>
                        <el-tag size="small" :type="companionStatus(p) === '招募中' ? 'success' : 'info'" effect="plain" class="status-badge">
                          {{ companionStatus(p) }}
                        </el-tag>
                      </div>
                    </div>
                  </div>

                  <div class="card-trip">
                    <h3 class="trip-dest">{{ p.destination }}</h3>
                    <p class="trip-date">
                      {{ formatDate(p.startDate) }} – {{ formatDate(p.endDate) }}
                      <span class="trip-days">{{ daysBetween(p.startDate, p.endDate) }} 天</span>
                    </p>
                  </div>

                  <div class="card-tags">
                    <span v-for="tag in companionTags(p)" :key="tag" class="trip-tag">{{ tag }}</span>
                  </div>

                  <p class="card-desc">
                    {{ (p.expectedMateDesc || '一起结伴，开心出行～').slice(0, 60) }}{{ (p.expectedMateDesc && p.expectedMateDesc.length > 60) ? '…' : '' }}
                  </p>

                  <div class="card-actions">
                    <el-button size="default" @click.stop="goCompanion(p.id)">
                      查看详情
                    </el-button>
                    <el-button type="danger" size="default" @click.stop="deleteCompanion(p)">
                      删除
                    </el-button>
                  </div>
                </article>
              </div>
              <div v-if="companionTotal > PAGE_SIZE" class="pagination-wrap">
                <el-pagination
                  :current-page="companionPage"
                  :page-size="PAGE_SIZE"
                  :total="companionTotal"
                  layout="prev, pager, next"
                  @current-change="onCompanionPageChange"
                />
              </div>
            </div>
          </template>
        </section>

        <!-- 我的收藏 -->
        <section v-show="activeTab === 'favorites'" class="content-panel">
          <h2>我的收藏</h2>
          <div v-if="store.favorites.length > 0" class="query-row">
            <el-select v-model="favoriteTypeFilter" placeholder="类型" clearable class="query-select" style="width: 120px">
              <el-option label="全部" value="" />
              <el-option label="游记" value="note" />
              <el-option label="路线" value="route" />
              <el-option label="结伴" value="companion" />
              <el-option label="动态" value="feed" />
              <el-option label="景点" value="spot" />
            </el-select>
            <el-input
              v-model="favoriteKeyword"
              placeholder="搜索收藏"
              clearable
              class="query-input"
              style="max-width: 200px"
            />
          </div>
          <div v-if="store.favorites.length === 0" class="empty">
            <el-empty description="收藏的内容会出现在这里">
              <template #image>
                <span class="empty-icon">❤️</span>
              </template>
              <el-button @click="router.push('/community')">去社区逛逛</el-button>
            </el-empty>
          </div>
          <template v-else>
            <div v-if="filteredFavorites.length === 0" class="empty">
              <el-empty description="没有匹配的收藏" />
            </div>
            <div v-else>
              <ul class="unified-content-list">
                <li
                  v-for="f in paginatedFavorites"
                  :key="`${f.type}-${f.id}`"
                  class="unified-content-row"
                  @click="goToFavorite(f)"
                >
                  <div class="unified-content-image">
                    <img :src="getFavoriteCover(f)" :alt="f.title || favoriteTypeLabel(f.type)" />
                  </div>
                  <div class="unified-content-body">
                    <h4 class="unified-content-title">{{ getFavoriteTitle(f) }}</h4>
                    <div v-if="f.authorName || f.nickname" class="unified-content-author">
                      <el-avatar :size="24" class="author-avatar">
                        {{ (f.authorName || f.nickname || '').charAt(0) }}
                      </el-avatar>
                      <span class="author-nickname">{{ f.authorName || f.nickname }}</span>
                    </div>
                    <div class="unified-content-stats">
                      <el-tag size="small" type="info" effect="plain">{{ favoriteTypeLabel(f.type) }}</el-tag>
                    </div>
                    <div class="unified-content-actions">
                      <el-button link type="primary" size="small" @click.stop="goToFavorite(f)">查看</el-button>
                      <el-button
                        link
                        type="danger"
                        size="small"
                        :loading="removingFavoriteId === (f.type + '-' + f.id)"
                        @click.stop="handleRemoveFavorite(f)"
                      >
                        取消收藏
                      </el-button>
                    </div>
                  </div>
                </li>
              </ul>
              <div v-if="favoriteTotal > PAGE_SIZE" class="pagination-wrap">
                <el-pagination
                  :current-page="favoritePage"
                  :page-size="PAGE_SIZE"
                  :total="favoriteTotal"
                  layout="prev, pager, next"
                  @current-change="onFavoritePageChange"
                />
              </div>
            </div>
          </template>
        </section>

        <!-- 账号与安全 -->
        <section v-show="activeTab === 'security'" class="content-panel">
          <h2>账号与安全</h2>
          <el-card shadow="never" class="settings-card">
            <h4>信息管理</h4>
            <p class="text-subtle">修改头像、昵称、简介请在「编辑资料」中操作。</p>
            <el-button type="primary" @click="editDialogVisible = true">编辑资料</el-button>
          </el-card>
          <el-card shadow="never" class="settings-card">
            <h4>安全设置</h4>
            <p class="text-subtle">修改密码、绑定/解绑第三方账号、登录设备管理需后端支持，敬请期待。</p>
          </el-card>
          <el-card shadow="never" class="settings-card">
            <el-form label-width="140px">
              <el-form-item label="谁可以看到我的动态">
                <el-select model-value="public" class="w-200">
                  <el-option value="public" label="所有人" />
                  <el-option value="friends" label="好友" />
                  <el-option value="private" label="仅自己" />
                </el-select>
              </el-form-item>
              <el-form-item label="谁可以看到结伴信息">
                <el-select model-value="public" class="w-200">
                  <el-option value="public" label="所有人" />
                  <el-option value="friends" label="好友" />
                </el-select>
              </el-form-item>
            </el-form>
          </el-card>
          <el-card shadow="never" class="settings-card danger-zone">
            <h4>注销账号</h4>
            <p class="text-subtle">注销后数据无法恢复。</p>
            <el-button type="danger" plain @click="handleDeactivate">申请注销</el-button>
          </el-card>
        </section>
      </main>
    </div>

    <el-dialog
      v-model="followDialogVisible"
      :title="followDialogType === 'following' ? '我的关注' : '我的粉丝'"
      width="420px"
      class="follow-dialog"
      destroy-on-close
    >
      <div v-loading="followListLoading" class="follow-list-wrap">
        <template v-if="followDialogType === 'following'">
          <div v-if="followingList.length === 0" class="follow-list-empty">
            <el-empty description="暂无关注" :image-size="64" />
          </div>
          <ul v-else class="follow-list">
            <li
              v-for="item in followingList"
              :key="item.userId"
              class="follow-list-item"
              @click="goUserProfile(item.userId)"
            >
              <el-avatar :size="40" class="follow-item-avatar">
                <img v-if="item.avatar" :src="item.avatar" :alt="item.nickname" />
                <span v-else>{{ (item.nickname || '旅').charAt(0) }}</span>
              </el-avatar>
              <span class="follow-item-name">{{ item.nickname || '旅友' }}</span>
              <span class="follow-item-time">{{ formatDateTime(item.createdAt) }}</span>
            </li>
          </ul>
        </template>
        <template v-else>
          <div v-if="followersList.length === 0" class="follow-list-empty">
            <el-empty description="暂无粉丝" :image-size="64" />
          </div>
          <ul v-else class="follow-list">
            <li
              v-for="item in followersList"
              :key="item.userId"
              class="follow-list-item"
              @click="goUserProfile(item.userId)"
            >
              <el-avatar :size="40" class="follow-item-avatar">
                <img v-if="item.avatar" :src="item.avatar" :alt="item.nickname" />
                <span v-else>{{ (item.nickname || '旅').charAt(0) }}</span>
              </el-avatar>
              <span class="follow-item-name">{{ item.nickname || '旅友' }}</span>
              <span class="follow-item-time">{{ formatDateTime(item.followedAt) }}</span>
            </li>
          </ul>
        </template>
      </div>
    </el-dialog>

    <EditProfileDialog
      :visible="editDialogVisible"
      :profile="store.me"
      @update:visible="editDialogVisible = $event"
      @updated="onProfileUpdated"
    />
  </div>
</template>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f0f9ff 0%, #f8fafc 20%);
  padding-bottom: 48px;
}

.profile-topbar {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px 20px 0;
}

.back-btn {
  flex-shrink: 0;
}

.profile-layout {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px;
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 32px;
  align-items: start;
}

@media (max-width: 768px) {
  .profile-layout {
    grid-template-columns: 1fr;
    padding: 16px 12px;
  }
}

.profile-left {
  position: sticky;
  top: 88px;
  display: flex;
  flex-direction: column;
  gap: 0;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.profile-header {
  padding: 20px 16px;
  border-bottom: 1px solid #f1f5f9;
}

.header-row-1 {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
}

.header-avatar-wrap {
  flex-shrink: 0;
}

.header-avatar {
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-size: 28px;
  font-weight: 600;
}

.header-name-wrap {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
}

.header-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.3;
}

.header-badge {
  margin: 0;
}

.header-slogan {
  margin: 0 0 12px;
  font-size: 13px;
  color: #0d9488;
  font-style: italic;
  line-height: 1.4;
}

.header-slogan.placeholder {
  color: #94a3b8;
  font-style: normal;
}

.header-stats {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin-bottom: 14px;
  padding: 12px 0;
  border-top: 1px solid #f1f5f9;
  border-bottom: 1px solid #f1f5f9;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.stat-value {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

.stat-label {
  font-size: 12px;
  color: #64748b;
}

.stat-item-clickable {
  cursor: pointer;
}

.stat-item-clickable:hover {
  color: var(--el-color-primary);
}

.stat-item-clickable:hover .stat-value,
.stat-item-clickable:hover .stat-label {
  color: inherit;
}

.header-actions {
  display: flex;
  flex-wrap: nowrap;
  gap: 8px;
  justify-content: flex-start;
}

.header-actions .el-button {
  flex: 1;
  min-width: 0;
}

.profile-menu {
  border-right: none;
}

.profile-menu :deep(.el-menu-item) {
  height: 48px;
  font-size: 14px;
}

.profile-right {
  min-width: 0;
}

.content-panel {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 24px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.content-panel h2 {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

.panel-head h2 {
  margin: 0;
}

.query-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.query-input {
  flex: 0 1 auto;
}

.query-select {
  flex: 0 0 auto;
}

.list-summary {
  margin: 0 0 12px;
  font-size: 13px;
  color: #64748b;
}

.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

.home-style-grid {
  display: grid;
  gap: 24px;
}

.home-style-grid-3 {
  grid-template-columns: repeat(1, 1fr);
}

@media (min-width: 640px) {
  .home-style-grid-3 {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .home-style-grid-3 {
    grid-template-columns: repeat(3, 1fr);
  }
}

.home-style-grid-4 {
  grid-template-columns: repeat(1, 1fr);
}

@media (min-width: 640px) {
  .home-style-grid-4 {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (min-width: 1024px) {
  .home-style-grid-4 {
    grid-template-columns: repeat(4, 1fr);
  }
}

.home-style-card-wrap {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.home-card-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 4px;
}

/* 我的游记 / 我的路线 / 我的结伴：一行三卡，均匀分布 */
.note-card-grid,
.route-card-grid {
  display: grid;
  grid-template-columns: repeat(1, 1fr);
  gap: 24px;
}

@media (min-width: 640px) {
  .note-card-grid,
  .route-card-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}

/* 我的结伴网格：与结伴广场一致的布局 */
.companion-card-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 24px;
  align-items: stretch;
  width: 100%;
}

@media (min-width: 900px) {
  .companion-card-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .companion-card-grid {
    grid-template-columns: 1fr;
  }
}

.note-card-unified {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition: box-shadow 0.2s, transform 0.2s;
}

.note-card-unified:hover {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.note-card-cover {
  aspect-ratio: 16 / 10;
  background: #f1f5f9;
  overflow: hidden;
}

.note-card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.note-card-body {
  padding: 12px 14px;
}

.note-card-author {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.note-author-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #e2e8f0, #cbd5e1);
  color: #64748b;
  font-size: 12px;
}

.note-author-name {
  font-size: 13px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.note-card-title {
  margin: 0 0 8px;
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
}

.note-card-stats {
  display: flex;
  align-items: center;
  gap: 14px;
  font-size: 13px;
  color: #64748b;
}

.stat-with-icon {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
}

.note-card-stats .stat-icon {
  font-size: 14px;
  color: #64748b;
}

.note-card-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 我的路线：与游记一致的卡片宽度，封面 16:10 */
.route-card-wrap {
  position: relative;
}

.route-card-wrap :deep(.rounded-2xl) {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  transition: box-shadow 0.2s, transform 0.2s;
}

.route-card-wrap:hover :deep(.rounded-2xl) {
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.route-card-status {
  position: absolute;
  top: 10px;
  left: 10px;
  z-index: 1;
}

.route-delete-btn {
  position: absolute;
  top: 10px;
  right: 10px;
  z-index: 1;
  opacity: 0.85;
}
.route-card-wrap:hover .route-delete-btn {
  opacity: 1;
}

/* 我的结伴：与结伴广场一致的卡片样式 */
.companion-card-grid .companion-card {
  min-width: 0;
  width: 100%;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 20px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  overflow: hidden;
  cursor: pointer;
  border: none;
}

.companion-card-grid .companion-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.companion-card-grid .companion-card .card-top {
  margin-bottom: 14px;
}

.companion-card-grid .companion-card .user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.companion-card-grid .companion-card .avatar-wrap {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, #5eead4, #0d9488);
}

.companion-card-grid .companion-card .avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.companion-card-grid .companion-card .avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}

.companion-card-grid .companion-card .user-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.companion-card-grid .companion-card .nickname {
  font-weight: 600;
  font-size: 15px;
  color: #1e293b;
}

.companion-card-grid .companion-card .status-badge {
  font-size: 11px;
}

.companion-card-grid .companion-card .card-trip {
  margin-bottom: 12px;
}

.companion-card-grid .companion-card .trip-dest {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
}

.companion-card-grid .companion-card .trip-date {
  margin: 6px 0 0;
  font-size: 14px;
  color: #64748b;
}

.companion-card-grid .companion-card .trip-days {
  margin-left: 8px;
  color: #0d9488;
  font-weight: 500;
}

.companion-card-grid .companion-card .card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.companion-card-grid .companion-card .trip-tag {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  background: #f0fdfa;
  color: #0d9488;
}

.companion-card-grid .companion-card .card-desc {
  flex: 1;
  margin: 0 0 16px;
  font-size: 14px;
  color: #64748b;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.8em;
}

.companion-card-grid .companion-card .card-actions {
  display: flex;
  flex-wrap: nowrap;
  gap: 10px;
  margin-top: auto;
  min-width: 0;
}

.companion-card-grid .companion-card .card-actions .el-button {
  flex: 1;
  min-width: 80px;
}

.companion-card-grid .companion-card:hover .card-actions .el-button--primary:not(.is-disabled) {
  background: #0f766e;
  border-color: #0f766e;
}

.card-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
}

.card-btns {
  display: flex;
  gap: 4px;
}

.empty {
  padding: 48px 20px;
  text-align: center;
}

.empty-icon {
  font-size: 64px;
  line-height: 1;
  display: block;
  margin-bottom: 8px;
}

.empty .el-empty__description {
  margin-top: 8px;
  color: #64748b;
}

.empty .el-button {
  margin-top: 20px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.route-card,
.note-card {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.route-card:hover,
.note-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.route-cover,
.note-cover {
  height: 120px;
  background: #f1f5f9;
}

.route-cover img,
.note-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.note-cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  background: #e2e8f0;
}

.route-body,
.note-body {
  padding: 14px;
}

.route-body h4,
.companion-card h4,
.note-body h4 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.route-meta,
.companion-meta,
.note-meta {
  margin: 0 0 4px;
  font-size: 13px;
  color: #64748b;
}

.fav-grid {
  grid-template-columns: 1fr;
}

.fav-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s, box-shadow 0.2s;
}

.fav-card:hover {
  background: #f1f5f9;
  border-color: #cbd5e1;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.fav-card-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.fav-type-tag {
  flex-shrink: 0;
}

.fav-card-title {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: #334155;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.fav-card:hover .fav-card-title {
  color: var(--el-color-primary);
}

.fav-remove-btn {
  flex-shrink: 0;
}

.unified-content-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.unified-content-row {
  display: flex;
  align-items: stretch;
  gap: 16px;
  padding: 16px 0;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.2s;
}

.unified-content-row:last-child {
  border-bottom: none;
}

.unified-content-row:hover {
  background: #fafafa;
}

.unified-content-image {
  flex-shrink: 0;
  width: 120px;
  height: 80px;
  border-radius: 8px;
  overflow: hidden;
  background: #f1f5f9;
}

.unified-content-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.unified-content-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 6px;
}

.unified-content-title {
  margin: 0;
  font-size: 15px;
  font-weight: 500;
  color: #1e293b;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.unified-content-author {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
}

.unified-content-author .author-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}

.unified-content-author .author-nickname {
  font-size: 13px;
  color: #64748b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unified-content-stats {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #64748b;
}

.stat-item-inline {
  white-space: nowrap;
}

.unified-content-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.settings-card {
  margin-bottom: 20px;
}

.settings-card h4 {
  margin: 0 0 8px;
  font-size: 15px;
  color: #1e293b;
}

.w-200 {
  width: 200px;
}

.danger-zone {
  border-color: #fecaca;
}

.text-subtle {
  color: #64748b;
  font-size: 14px;
  margin: 0 0 12px;
}

.follow-list-wrap {
  min-height: 120px;
  max-height: 400px;
  overflow-y: auto;
}

.follow-list-empty {
  padding: 24px 0;
}

.follow-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.follow-list-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 0;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
  transition: background 0.2s;
}

.follow-list-item:last-child {
  border-bottom: none;
}

.follow-list-item:hover {
  background: #f8fafc;
}

.follow-item-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #e2e8f0, #cbd5e1);
  color: #64748b;
  font-size: 16px;
}

.follow-item-name {
  flex: 1;
  min-width: 0;
  font-size: 14px;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.follow-item-time {
  flex-shrink: 0;
  font-size: 12px;
  color: #94a3b8;
}
</style>
