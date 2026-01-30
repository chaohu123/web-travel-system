<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import {
  ElTabs,
  ElTabPane,
  ElRadioGroup,
  ElRadioButton,
  ElPagination,
  ElCard,
  ElAvatar,
  ElTag,
  ElSkeleton,
  ElEmpty,
  ElButton,
  ElInput,
} from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import DynamicCard from '../components/DynamicCard.vue'
import PublishFeedDialog from '../components/PublishFeedDialog.vue'
import PublishDynamicDialog from '../components/PublishDynamicDialog.vue'
import {
  useCommunityStore,
  CATEGORY_TABS,
  type CategoryTab,
  type RecommendedUser,
} from '../store/community'
import { useAuthStore } from '../store'
import { feedsApi, routesApi, userApi } from '../api'
import type { FeedItem, UserPublicProfile, PlanResponse } from '../api'
import { fetchUnifiedDynamicItems } from '../composables/useCommunityFeed'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const store = useCommunityStore()

const publishVisible = ref(false)
const publishDynamicVisible = ref(false)
const pageSize = 5
const currentPage = ref(1)
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

const searchInput = computed({
  get: () => store.searchKeyword,
  set: (v: string) => store.setSearchKeyword(v),
})

const totalItems = computed(() => store.filteredDynamicItems.length)

const displayedItems = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return store.filteredDynamicItems.slice(start, start + pageSize)
})

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

const hotRoutes = ref<PlanResponse[]>([])
const routeLoading = ref(false)

async function loadHotRoutes() {
  routeLoading.value = true
  try {
    hotRoutes.value = await routesApi.hot(5)
  } catch {
    hotRoutes.value = []
  } finally {
    routeLoading.value = false
  }
}

function formatRouteDateRange(r: PlanResponse) {
  if (!r.startDate || !r.endDate) return ''
  return `${r.startDate} ~ ${r.endDate}`
}

function goRoute(id: number) {
  router.push({ name: 'route-detail', params: { id: String(id) } })
}

async function loadDynamicFeed() {
  store.setDynamicLoading(true)
  try {
    const items = await fetchUnifiedDynamicItems()
    store.setDynamicItems(items)
    currentPage.value = 1
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

function handlePageChange(page: number) {
  currentPage.value = page
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

function onDynamicPublished(type: 'feed' | 'note' | 'route' | 'companion') {
  // 发布成功后刷新动态流
  loadDynamicFeed()
}

async function loadMeProfile() {
  if (!auth.token || auth.userId == null) return
  try {
    meProfile.value = await userApi.getPublicProfile(auth.userId)
  } catch {
    meProfile.value = null
  }
}

onMounted(async () => {
  await loadDynamicFeed()
  loadNotes()
  loadRecommended()
  loadHotRoutes()
  loadMeProfile()
})

function handleSearch() {
  store.setSearchKeyword(searchInput.value.trim())
  currentPage.value = 1
}

watch(activeTab, () => {
  currentPage.value = 1
})
watch(sortOrder, () => {
  currentPage.value = 1
})
watch(() => store.searchKeyword, () => {
  currentPage.value = 1
})

// 从路由 query 参数初始化搜索关键词
watch(
  () => route.query.q,
  (q) => {
    if (typeof q === 'string' && q.trim()) {
      store.setSearchKeyword(q.trim())
    }
  },
  { immediate: true }
)
</script>

<template>
  <div class="community-page">
    <div class="community-layout">
      <main class="main-content">
        <form class="community-main-form" @submit.prevent="handleSearch">
          <!-- 顶部筛选与排序 -->
          <section class="filter-section">
            <!-- 第一行：全部 关注 游记 路线 打卡 结伴 发布动态 -->
            <div class="tabs-with-publish">
              <el-tabs v-model="activeTab" class="category-tabs">
                <el-tab-pane
                  v-for="tab in CATEGORY_TABS"
                  :key="tab.value"
                  :label="tab.label"
                  :name="tab.value"
                />
              </el-tabs>
              <el-button
                type="primary"
                class="publish-btn publish-btn-in-tabs"
                @click="auth.token ? (publishDynamicVisible = true) : router.push({ name: 'login', query: { redirect: '/community' } })"
              >
                <span class="publish-icon">+</span>
                发布动态
              </el-button>
            </div>
            
            <!-- 第二行：搜索框 最新最热 -->
            <div class="search-sort-row">
              <el-input
                v-model="searchInput"
                placeholder="搜索关键词、目的地、话题..."
                clearable
                class="search-input"
                @keyup.enter="handleSearch"
                @clear="handleSearch"
              >
                <template #prefix>
                  <el-icon class="search-input-icon"><Search /></el-icon>
                </template>
                <template #append>
                  <el-button type="primary" @click="handleSearch">搜索</el-button>
                </template>
              </el-input>
              <el-radio-group v-model="sortOrder" size="default" class="sort-radio">
                <el-radio-button :value="'latest'">最新</el-radio-button>
                <el-radio-button :value="'hot'">最热</el-radio-button>
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
              <el-pagination
                v-if="totalItems > 0"
                class="pagination"
                background
                layout="prev, pager, next"
                :page-size="pageSize"
                :current-page="currentPage"
                :total="totalItems"
                @current-change="handlePageChange"
              />
            </div>
          </section>
        </form>
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

        <!-- 热门游记（参考游记详情页侧栏样式） -->
        <el-card class="sidebar-card sidebar-notes-card" shadow="never">
          <template #header>
            <span class="sidebar-title">热门游记</span>
          </template>
          <p class="sidebar-subtitle">按点赞排序的优质游记</p>
          <div v-if="store.noteLoading" class="sidebar-loading">加载中...</div>
          <ul v-else class="sidebar-notes-list">
            <li
              v-for="n in hotNotes"
              :key="n.id"
              class="sidebar-note-item"
              @click="goNote(n.id)"
            >
              <div class="sidebar-note-cover">
                <img
                  :src="n.coverImage || 'https://picsum.photos/seed/note' + n.id + '/160/100'"
                  alt=""
                  @error="(e: Event) => (e.currentTarget as HTMLImageElement).style.display = 'none'"
                />
              </div>
              <div class="sidebar-note-body">
                <div class="sidebar-note-title">{{ n.title }}</div>
                <div class="sidebar-note-meta">
                  {{ n.destination || '目的地未填写' }}
                </div>
                <div class="sidebar-note-extra">
                  <span class="sidebar-note-likes">{{ n.likeCount ?? 0 }} 赞</span>
                </div>
              </div>
            </li>
          </ul>
          <el-button
            v-if="!store.noteLoading && hotNotes.length > 0"
            type="primary"
            text
            class="sidebar-more-btn"
            @click="router.push({ name: 'notes' })"
          >
            查看更多游记
          </el-button>
        </el-card>

        <!-- 热门路线（参考游记详情页关联路线样式） -->
        <el-card class="sidebar-card sidebar-routes-card" shadow="never">
          <template #header>
            <span class="sidebar-title">热门路线</span>
          </template>
          <p class="sidebar-subtitle">旅友常参考的路线规划</p>
          <div v-if="routeLoading" class="sidebar-loading">加载中...</div>
          <template v-else-if="hotRoutes.length > 0">
            <ul class="sidebar-routes-list">
              <li
                v-for="r in hotRoutes"
                :key="r.id"
                class="sidebar-route-item"
                @click="goRoute(r.id)"
              >
                <div class="sidebar-route-brief">
                  <div class="sidebar-route-name">{{ r.title || r.destination }}</div>
                  <div class="sidebar-route-meta">{{ r.destination }} · {{ formatRouteDateRange(r) }}</div>
                </div>
              </li>
            </ul>
            <el-button type="primary" round size="small" class="sidebar-route-btn" @click="router.push({ name: 'routes' })">
              更多路线
            </el-button>
          </template>
          <template v-else>
            <p class="sidebar-text text-subtle">暂无热门路线，去规划一条吧</p>
            <el-button type="primary" round size="small" @click="router.push({ name: 'routes' })">
              去规划
            </el-button>
          </template>
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

    <PublishDynamicDialog
      :visible="publishDynamicVisible"
      @update:visible="publishDynamicVisible = $event"
      @published="onDynamicPublished"
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

/* 第一行：标签 + 发布动态 同一行 */
.tabs-with-publish {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.tabs-with-publish .category-tabs {
  flex: 1;
  min-width: 0;
}

.tabs-with-publish .category-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.tabs-with-publish .category-tabs :deep(.el-tabs__content) {
  display: none;
}

.tabs-with-publish .publish-btn-in-tabs {
  flex-shrink: 0;
  margin-left: 0;
}

@media (max-width: 768px) {
  .tabs-with-publish {
    flex-wrap: wrap;
  }
  .tabs-with-publish .publish-btn-in-tabs {
    width: 100%;
  }
}

.category-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.category-tabs :deep(.el-tabs__nav-wrap) {
  align-items: center;
}

.category-tabs :deep(.el-tabs__nav-next),
.category-tabs :deep(.el-tabs__nav-prev) {
  display: none;
}

/* 第二行：搜索框 + 最新最热 */
.search-sort-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.search-sort-row .search-input {
  flex: 1;
  min-width: 200px;
}

.search-sort-row .sort-radio {
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .search-sort-row {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-sort-row .search-input {
    width: 100%;
  }
  
  .search-sort-row .sort-radio {
    width: 100%;
    justify-content: center;
  }
}

.search-input {
  flex: 1;
  min-width: 0;
}

.search-input-icon {
  font-size: 18px;
  color: #0d9488;
  margin-right: 4px;
}

.search-input :deep(.el-input__wrapper) {
  padding-left: 12px;
}

.search-input :deep(.el-input-group__append) {
  padding: 0;
  background: #0d9488;
  border-color: #0d9488;
}

.search-input :deep(.el-input-group__append .el-button) {
  margin: 0;
  background: transparent;
  border: none;
  color: #fff;
}

.publish-btn {
  flex-shrink: 0;
  white-space: nowrap;
  border-radius: 8px;
  padding: 10px 20px;
  font-weight: 500;
  background: linear-gradient(135deg, #0d9488, #0f766e);
  border: none;
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.3);
  transition: all 0.2s ease;
}

.publish-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(13, 148, 136, 0.4);
}

.publish-icon {
  display: inline-block;
  margin-right: 6px;
  font-size: 18px;
  font-weight: 300;
  line-height: 1;
}

.publish-btn-in-tabs {
  margin-left: 8px;
  vertical-align: middle;
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

.community-main-form {
  margin: 0;
}

.sort-radio :deep(.el-radio-button__inner) {
  border-radius: 8px;
}

.sort-radio :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
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

.pagination {
  align-self: center;
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
  /* 取消粘性定位，随页面整体滚动 */
  align-self: flex-start;
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

.sidebar-subtitle {
  margin: 0 0 12px;
  font-size: 12px;
  color: #6b7280;
}

/* 热门游记：参考游记详情页推荐游记卡片 */
.sidebar-notes-card :deep(.el-card__body) {
  padding-top: 0;
}

.sidebar-notes-list {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sidebar-note-item {
  display: flex;
  gap: 10px;
  cursor: pointer;
  padding: 8px;
  margin: 0 -8px;
  border-radius: 12px;
  transition: all 0.2s ease;
}

.sidebar-note-item:hover {
  background: #f8fafc;
  transform: translateX(4px);
}

.sidebar-note-cover {
  flex: 0 0 72px;
  height: 54px;
  border-radius: 10px;
  overflow: hidden;
  background: #f1f5f9;
  transition: transform 0.2s ease;
}

.sidebar-note-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.sidebar-note-item:hover .sidebar-note-cover {
  transform: scale(1.05);
}

.sidebar-note-body {
  flex: 1;
  min-width: 0;
}

.sidebar-note-title {
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

.sidebar-note-item:hover .sidebar-note-title {
  color: #0f766e;
}

.sidebar-note-meta {
  margin-top: 2px;
  font-size: 12px;
  color: #6b7280;
}

.sidebar-note-extra {
  margin-top: 4px;
}

.sidebar-note-likes {
  font-size: 12px;
  color: #0d9488;
  font-weight: 500;
}

.sidebar-more-btn {
  width: 100%;
  margin-top: 12px;
  padding: 8px 0;
}

/* 热门路线：参考游记详情页关联路线 */
.sidebar-routes-card :deep(.el-card__body) {
  padding-top: 0;
}

.sidebar-routes-list {
  list-style: none;
  padding: 0;
  margin: 0 0 12px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.sidebar-route-item {
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  cursor: pointer;
  transition: all 0.2s ease;
  border: 1px solid transparent;
}

.sidebar-route-item:hover {
  background: #ecfeff;
  border-color: rgba(15, 118, 110, 0.15);
  transform: translateX(4px);
}

.sidebar-route-brief {
  margin: 0;
}

.sidebar-route-name {
  font-size: 14px;
  font-weight: 500;
  color: #111827;
  margin-bottom: 4px;
}

.sidebar-route-meta {
  font-size: 12px;
  color: #6b7280;
}

.sidebar-route-btn {
  width: 100%;
  margin-top: 4px;
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
