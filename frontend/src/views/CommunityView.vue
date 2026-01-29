<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  ElTabs,
  ElTabPane,
  ElRadioGroup,
  ElRadioButton,
  ElCard,
  ElAvatar,
  ElTag,
  ElSkeleton,
  ElEmpty,
  ElButton,
} from 'element-plus'
import DynamicCard from '../components/DynamicCard.vue'
import PublishFeedDialog from '../components/PublishFeedDialog.vue'
import {
  useCommunityStore,
  CATEGORY_TABS,
  type CategoryTab,
  type RecommendedUser,
} from '../store/community'
import { useAuthStore } from '../store'
import { feedsApi } from '../api'
import type { FeedItem } from '../api'
import type { UserPublicProfile } from '../api'
import { fetchUnifiedDynamicItems } from '../composables/useCommunityFeed'
import { userApi } from '../api'

const router = useRouter()
const auth = useAuthStore()
const store = useCommunityStore()

const publishVisible = ref(false)
const loadingMore = ref(false)
const pageSize = 12
const displayCount = ref(pageSize)
const sentinelRef = ref<HTMLElement | null>(null)
/** 当前用户公开资料（关注/粉丝、旅行风格） */
const meProfile = ref<UserPublicProfile | null>(null)

const activeTab = computed({
  get: () => store.categoryTab,
  set: (v: CategoryTab) => store.setCategoryTab(v),
})

const sortOrder = computed({
  get: () => store.sortOrder,
  set: (v: 'latest' | 'hot') => store.setSortOrder(v),
})

const displayedItems = computed(() =>
  store.filteredDynamicItems.slice(0, displayCount.value)
)

const noMore = computed(
  () => displayCount.value >= store.filteredDynamicItems.length
)

const isEmpty = computed(() => {
  if (store.dynamicLoading) return false
  return store.filteredDynamicItems.length === 0
})

/** 关注 Tab：后端未提供关注流时始终展示空状态引导 */
const isFollowingEmpty = computed(
  () => store.categoryTab === 'following' && !store.dynamicLoading
)

const hotNotes = computed(() =>
  [...(store.featuredNotes || [])]
    .sort((a, b) => (b.likeCount ?? 0) - (a.likeCount ?? 0))
    .slice(0, 5)
)

async function loadDynamicFeed() {
  store.setDynamicLoading(true)
  try {
    const items = await fetchUnifiedDynamicItems()
    store.setDynamicItems(items)
    displayCount.value = pageSize
  } catch {
    store.setDynamicItems([])
  } finally {
    store.setDynamicLoading(false)
  }
}

function loadNotes() {
  store.setNoteLoading(true)
  import('../api').then(({ notesApi }) =>
    notesApi
      .list()
      .then((list) => store.setFeaturedNotes(list || []))
      .catch(() => store.setFeaturedNotes([]))
      .finally(() => store.setNoteLoading(false))
  )
}

function loadRecommended() {
  const mock: RecommendedUser[] = [
    { id: 1, nickname: '小鹿', avatar: '', creditLevel: '金牌', tags: ['摄影', '美食'] },
    { id: 2, nickname: '行者老张', avatar: '', creditLevel: '钻石', tags: ['自驾', '风光'] },
    { id: 3, nickname: '桃桃', avatar: '', creditLevel: '银牌', tags: ['休闲', '夜市'] },
  ]
  store.setRecommendedUsers(mock)
}

function loadMore() {
  if (noMore.value || loadingMore.value) return
  loadingMore.value = true
  setTimeout(() => {
    displayCount.value += pageSize
    loadingMore.value = false
  }, 200)
}

function goNote(id: number) {
  router.push({ name: 'note-detail', params: { id: String(id) } })
}

function goUser(id: number) {
  router.push({ name: 'user-profile', params: { id: String(id) } })
}

function onPublished(feed: FeedItem) {
  store.prependFeed(feed)
}

async function loadMeProfile() {
  if (!auth.token || auth.userId == null) return
  try {
    meProfile.value = await userApi.getPublicProfile(auth.userId)
  } catch {
    meProfile.value = null
  }
}

let scrollObserver: IntersectionObserver | null = null
let observedSentinelEl: HTMLElement | null = null
let stopSentinelWatch: (() => void) | null = null

onMounted(async () => {
  await loadDynamicFeed()
  loadNotes()
  loadRecommended()
  loadMeProfile()

  stopSentinelWatch = watch(sentinelRef, (el) => {
    if (!el) return
    scrollObserver = new IntersectionObserver(
      (entries) => {
        if (!entries[0]?.isIntersecting) return
        if (noMore.value || loadingMore.value || store.dynamicLoading) return
        loadMore()
      },
      { rootMargin: '200px', threshold: 0 }
    )
    scrollObserver.observe(el)
    observedSentinelEl = el
    stopSentinelWatch?.()
  }, { flush: 'post' })
  onUnmounted(() => {
    stopSentinelWatch?.()
    if (scrollObserver && observedSentinelEl) {
      scrollObserver.unobserve(observedSentinelEl)
      scrollObserver.disconnect()
    }
  })
})

watch(activeTab, () => {
  displayCount.value = pageSize
})
watch(sortOrder, () => {
  displayCount.value = pageSize
})
</script>

<template>
  <div class="community-page">
    <div class="community-layout">
      <main class="main-content">
        <!-- 顶部筛选与排序 -->
        <section class="filter-section">
          <el-tabs v-model="activeTab" class="category-tabs">
            <el-tab-pane
              v-for="tab in CATEGORY_TABS"
              :key="tab.value"
              :label="tab.label"
              :name="tab.value"
            />
          </el-tabs>
          <div class="sort-row">
            <el-radio-group v-model="sortOrder" size="default">
              <el-radio-button value="latest">最新</el-radio-button>
              <el-radio-button value="hot">最热</el-radio-button>
            </el-radio-group>
          </div>
        </section>

        <!-- 动态流：骨架屏 / 空状态 / 列表 -->
        <section class="feed-section">
          <div v-if="store.dynamicLoading" class="skeleton-list">
            <el-card v-for="i in 4" :key="i" class="skeleton-card" shadow="never">
              <el-skeleton :rows="6" animated />
            </el-card>
          </div>

          <div v-else-if="isFollowingEmpty" class="empty-wrap">
            <el-empty description="暂无关注动态">
              <template #image>
                <div class="empty-illus">👋</div>
              </template>
              <template #description>
                <p class="empty-desc">登录后查看关注的人的动态，或去发现更多旅友</p>
              </template>
              <el-button v-if="auth.token" type="primary" @click="router.push({ name: 'companion-list' })">
                发现结伴
              </el-button>
              <el-button v-else type="primary" @click="router.push({ name: 'login' })">
                去登录
              </el-button>
            </el-empty>
          </div>

          <div v-else-if="isEmpty" class="empty-wrap">
            <el-empty description="当前分类暂无内容">
              <template #image>
                <div class="empty-illus">📷</div>
              </template>
              <template #description>
                <p class="empty-desc">来发布第一条动态，或换个分类看看吧</p>
              </template>
              <el-button v-if="auth.token" type="primary" @click="publishVisible = true">
                发布动态
              </el-button>
              <el-button v-else type="primary" @click="router.push({ name: 'login' })">
                登录后发布
              </el-button>
            </el-empty>
          </div>

          <div v-else class="feed-list">
            <DynamicCard
              v-for="d in displayedItems"
              :key="`${d.type}-${d.id}`"
              :item="d"
            />
            <div ref="sentinelRef" class="load-trigger" />
            <div class="load-more-row">
              <el-button
                v-if="!noMore && store.filteredDynamicItems.length > 0"
                :loading="loadingMore"
                text
                type="primary"
                @click="loadMore"
              >
                加载更多
              </el-button>
              <p v-else-if="store.filteredDynamicItems.length > 0" class="no-more">已经到底部</p>
            </div>
          </div>
        </section>
      </main>

      <!-- 右侧辅助区（PC） -->
      <aside class="sidebar">
        <!-- 当前用户卡片：头像、昵称、旅行风格、关注/粉丝 -->
        <el-card v-if="auth.token" class="sidebar-card user-card" shadow="never">
          <div class="user-card-inner" @click="router.push({ name: 'profile' })">
            <el-avatar :size="48" class="user-avatar">
              {{ (auth.nickname || meProfile?.nickname || '我').charAt(0) }}
            </el-avatar>
            <div class="user-meta">
              <span class="user-name">{{ auth.nickname || meProfile?.nickname || '旅友' }}</span>
              <div v-if="meProfile?.followersCount != null || meProfile?.followingCount != null" class="user-stats">
                关注 {{ meProfile?.followingCount ?? 0 }} · 粉丝 {{ meProfile?.followersCount ?? 0 }}
              </div>
              <div v-if="meProfile?.preferences?.travelStyles?.length" class="user-tags">
                <el-tag v-for="s in (meProfile?.preferences?.travelStyles ?? []).slice(0, 3)" :key="s" size="small" effect="plain" class="style-tag">{{ s }}</el-tag>
              </div>
              <el-tag size="small" type="warning" effect="plain" class="my-tag">我的主页</el-tag>
            </div>
          </div>
        </el-card>

        <!-- 推荐旅友 -->
        <el-card class="sidebar-card" shadow="never">
          <template #header>
            <span class="sidebar-title">推荐旅友</span>
          </template>
          <div
            v-for="u in store.recommendedUsers"
            :key="u.id"
            class="rec-user"
            @click="goUser(u.id)"
          >
            <el-avatar :size="40" class="rec-avatar">
              {{ (u.nickname || 'U').charAt(0) }}
            </el-avatar>
            <div class="rec-info">
              <span class="rec-name">{{ u.nickname }}</span>
              <el-tag size="small" type="warning" effect="plain">{{ u.creditLevel }}</el-tag>
              <div class="rec-tags">
                <span v-for="t in u.tags.slice(0, 2)" :key="t" class="rec-tag">{{ t }}</span>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 热门游记 -->
        <el-card class="sidebar-card" shadow="never">
          <template #header>
            <span class="sidebar-title">热门游记</span>
          </template>
          <div v-if="store.noteLoading" class="sidebar-loading">加载中...</div>
          <div v-else class="rec-list">
            <div
              v-for="n in hotNotes"
              :key="n.id"
              class="rec-item"
              @click="goNote(n.id)"
            >
              <span class="rec-item-title">{{ n.title }}</span>
              <span class="rec-item-meta">{{ n.likeCount ?? 0 }} 赞</span>
            </div>
          </div>
        </el-card>

        <!-- 热门路线 -->
        <el-card class="sidebar-card" shadow="never">
          <template #header>
            <span class="sidebar-title">热门路线</span>
          </template>
          <p class="sidebar-text text-subtle">登录后在「我的路线」查看或创建路线</p>
          <el-button type="primary" text @click="router.push({ name: 'routes' })">
            去规划
          </el-button>
        </el-card>
      </aside>
    </div>

    <!-- 发布 FAB -->
    <button
      v-if="auth.token"
      type="button"
      class="fab"
      title="发布动态"
      @click="publishVisible = true"
    >
      <span class="fab-icon">+</span>
    </button>

    <PublishFeedDialog
      :visible="publishVisible"
      @update:visible="publishVisible = $event"
      @published="onPublished"
    />
  </div>
</template>

<style scoped>
.community-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f0f9ff 0%, #f8fafc 18%);
  padding-bottom: 88px;
}

.community-layout {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px;
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 28px;
  align-items: start;
}

@media (max-width: 1024px) {
  .community-layout {
    grid-template-columns: 1fr;
  }
}

.main-content {
  min-width: 0;
}

.filter-section {
  margin-bottom: 20px;
  background: #fff;
  border-radius: 16px;
  padding: 16px 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.category-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.category-tabs :deep(.el-tabs__item) {
  font-size: 15px;
}

.category-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.category-tabs :deep(.el-tabs__active-bar) {
  background-color: #0d9488;
}

.category-tabs :deep(.el-tabs__item.is-active) {
  color: #0d9488;
}

.category-tabs :deep(.el-tabs__ink-bar) {
  background-color: #0d9488;
}

.category-tabs :deep(.el-tabs__indicator) {
  background-color: #0d9488;
}

.category-tabs :deep(.el-tabs__item:hover),
.category-tabs :deep(.el-tabs__item.is-active) {
  color: #0d9488;
}

.sort-row {
  display: flex;
  justify-content: flex-end;
}

.sort-row :deep(.el-radio-button__inner) {
  border-radius: 8px;
}

.sort-row :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background: #0d9488;
  border-color: #0d9488;
}

.feed-section {
  min-height: 200px;
}

.skeleton-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.skeleton-card {
  border-radius: 16px;
  padding: 20px;
}

.skeleton-card :deep(.el-card__body) {
  padding: 20px;
}

.empty-wrap {
  background: #fff;
  border-radius: 16px;
  padding: 48px 24px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.empty-illus {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.8;
}

.empty-desc {
  margin-top: 8px;
  color: #64748b;
  font-size: 14px;
}

.feed-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.load-trigger {
  height: 1px;
  visibility: hidden;
}

.load-more-row {
  text-align: center;
  padding: 20px 0;
}

.no-more {
  margin: 0;
  font-size: 14px;
  color: #94a3b8;
}

.sidebar {
  position: sticky;
  top: 88px;
}

.sidebar-card {
  border-radius: 16px;
  margin-bottom: 20px;
  overflow: hidden;
}

.sidebar-card :deep(.el-card__header) {
  padding: 16px 20px;
  font-weight: 600;
  color: #1e293b;
}

.sidebar-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.sidebar-title {
  font-size: 16px;
}

.user-card-inner {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.user-avatar {
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-weight: 600;
}

.user-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.user-name {
  font-weight: 600;
  color: #1e293b;
}

.user-stats {
  font-size: 12px;
  color: #64748b;
}

.user-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.style-tag {
  font-size: 11px;
}

.my-tag {
  align-self: flex-start;
}

.rec-user {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
  cursor: pointer;
}

.rec-user:last-child {
  border-bottom: none;
}

.rec-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-weight: 600;
}

.rec-info {
  min-width: 0;
}

.rec-name {
  display: block;
  font-weight: 500;
  color: #334155;
}

.rec-tags {
  margin-top: 4px;
}

.rec-tag {
  font-size: 12px;
  color: #64748b;
  margin-right: 6px;
}

.sidebar-loading {
  font-size: 14px;
  color: #94a3b8;
}

.rec-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rec-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  cursor: pointer;
  font-size: 14px;
}

.rec-item-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #334155;
}

.rec-item-meta {
  flex-shrink: 0;
  font-size: 12px;
  color: #94a3b8;
  margin-left: 8px;
}

.sidebar-text {
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 1.5;
}

.text-subtle {
  color: #64748b;
}

.fab {
  position: fixed;
  right: 28px;
  bottom: 28px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: linear-gradient(135deg, #0d9488, #0f766e);
  color: #fff;
  border: none;
  box-shadow: 0 4px 20px rgba(13, 148, 136, 0.45);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  z-index: 40;
}

.fab:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 24px rgba(13, 148, 136, 0.5);
}

.fab-icon {
  font-size: 28px;
  line-height: 1;
  font-weight: 300;
}
</style>
