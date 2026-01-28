<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import FeedCard from '../components/FeedCard.vue'
import PublishFeedDialog from '../components/PublishFeedDialog.vue'
import { useCommunityStore, TOPIC_TAGS, type RecommendedUser } from '../store/community'
import { useAuthStore } from '../store'
import { feedsApi, notesApi } from '../api'
import type { FeedItem } from '../api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const store = useCommunityStore()

const publishVisible = ref(false)
const loadingMore = ref(false)
const feedPageSize = 10
const displayedFeedCount = ref(feedPageSize)

const feedTypeTags = ['游记', '攻略', '经验分享']

watch(
  () => route.query.q,
  (q) => {
    store.setSearchKeyword(typeof q === 'string' ? q : '')
  },
  { immediate: true }
)

const displayedFeeds = computed(() => store.filteredFeeds.slice(0, displayedFeedCount.value))
const noMore = computed(() => displayedFeedCount.value >= store.filteredFeeds.length)

function getFeedTypeTag(_item: FeedItem) {
  return feedTypeTags[Math.floor(Math.random() * feedTypeTags.length)]
}

async function loadFeeds() {
  store.setFeedLoading(true)
  try {
    const list = await feedsApi.list()
    store.setFeeds(list || [])
  } catch {
    store.setFeeds([])
  } finally {
    store.setFeedLoading(false)
  }
}

async function loadNotes() {
  store.setNoteLoading(true)
  try {
    const list = await notesApi.list()
    store.setFeaturedNotes(list || [])
  } catch {
    store.setFeaturedNotes([])
  } finally {
    store.setNoteLoading(false)
  }
}

function loadRecommended() {
  const mock: RecommendedUser[] = [
    { id: 1, nickname: '小鹿', avatar: '', creditLevel: '金牌', tags: ['摄影', '美食'] },
    { id: 2, nickname: '行者老张', avatar: '', creditLevel: '钻石', tags: ['自驾', '风光'] },
    { id: 3, nickname: '桃桃', avatar: '', creditLevel: '银牌', tags: ['休闲', '夜市'] },
    { id: 4, nickname: '北极星', avatar: '', creditLevel: '金牌', tags: ['极光', '北欧'] },
  ]
  store.setRecommendedUsers(mock)
}

function onTopicClick(tag: string) {
  store.setSelectedTopic(store.selectedTopic === tag ? null : tag)
}

function onSortChange(order: 'latest' | 'hot') {
  store.setSortOrder(order)
}

function loadMore() {
  if (noMore.value || loadingMore.value) return
  loadingMore.value = true
  setTimeout(() => {
    displayedFeedCount.value += feedPageSize
    loadingMore.value = false
  }, 300)
}

function goNote(id: number) {
  router.push(`/notes/${id}`)
}

function onPublished(feed: FeedItem) {
  store.prependFeed(feed)
}

onMounted(async () => {
  await Promise.all([loadFeeds(), loadNotes()])
  loadRecommended()
})
</script>

<template>
  <div class="community-page">
    <div class="community-body">
      <main class="content-main">
        <section class="topic-section">
          <div class="topic-tags">
            <span
              v-for="tag in TOPIC_TAGS"
              :key="tag"
              class="topic-tag"
              :class="{ active: store.selectedTopic === tag }"
              @click="onTopicClick(tag)"
            >
              {{ tag }}
            </span>
          </div>
          <div class="sort-tabs">
            <button
              type="button"
              class="sort-tab"
              :class="{ active: store.sortOrder === 'latest' }"
              @click="onSortChange('latest')"
            >
              最新
            </button>
            <button
              type="button"
              class="sort-tab"
              :class="{ active: store.sortOrder === 'hot' }"
              @click="onSortChange('hot')"
            >
              最热
            </button>
          </div>
        </section>

        <section class="feed-section">
          <h2 class="block-title">热门动态</h2>
          <div v-if="store.feedLoading" class="feed-loading">
            <span class="spinner" />
            <span>加载中...</span>
          </div>
          <div v-else-if="store.filteredFeeds.length === 0" class="empty-feed">
            <el-empty description="暂无动态，来发布第一条吧～" />
          </div>
          <div v-else class="feed-list">
            <FeedCard
              v-for="item in displayedFeeds"
              :key="item.id"
              :item="item"
              :type-tag="getFeedTypeTag(item)"
            />
            <div class="load-more">
              <el-button
                v-if="!noMore && store.filteredFeeds.length > 0"
                :loading="loadingMore"
                text
                type="primary"
                @click="loadMore"
              >
                加载更多
              </el-button>
              <p v-else-if="store.filteredFeeds.length > 0" class="no-more">已经到底部</p>
            </div>
          </div>
        </section>

        <section class="notes-section">
          <h2 class="block-title">精选游记</h2>
          <div v-if="store.noteLoading" class="notes-loading">加载中...</div>
          <div v-else class="notes-scroll">
            <article
              v-for="n in store.filteredNotes"
              :key="n.id"
              class="note-card"
              @click="goNote(n.id)"
            >
              <div class="note-cover">
                <img
                  :src="n.coverImage || `https://picsum.photos/seed/note${n.id}/320/200`"
                  :alt="n.title"
                  loading="lazy"
                />
              </div>
              <h4 class="note-title">{{ n.title }}</h4>
              <p class="note-meta">
                {{ n.authorName || '旅友' }} · {{ n.destination || '旅行' }}
              </p>
              <p class="note-stats">0 浏览 · 0 点赞 · 0 评论</p>
            </article>
          </div>
        </section>
      </main>

      <aside class="sidebar">
        <div class="sidebar-card">
          <h3 class="sidebar-title">推荐用户</h3>
          <div
            v-for="u in store.recommendedUsers"
            :key="u.id"
            class="rec-user"
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
        </div>
        <div class="sidebar-card">
          <h3 class="sidebar-title">热门路线</h3>
          <p class="sidebar-text text-subtle">完成路线规划后可在此展示推荐。</p>
          <el-button type="primary" text @click="router.push('/routes')">去规划</el-button>
        </div>
        <div class="sidebar-card notice-card">
          <h3 class="sidebar-title">社区公告</h3>
          <p class="sidebar-text">分享真实旅行，友善交流。发布游记与攻略可获得更多曝光与旅友互动。</p>
        </div>
      </aside>
    </div>

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
  padding-bottom: 80px;
}

.community-body {
  max-width: 1200px;
  margin: 0 auto;
  padding: 24px 20px;
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 28px;
  align-items: start;
}

@media (max-width: 1024px) {
  .community-body {
    grid-template-columns: 1fr;
  }
}

.content-main {
  min-width: 0;
}

.topic-section {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}

.topic-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.topic-tag {
  padding: 8px 16px;
  border-radius: 20px;
  font-size: 14px;
  color: #64748b;
  background: #fff;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition: background 0.2s, color 0.2s, border-color 0.2s;
}

.topic-tag:hover,
.topic-tag.active {
  background: #ccfbf1;
  color: #0d9488;
  border-color: #99f6e4;
}

.sort-tabs {
  display: flex;
  gap: 4px;
}

.sort-tab {
  padding: 6px 14px;
  border-radius: 8px;
  font-size: 14px;
  color: #64748b;
  background: #fff;
  border: 1px solid #e2e8f0;
  cursor: pointer;
}

.sort-tab.active {
  background: #0d9488;
  color: #fff;
  border-color: #0d9488;
}

.block-title {
  margin: 0 0 16px;
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

.feed-section {
  margin-bottom: 32px;
}

.feed-loading,
.notes-loading {
  padding: 40px;
  text-align: center;
  color: #64748b;
}

.feed-loading .spinner {
  display: inline-block;
  width: 20px;
  height: 20px;
  margin-right: 8px;
  vertical-align: middle;
  border: 2px solid #e2e8f0;
  border-top-color: #0d9488;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.empty-feed {
  padding: 40px 20px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.feed-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.load-more {
  text-align: center;
  padding: 20px 0;
}

.no-more {
  margin: 0;
  font-size: 14px;
  color: #94a3b8;
}

.notes-section {
  margin-bottom: 24px;
}

.notes-scroll {
  display: flex;
  gap: 20px;
  overflow-x: auto;
  padding-bottom: 12px;
}

.notes-scroll::-webkit-scrollbar {
  height: 8px;
}

.notes-scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}

.note-card {
  flex: 0 0 240px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.note-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.note-cover {
  width: 100%;
  height: 140px;
  background: #f1f5f9;
}

.note-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.note-title {
  margin: 0;
  padding: 12px 14px 4px;
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.note-meta {
  margin: 0;
  padding: 0 14px;
  font-size: 13px;
  color: #64748b;
}

.note-stats {
  margin: 4px 14px 12px;
  font-size: 12px;
  color: #94a3b8;
}

.sidebar {
  position: sticky;
  top: 88px;
}

.sidebar-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 20px;
  margin-bottom: 20px;
}

.sidebar-title {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.rec-user {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid #f1f5f9;
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

.sidebar-text {
  margin: 0 0 12px;
  font-size: 14px;
  line-height: 1.5;
}

.notice-card {
  background: linear-gradient(135deg, #f0fdfa 0%, #ccfbf1 100%);
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

.text-subtle {
  color: #64748b;
}
</style>
