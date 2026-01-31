<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ElCard,
  ElTabs,
  ElTabPane,
  ElRadioGroup,
  ElRadioButton,
  ElEmpty,
  ElSkeleton,
  ElMessage,
  ElButton,
  ElAvatar,
  ElPagination,
} from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import FeedDynamicCard from '../components/FeedDynamicCard.vue'
import PublishFeedDialog from '../components/PublishFeedDialog.vue'
import ShareToDialog from '../components/ShareToDialog.vue'
import {
  useCommunityStore,
  type CategoryTab,
  type RecommendedUser,
} from '../store/community'
import { useAuthStore } from '../store'
import { feedsApi, routesApi } from '../api'
import type { FeedItem, PlanResponse } from '../api'
import { fetchUnifiedDynamicItems } from '../composables/useCommunityFeed'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const store = useCommunityStore()

// 定位到指定帖子：支持 query.scrollTo=type-id（如 companion-123）或 query.highlight=id（个人中心跳转用，视为 feed-id）
const scrollToTarget = computed(() => {
  const q = route.query
  const raw = (q.scrollTo as string) || (q.highlight != null ? `feed-${q.highlight}` : null)
  if (!raw || typeof raw !== 'string') return null
  const dash = raw.indexOf('-')
  if (dash === -1) return null
  const type = raw.slice(0, dash)
  const id = Number(raw.slice(dash + 1))
  if (!Number.isFinite(id)) return null
  return { type, id }
})

// 发布区（内联）
const content = ref('')
const imageUrlsJson = ref('')
const posting = ref(false)
const errorMsg = ref('')
const canPublish = computed(() => content.value.trim().length > 0)
const textareaRef = ref<HTMLTextAreaElement | null>(null)

// 发布弹窗
const publishDialogVisible = ref(false)
// 分享弹窗
const shareDialogVisible = ref(false)
const sharePayload = ref({ url: '', title: '', image: '' as string | undefined, type: undefined as 'note' | 'route' | 'companion' | 'feed' | undefined })

// 分类 Tab & 排序（Pinia）
const activeTab = computed({
  get: () => store.categoryTab,
  set: (v: CategoryTab) => store.setCategoryTab(v),
})
const sortOrder = computed({
  get: () => store.sortOrder,
  set: (v: 'latest' | 'hot') => store.setSortOrder(v),
})

// 统一动态流（过滤后）
const filteredItems = computed(() => store.filteredDynamicItems)

// 分页
const pageSizes = [5, 10, 15]
const pageSize = ref(10)
const currentPage = ref(1)
const totalItems = computed(() => filteredItems.value.length)
const displayedItems = computed(() => {
  const list = filteredItems.value
  const start = (currentPage.value - 1) * pageSize.value
  return list.slice(start, start + pageSize.value)
})
const totalPages = computed(() => Math.max(1, Math.ceil(totalItems.value / pageSize.value)))

function onPageChange(page: number) {
  currentPage.value = page
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function onSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

// 骨架屏 / 空状态
const isEmpty = computed(() => !store.dynamicLoading && filteredItems.value.length === 0)
const isFollowingEmpty = computed(
  () => store.categoryTab === 'following' && !store.dynamicLoading
)

// 右侧：热门游记、热门路线、推荐旅友
const hotNotes = computed(() =>
  [...(store.featuredNotes || [])]
    .sort((a, b) => (b.likeCount ?? 0) - (a.likeCount ?? 0))
    .slice(0, 5)
)
const hotRoutes = ref<PlanResponse[]>([])
const routeLoading = ref(false)
const recommendedUsers = computed(() => store.recommendedUsers)

async function loadFeedData() {
  store.setDynamicLoading(true)
  try {
    const items = await fetchUnifiedDynamicItems()
    store.setDynamicItems(items)
    currentPage.value = 1
    // 若 URL 带有定位参数，跳到目标所在页
    const target = scrollToTarget.value
    if (target) {
      const idx = items.findIndex((i) => i.type === target.type && i.id === target.id)
      if (idx >= 0) {
        currentPage.value = Math.floor(idx / pageSize.value) + 1
      }
    }
  } catch {
    store.setDynamicItems([])
  } finally {
    store.setDynamicLoading(false)
  }
}

function scrollToHighlightCard() {
  const target = scrollToTarget.value
  if (!target) return
  const cardId = `feed-card-${target.type}-${target.id}`
  nextTick(() => {
    const el = document.getElementById(cardId)
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' })
      // 清除 query，避免刷新时再次滚动
      router.replace({ name: 'feed' })
    }
  })
}

async function loadNotes() {
  try {
    const { notesApi } = await import('../api')
    const list = await notesApi.list()
    store.setFeaturedNotes(list || [])
  } catch {
    store.setFeaturedNotes([])
  }
}

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

function loadRecommended() {
  const mock: RecommendedUser[] = [
    { id: 1, nickname: '小鹿', avatar: '', creditLevel: '金牌', tags: ['摄影', '美食'] },
    { id: 2, nickname: '行者老张', avatar: '', creditLevel: '钻石', tags: ['自驾', '风光'] },
    { id: 3, nickname: '桃桃', avatar: '', creditLevel: '银牌', tags: ['休闲', '夜市'] },
  ]
  store.setRecommendedUsers(mock)
}

async function submitPublish() {
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
      imageJson = raw.startsWith('[') ? raw : JSON.stringify(raw.split(/[,，]/).map((s) => s.trim()).filter(Boolean))
    }
    await feedsApi.create({
      content: content.value.trim(),
      imageUrlsJson: imageJson,
    })
    content.value = ''
    imageUrlsJson.value = ''
    await loadFeedData()
    ElMessage.success('发布成功')
  } catch (e: unknown) {
    const err = e as { message?: string; response?: { data?: { message?: string } } }
    errorMsg.value = err.message || err.response?.data?.message || '发布失败'
    ElMessage.error(errorMsg.value)
  } finally {
    posting.value = false
  }
}

function openPublishDialog() {
  if (!auth.token) {
    ElMessage.warning('请先登录')
    router.push({ name: 'login', query: { redirect: '/feed' } })
    return
  }
  publishDialogVisible.value = true
}

function onPublished(feed: FeedItem) {
  store.prependFeed(feed)
  loadFeedData()
}

function goNote(id: number) {
  router.push({ name: 'note-detail', params: { id: String(id) } })
}

function goRoute(id: number) {
  router.push({ name: 'route-detail', params: { id: String(id) } })
}

function goUser(id: number) {
  router.push({ name: 'user-profile', params: { id: String(id) } })
}

function goBack() {
  if (typeof window !== 'undefined' && window.history.length > 1) {
    router.back()
  } else {
    router.push({ name: 'home' })
  }
}

function openShareDialog(payload: { url: string; title: string; image?: string; type?: 'note' | 'route' | 'companion' | 'feed' }) {
  sharePayload.value = { url: payload.url, title: payload.title, image: payload.image, type: payload.type }
  shareDialogVisible.value = true
}

onMounted(async () => {
  await loadFeedData()
  loadNotes()
  loadHotRoutes()
  loadRecommended()
})

watch([activeTab, sortOrder], () => {
  currentPage.value = 1
})

// 数据加载完成后，若存在定位目标则滚动到该帖子
watch(
  () => [store.dynamicLoading, scrollToTarget.value, displayedItems.value.length] as const,
  ([loading, target, len]) => {
    if (loading || !target || len === 0) return
    const hasTarget = displayedItems.value.some((i) => i.type === target.type && i.id === target.id)
    if (hasTarget) scrollToHighlightCard()
  },
  { flush: 'post' }
)

// 当前页超出总页数时回退到最后一页（如筛选后条数变少）
watch([totalPages, currentPage], ([pages, page]) => {
  if (page > pages && pages >= 1) currentPage.value = pages
})
</script>

<template>
  <div class="feed-page">
    <!-- 返回按钮（样式仿照游记详情页） -->
    <div class="feed-back-button">
      <el-button :icon="ArrowLeft" circle @click="goBack" />
    </div>
    <div class="feed-layout">
      <!-- 左侧主内容区 70% -->
      <main class="main-flow">
        <!-- 顶部内容控制区：Tab + 排序 + 发布入口 -->
        <section class="top-control sticky-tabs">
          <el-tabs v-model="activeTab" class="category-tabs">
            <el-tab-pane label="全部" name="all" />
            <el-tab-pane label="关注" name="following" />
            <el-tab-pane label="游记" name="note" />
            <el-tab-pane label="路线" name="route" />
            <el-tab-pane label="打卡" name="checkin" />
            <el-tab-pane label="结伴" name="companion" />
          </el-tabs>
          <div class="control-right">
            <el-radio-group v-model="sortOrder" size="default" class="sort-radio">
              <el-radio-button value="latest">最新</el-radio-button>
              <el-radio-button value="hot">最热</el-radio-button>
            </el-radio-group>
            <el-button type="primary" class="publish-entry-btn" @click="openPublishDialog">
              发布动态
            </el-button>
          </div>
        </section>

        <!-- 发布动态输入区 -->
        <el-card class="publish-card" shadow="never">
          <textarea
            ref="textareaRef"
            v-model="content"
            class="publish-textarea"
            rows="3"
            placeholder="记录你的旅行瞬间…"
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
                class="upload-input"
                placeholder="图片 URL，多个用逗号分隔"
              />
            </div>
            <div class="toolbar-right">
              <el-button
                type="primary"
                :disabled="!canPublish || posting"
                :loading="posting"
                class="publish-btn"
                @click="submitPublish"
              >
                {{ posting ? '发布中…' : '发布' }}
              </el-button>
            </div>
          </div>
          <p v-if="errorMsg" class="publish-error">{{ errorMsg }}</p>
        </el-card>

        <!-- 动态列表：骨架屏 / 空状态 / 卡片列表 -->
        <div v-if="store.dynamicLoading" class="skeleton-wrap">
          <el-card v-for="i in 4" :key="i" class="skeleton-card" shadow="never">
            <el-skeleton :rows="6" animated />
          </el-card>
        </div>

        <div v-else-if="isEmpty || isFollowingEmpty" class="empty-wrap">
          <el-empty>
            <template #image>
              <div class="empty-illus">✈️</div>
            </template>
            <template #description>
              <p class="empty-desc">
                {{ isFollowingEmpty ? '暂无关注动态，去发现更多旅友吧' : '还没有动态，去发布第一条旅行分享吧' }}
              </p>
            </template>
            <el-button v-if="!isFollowingEmpty" type="primary" @click="textareaRef?.focus()">
              去发布
            </el-button>
            <el-button v-else type="primary" @click="router.push({ name: 'companion-list' })">
              发现结伴
            </el-button>
          </el-empty>
        </div>

        <div v-else class="feed-list-wrap">
          <div class="feed-list">
            <div
              v-for="item in displayedItems"
              :key="`${item.type}-${item.id}`"
              :id="`feed-card-${item.type}-${item.id}`"
              class="feed-card-wrap"
            >
              <FeedDynamicCard :item="item" @share="openShareDialog" />
            </div>
          </div>
          <div v-if="totalItems > 0" class="feed-pagination">
            <el-pagination
              v-model:current-page="currentPage"
              v-model:page-size="pageSize"
              :page-sizes="pageSizes"
              :total="totalItems"
              :max-visible-buttons="5"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @current-change="onPageChange"
              @size-change="onSizeChange"
            />
          </div>
        </div>
      </main>

      <!-- 右侧辅助信息区 30%（PC） -->
      <aside class="sidebar">
        <el-card v-if="auth.token" class="sidebar-card user-card" shadow="never">
          <div class="user-inner" @click="router.push({ name: 'profile' })">
            <el-avatar :size="56" class="user-avatar-wrap">
              {{ (auth.nickname || '我').charAt(0) }}
            </el-avatar>
            <div class="user-meta">
              <span class="user-name">{{ auth.nickname || '旅友' }}</span>
              <span class="user-desc">记录每一次出发</span>
            </div>
            <el-button type="primary" size="small" plain class="go-profile">个人中心</el-button>
          </div>
        </el-card>
        <el-card class="sidebar-card" shadow="never">
          <template #header>
            <span class="sidebar-title">推荐旅友</span>
          </template>
          <div class="rec-list">
            <div
              v-for="u in recommendedUsers"
              :key="u.id"
              class="rec-user"
              @click="goUser(u.id)"
            >
              <el-avatar :size="36" class="rec-avatar">{{ u.nickname?.charAt(0) }}</el-avatar>
              <div class="rec-info">
                <span class="rec-name">{{ u.nickname }}</span>
                <span class="rec-tags">{{ (u.tags || []).slice(0, 2).join(' · ') }}</span>
              </div>
            </div>
          </div>
        </el-card>
        <el-card class="sidebar-card" shadow="never">
          <template #header>
            <span class="sidebar-title">热门游记</span>
          </template>
          <div class="rec-links">
            <div
              v-for="n in hotNotes"
              :key="n.id"
              class="rec-link"
              @click="goNote(n.id)"
            >
              {{ n.title || n.destination || '游记' }}
            </div>
            <router-link v-if="hotNotes.length === 0" to="/notes" class="rec-link">去逛逛</router-link>
          </div>
        </el-card>
        <el-card class="sidebar-card" shadow="never">
          <template #header>
            <span class="sidebar-title">热门路线</span>
          </template>
          <div class="rec-links">
            <div
              v-for="r in hotRoutes"
              :key="r.id"
              class="rec-link"
              @click="goRoute(r.id)"
            >
              {{ r.title || r.destination }}
            </div>
            <router-link v-if="hotRoutes.length === 0 && !routeLoading" to="/routes" class="rec-link">去逛逛</router-link>
            <span v-if="routeLoading" class="rec-loading">加载中...</span>
          </div>
        </el-card>
      </aside>
    </div>

    <PublishFeedDialog
      v-model:visible="publishDialogVisible"
      @published="onPublished"
    />
    <ShareToDialog
      v-model:visible="shareDialogVisible"
      :share-url="sharePayload.url"
      :share-title="sharePayload.title"
      :share-image="sharePayload.image"
      :share-type="sharePayload.type"
    />
  </div>
</template>

<style scoped>
.feed-page {
  min-height: 100vh;
  background: var(--feed-bg, #f5f7fa);
  padding: 24px 20px 48px;
  position: relative;
}

.feed-back-button {
  position: fixed;
  top: 80px;
  left: 20px;
  z-index: 200;
  animation: feedFadeInLeft 0.5s ease-out;
}

.feed-back-button :deep(.el-button) {
  width: 44px;
  height: 44px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.1);
  transition: all 0.3s ease;
}

.feed-back-button :deep(.el-button:hover) {
  background: #0f766e;
  border-color: #0f766e;
  color: #ffffff;
  transform: translateX(-4px);
  box-shadow: 0 6px 20px rgba(15, 118, 110, 0.3);
}

@keyframes feedFadeInLeft {
  from {
    opacity: 0;
    transform: translateX(-20px);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.feed-layout {
  max-width: var(--feed-max-width, 1200px);
  margin: 0 auto;
  display: flex;
  gap: var(--feed-gap, 20px);
  align-items: start;
}

.main-flow {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: var(--feed-gap, 20px);
}

.sidebar {
  flex: 0 0 320px;
}

/* 顶部控制区 */
.top-control {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 12px;
  background: var(--feed-bg, #f5f7fa);
  padding: 12px 0;
  z-index: 10;
}

.sticky-tabs {
  position: sticky;
  top: 0;
  z-index: 10;
}

.category-tabs {
  flex: 1;
  min-width: 200px;
}

.category-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
  min-height: 40px;
  flex-shrink: 0;
}

.category-tabs :deep(.el-tabs__content) {
  display: none;
}

.category-tabs :deep(.el-tabs__nav-wrap::after) {
  display: none;
}

.control-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.sort-radio {
  flex-shrink: 0;
}

.publish-entry-btn {
  flex-shrink: 0;
  border-radius: 8px;
}

/* 发布区 */
.publish-card {
  border-radius: var(--feed-card-radius-inner, 12px);
  padding: var(--feed-card-padding, 16px);
  border: none;
  background: #fff;
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
  line-height: 1.7;
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

.upload-input {
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 13px;
  color: #303133;
  width: 220px;
  max-width: 100%;
  transition: border-color 0.2s;
}

.upload-input::placeholder {
  color: #c0c4cc;
}

.upload-input:focus {
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
  gap: var(--feed-gap, 20px);
}

.skeleton-card {
  border-radius: var(--feed-card-radius, 14px);
  border: none;
}

.skeleton-card :deep(.el-card__body) {
  padding: 20px;
}

/* 空状态 */
.empty-wrap {
  background: #fff;
  border-radius: var(--feed-card-radius, 14px);
  padding: 48px 24px;
  box-shadow: var(--feed-shadow, 0 1px 4px rgba(0, 0, 0, 0.06));
}

.empty-illus {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.9;
}

.empty-desc {
  margin: 0 0 16px;
  color: #606266;
  font-size: 14px;
}

/* 动态列表 */
.feed-list-wrap {
  display: flex;
  flex-direction: column;
  gap: var(--feed-gap, 20px);
}

.feed-list {
  display: flex;
  flex-direction: column;
  gap: var(--feed-gap, 20px);
}

.feed-pagination {
  margin-top: 24px;
  padding: 16px 0;
  display: flex;
  justify-content: center;
}

.feed-pagination :deep(.el-pagination) {
  flex-wrap: wrap;
  justify-content: center;
}

/* 右侧栏 */
.sidebar-card {
  border-radius: var(--feed-card-radius-inner, 12px);
  margin-bottom: var(--feed-gap, 20px);
  border: none;
  background: #fff;
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
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-weight: 600;
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

.rec-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.rec-user {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  cursor: pointer;
  border-radius: 8px;
  transition: background 0.2s;
}

.rec-user:hover {
  background: #f5f7fa;
}

.rec-avatar {
  background: linear-gradient(135deg, #e0f2fe, #0ea5e9);
  color: #fff;
  font-size: 14px;
}

.rec-info {
  flex: 1;
  min-width: 0;
}

.rec-name {
  display: block;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.rec-tags {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}

.rec-links {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.rec-link {
  display: block;
  padding: 10px 12px;
  border-radius: 8px;
  font-size: 14px;
  color: #606266;
  text-decoration: none;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.rec-link:hover {
  background: #f5f7fa;
  color: #0d9488;
}

.rec-loading {
  font-size: 13px;
  color: #909399;
}

/* 移动端：单列、隐藏右侧、顶部 Tab 固定 */
@media (max-width: 1024px) {
  .feed-back-button {
    top: 70px;
    left: 12px;
  }

  .feed-back-button :deep(.el-button) {
    width: 40px;
    height: 40px;
  }

  .sidebar {
    display: none;
  }

  .feed-layout {
    flex-direction: column;
  }

  .top-control {
    position: sticky;
    top: 0;
    background: var(--feed-bg, #f5f7fa);
    padding: 12px 0;
    margin: -24px -20px 0;
    padding-left: 20px;
    padding-right: 20px;
  }

  .control-right {
    width: 100%;
    justify-content: flex-end;
  }
}

@media (max-width: 768px) {
  .feed-back-button {
    top: 60px;
    left: 8px;
  }

  .feed-back-button :deep(.el-button) {
    width: 36px;
    height: 36px;
  }
}
</style>
