<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useMessageStore, type InteractionCategory } from '../store/message'
import { ChatDotRound, ChatLineRound, Star } from '@element-plus/icons-vue'

const router = useRouter()
const messageStore = useMessageStore()

const activeCategory = ref<'interaction' | 'private'>('interaction')
const interactionFilter = ref<InteractionCategory>('all')

const isMobile = computed(() => window.innerWidth < 768)

const interactions = computed(() => messageStore.interactionMessages)
const conversations = computed(() => messageStore.conversations)
const interactionLoading = computed(() => messageStore.interactionLoading)
const convoLoading = computed(() => messageStore.convoLoading)

const interactionUnread = computed(() => messageStore.interactionUnreadCount)
const privateUnread = computed(() => messageStore.privateUnreadCount)
const totalUnread = computed(() => messageStore.totalUnread)

function formatTime(iso: string) {
  const date = new Date(iso)
  const now = new Date()
  const diff = (now.getTime() - date.getTime()) / 1000
  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 3600 * 24) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 3600 * 24 * 7) return `${Math.floor(diff / (3600 * 24))} 天前`
  return date.toLocaleDateString()
}

function interactionActionLabel(type: 'LIKE' | 'COMMENT', targetType: 'NOTE' | 'ROUTE') {
  if (type === 'LIKE') {
    return targetType === 'NOTE' ? '点赞了你的游记' : '点赞了你的路线'
  }
  return targetType === 'NOTE' ? '评论了你的游记' : '评论了你的路线'
}

function goToTarget(targetType: 'NOTE' | 'ROUTE', targetId: number) {
  if (targetType === 'NOTE') {
    router.push({ name: 'note-detail', params: { id: targetId } })
  } else {
    router.push({ name: 'route-detail', params: { id: targetId } })
  }
}

async function handleInteractionClick(id: number, targetType: 'NOTE' | 'ROUTE', targetId: number) {
  await messageStore.markInteractionRead(id)
  goToTarget(targetType, targetId)
}

async function handleConversationClick(conversationId: number, peerUserId: number) {
  await messageStore.clearConversationUnread(conversationId)
  router.push({ name: 'chat', params: { id: peerUserId } })
}

async function markAllInteractionRead() {
  await messageStore.markAllInteractionRead()
}

function onTabChange(name: string | number) {
  activeCategory.value = name as 'interaction' | 'private'
}

async function loadInteraction(reset = false) {
  if (reset) {
    messageStore.interactionPage = 1
  }
  await messageStore.fetchInteractionMessages(interactionFilter.value)
}

async function loadMoreInteraction() {
  if (interactions.value.length >= messageStore.interactionTotal) return
  messageStore.interactionPage += 1
  await loadInteraction()
}

async function loadConversations(reset = false) {
  if (reset) {
    messageStore.convoPage = 1
  }
  await messageStore.fetchConversations()
}

async function loadMoreConversations() {
  if (conversations.value.length >= messageStore.convoTotal) return
  messageStore.convoPage += 1
  await loadConversations()
}

onMounted(async () => {
  await Promise.all([loadInteraction(true), loadConversations(true)])
})
</script>

<template>
  <div class="message-page">
    <div class="message-header">
      <div>
        <h1 class="title">消息中心</h1>
        <p class="subtitle">共 {{ totalUnread }} 条未读消息</p>
      </div>
    </div>

    <div v-if="isMobile" class="message-content mobile">
      <el-tabs v-model="activeCategory" class="mobile-tabs" @tab-change="onTabChange">
        <el-tab-pane name="interaction">
          <template #label>
            <span class="tab-label">
              <el-icon class="tab-icon">
                <ChatDotRound />
              </el-icon>
              互动消息
              <el-badge
                v-if="interactionUnread"
                :value="interactionUnread > 99 ? '99+' : interactionUnread"
                class="tab-badge"
              />
            </span>
          </template>
          <section class="panel">
            <div class="panel-header">
              <div class="panel-title">
                <span>互动通知</span>
                <span class="panel-sub">点赞与评论的最新动态</span>
              </div>
              <div class="panel-actions">
                <el-segmented
                  v-model="interactionFilter"
                  size="small"
                  :options="[
                    { label: '全部', value: 'all' },
                    { label: '点赞', value: 'like' },
                    { label: '评论', value: 'comment' },
                  ]"
                  @change="() => loadInteraction(true)"
                />
                <el-button v-if="interactionUnread" text size="small" @click="markAllInteractionRead">
                  全部标为已读
                </el-button>
              </div>
            </div>

            <el-skeleton v-if="interactionLoading && !interactions.length" animated :count="3">
              <template #template>
                <div class="msg-item-skeleton">
                  <el-skeleton-item variant="circle" style="width: 40px; height: 40px" />
                  <div class="msg-item-skeleton-main">
                    <el-skeleton-item variant="text" style="width: 60%" />
                    <el-skeleton-item variant="text" style="width: 40%" />
                  </div>
                </div>
              </template>
            </el-skeleton>

            <template v-else>
              <el-empty v-if="!interactions.length" description="暂无互动消息">
                <template #image>
                  <el-icon class="empty-icon">
                    <Star />
                  </el-icon>
                </template>
              </el-empty>

              <el-scrollbar v-else class="list-scroll">
                <div
                  v-for="item in interactions"
                  :key="item.id"
                  class="interaction-item"
                  :class="{ unread: !item.read }"
                  @click="handleInteractionClick(item.id, item.targetType, item.targetId)"
                >
                  <el-avatar :size="40" :src="item.fromUserAvatar">
                    {{ item.fromUserName?.charAt(0) || '友' }}
                  </el-avatar>
                  <div class="interaction-main">
                    <div class="interaction-line">
                      <span class="nickname">{{ item.fromUserName }}</span>
                      <span class="action">{{ interactionActionLabel(item.type, item.targetType) }}</span>
                    </div>
                    <div class="interaction-target">
                      <span class="tag">
                        {{ item.targetType === 'NOTE' ? '游记' : '路线' }}
                      </span>
                      <span class="title">{{ item.targetTitle }}</span>
                    </div>
                    <div v-if="item.contentPreview" class="interaction-preview">
                      “{{ item.contentPreview }}”
                    </div>
                    <div class="interaction-meta">
                      <span class="time">{{ formatTime(item.createdAt) }}</span>
                      <span v-if="!item.read" class="unread-dot" />
                    </div>
                  </div>
                </div>

                <div v-if="interactions.length < messageStore.interactionTotal" class="load-more-wrap">
                  <el-button :loading="interactionLoading" text size="small" @click="loadMoreInteraction">
                    加载更多
                  </el-button>
                </div>
              </el-scrollbar>
            </template>
          </section>
        </el-tab-pane>

        <el-tab-pane name="private">
          <template #label>
            <span class="tab-label">
              <el-icon class="tab-icon">
                <ChatLineRound />
              </el-icon>
              私信
              <el-badge
                v-if="privateUnread"
                :value="privateUnread > 99 ? '99+' : privateUnread"
                class="tab-badge"
              />
            </span>
          </template>
          <section class="panel">
            <div class="panel-header">
              <div class="panel-title">
                <span>私信会话</span>
                <span class="panel-sub">和旅友的私密交流</span>
              </div>
            </div>

            <el-skeleton v-if="convoLoading && !conversations.length" animated :count="3">
              <template #template>
                <div class="msg-item-skeleton">
                  <el-skeleton-item variant="circle" style="width: 40px; height: 40px" />
                  <div class="msg-item-skeleton-main">
                    <el-skeleton-item variant="text" style="width: 60%" />
                    <el-skeleton-item variant="text" style="width: 40%" />
                  </div>
                </div>
              </template>
            </el-skeleton>

            <template v-else>
              <el-empty v-if="!conversations.length" description="暂无私信，去结伴认识新旅友吧">
                <template #image>
                  <el-icon class="empty-icon">
                    <ChatLineRound />
                  </el-icon>
                </template>
              </el-empty>

              <el-scrollbar v-else class="list-scroll">
                <div
                  v-for="item in conversations"
                  :key="item.id"
                  class="conversation-item"
                  :class="{ unread: item.unreadCount > 0, pinned: item.pinned }"
                  @click="handleConversationClick(item.id, item.peerUserId)"
                >
                  <el-avatar :size="40" :src="item.peerAvatar">
                    {{ item.peerNickname?.charAt(0) || '友' }}
                  </el-avatar>
                  <div class="conversation-main">
                    <div class="conversation-top">
                      <span class="nickname">{{ item.peerNickname }}</span>
                      <span class="time">{{ formatTime(item.lastMessageTime) }}</span>
                    </div>
                    <div class="conversation-bottom">
                      <span class="preview">{{ item.lastMessagePreview }}</span>
                      <el-badge
                        v-if="item.unreadCount"
                        :value="item.unreadCount > 99 ? '99+' : item.unreadCount"
                        class="unread-badge"
                      />
                    </div>
                  </div>
                </div>

                <div v-if="conversations.length < messageStore.convoTotal" class="load-more-wrap">
                  <el-button :loading="convoLoading" text size="small" @click="loadMoreConversations">
                    加载更多
                  </el-button>
                </div>
              </el-scrollbar>
            </template>
          </section>
        </el-tab-pane>
      </el-tabs>
    </div>

    <div v-else class="message-content desktop">
      <aside class="sidebar">
        <div
          class="sidebar-item"
          :class="{ active: activeCategory === 'interaction' }"
          @click="activeCategory = 'interaction'"
        >
          <div class="left">
            <el-icon class="icon interaction">
              <ChatDotRound />
            </el-icon>
            <div class="text">
              <div class="label">互动消息</div>
              <div class="desc">点赞、评论等互动提醒</div>
            </div>
          </div>
          <el-badge
            v-if="interactionUnread"
            :value="interactionUnread > 99 ? '99+' : interactionUnread"
            class="sidebar-badge"
          />
        </div>

        <div
          class="sidebar-item"
          :class="{ active: activeCategory === 'private' }"
          @click="activeCategory = 'private'"
        >
          <div class="left">
            <el-icon class="icon private">
              <ChatLineRound />
            </el-icon>
            <div class="text">
              <div class="label">私信</div>
              <div class="desc">与旅友的一对一交流</div>
            </div>
          </div>
          <el-badge
            v-if="privateUnread"
            :value="privateUnread > 99 ? '99+' : privateUnread"
            class="sidebar-badge"
          />
        </div>
      </aside>

      <main class="main-panel">
        <section v-show="activeCategory === 'interaction'" class="panel">
          <div class="panel-header">
            <div class="panel-title">
              <span>互动通知</span>
              <span class="panel-sub">点赞与评论的最新动态</span>
            </div>
            <div class="panel-actions">
              <el-segmented
                v-model="interactionFilter"
                size="small"
                :options="[
                  { label: '全部', value: 'all' },
                  { label: '点赞', value: 'like' },
                  { label: '评论', value: 'comment' },
                ]"
                @change="() => loadInteraction(true)"
              />
              <el-button v-if="interactionUnread" text size="small" @click="markAllInteractionRead">
                全部标为已读
              </el-button>
            </div>
          </div>

          <el-skeleton v-if="interactionLoading && !interactions.length" animated :count="3">
            <template #template>
              <div class="msg-item-skeleton">
                <el-skeleton-item variant="circle" style="width: 40px; height: 40px" />
                <div class="msg-item-skeleton-main">
                  <el-skeleton-item variant="text" style="width: 60%" />
                  <el-skeleton-item variant="text" style="width: 40%" />
                </div>
              </div>
            </template>
          </el-skeleton>

          <template v-else>
            <el-empty v-if="!interactions.length" description="暂无互动消息">
              <template #image>
                <el-icon class="empty-icon">
                  <Star />
                </el-icon>
              </template>
            </el-empty>

            <el-scrollbar v-else class="list-scroll">
              <div
                v-for="item in interactions"
                :key="item.id"
                class="interaction-item"
                :class="{ unread: !item.read }"
                @click="handleInteractionClick(item.id, item.targetType, item.targetId)"
              >
                <el-avatar :size="40" :src="item.fromUserAvatar">
                  {{ item.fromUserName?.charAt(0) || '友' }}
                </el-avatar>
                <div class="interaction-main">
                  <div class="interaction-line">
                    <span class="nickname">{{ item.fromUserName }}</span>
                    <span class="action">{{ interactionActionLabel(item.type, item.targetType) }}</span>
                  </div>
                  <div class="interaction-target">
                    <span class="tag">
                      {{ item.targetType === 'NOTE' ? '游记' : '路线' }}
                    </span>
                    <span class="title">{{ item.targetTitle }}</span>
                  </div>
                  <div v-if="item.contentPreview" class="interaction-preview">
                    “{{ item.contentPreview }}”
                  </div>
                  <div class="interaction-meta">
                    <span class="time">{{ formatTime(item.createdAt) }}</span>
                    <span v-if="!item.read" class="unread-dot" />
                  </div>
                </div>
              </div>

              <div v-if="interactions.length < messageStore.interactionTotal" class="load-more-wrap">
                <el-button :loading="interactionLoading" text size="small" @click="loadMoreInteraction">
                  加载更多
                </el-button>
              </div>
            </el-scrollbar>
          </template>
        </section>

        <section v-show="activeCategory === 'private'" class="panel">
          <div class="panel-header">
            <div class="panel-title">
              <span>私信会话</span>
              <span class="panel-sub">和旅友的私密交流</span>
            </div>
          </div>

          <el-skeleton v-if="convoLoading && !conversations.length" animated :count="3">
            <template #template>
              <div class="msg-item-skeleton">
                <el-skeleton-item variant="circle" style="width: 40px; height: 40px" />
                <div class="msg-item-skeleton-main">
                  <el-skeleton-item variant="text" style="width: 60%" />
                  <el-skeleton-item variant="text" style="width: 40%" />
                </div>
              </div>
            </template>
          </el-skeleton>

          <template v-else>
            <el-empty v-if="!conversations.length" description="暂无私信，去结伴认识新旅友吧">
              <template #image>
                <el-icon class="empty-icon">
                  <ChatLineRound />
                </el-icon>
              </template>
            </el-empty>

            <el-scrollbar v-else class="list-scroll">
              <div
                v-for="item in conversations"
                :key="item.id"
                class="conversation-item"
                :class="{ unread: item.unreadCount > 0, pinned: item.pinned }"
                @click="handleConversationClick(item.id, item.peerUserId)"
              >
                <el-avatar :size="40" :src="item.peerAvatar">
                  {{ item.peerNickname?.charAt(0) || '友' }}
                </el-avatar>
                <div class="conversation-main">
                  <div class="conversation-top">
                    <span class="nickname">{{ item.peerNickname }}</span>
                    <span class="time">{{ formatTime(item.lastMessageTime) }}</span>
                  </div>
                  <div class="conversation-bottom">
                    <span class="preview">{{ item.lastMessagePreview }}</span>
                    <el-badge
                      v-if="item.unreadCount"
                      :value="item.unreadCount > 99 ? '99+' : item.unreadCount"
                      class="unread-badge"
                    />
                  </div>
                </div>
              </div>

              <div v-if="conversations.length < messageStore.convoTotal" class="load-more-wrap">
                <el-button :loading="convoLoading" text size="small" @click="loadMoreConversations">
                  加载更多
                </el-button>
              </div>
            </el-scrollbar>
          </template>
        </section>
      </main>
    </div>
  </div>
</template>

<style scoped>
.message-page {
  max-width: 1200px;
  margin: 24px auto;
  padding: 0 16px 40px;
}

.message-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.title {
  font-size: 24px;
  font-weight: 600;
  color: #0f172a;
}

.subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: #94a3b8;
}

.message-content.desktop {
  display: grid;
  grid-template-columns: 260px 1fr;
  gap: 20px;
}

.message-content.mobile {
  margin-top: 12px;
}

.sidebar {
  background: #f8fafc;
  border-radius: 16px;
  padding: 12px;
  border: 1px solid #e2e8f0;
}

.sidebar-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.2s, transform 0.1s;
}

.sidebar-item + .sidebar-item {
  margin-top: 4px;
}

.sidebar-item .left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sidebar-item .icon {
  font-size: 20px;
}

.sidebar-item .icon.interaction {
  color: #f97316;
}

.sidebar-item .icon.private {
  color: #6366f1;
}

.sidebar-item .text .label {
  font-size: 14px;
  color: #0f172a;
  font-weight: 500;
}

.sidebar-item .text .desc {
  font-size: 12px;
  color: #94a3b8;
}

.sidebar-item:hover {
  background: #e2f3ff;
}

.sidebar-item.active {
  background: linear-gradient(to right, #ecfeff, #eff6ff);
}

.sidebar-badge :deep(.el-badge__content) {
  background-color: #ef4444;
}

.main-panel {
  min-height: 420px;
}

.panel {
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  padding: 16px 16px 4px;
  display: flex;
  flex-direction: column;
  min-height: 420px;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.panel-title span:first-child {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.panel-sub {
  margin-left: 6px;
  font-size: 12px;
  color: #94a3b8;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.list-scroll {
  max-height: 520px;
  padding-right: 4px;
}

.interaction-item,
.conversation-item {
  display: flex;
  gap: 12px;
  padding: 10px 8px;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.15s, transform 0.1s, box-shadow 0.15s;
}

.interaction-item + .interaction-item,
.conversation-item + .conversation-item {
  margin-top: 4px;
}

.interaction-item:hover,
.conversation-item:hover {
  background: #f8fafc;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.08);
}

.interaction-item.unread,
.conversation-item.unread {
  background: #ecfeff;
}

.interaction-item.unread:hover,
.conversation-item.unread:hover {
  background: #e0f2fe;
}

.interaction-main,
.conversation-main {
  flex: 1;
  min-width: 0;
}

.interaction-line {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.nickname {
  font-size: 14px;
  font-weight: 500;
  color: #0f172a;
}

.action {
  font-size: 13px;
  color: #0f172a;
}

.interaction-target {
  margin-top: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.interaction-target .tag {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 9999px;
  background: #e0f2fe;
  color: #0369a1;
}

.interaction-target .title {
  font-size: 13px;
  color: #1e293b;
  font-weight: 500;
}

.interaction-preview {
  margin-top: 4px;
  font-size: 13px;
  color: #64748b;
}

.interaction-meta {
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.time {
  font-size: 12px;
  color: #94a3b8;
}

.unread-dot {
  width: 8px;
  height: 8px;
  border-radius: 9999px;
  background: #ef4444;
}

.conversation-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.conversation-bottom {
  margin-top: 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.conversation-bottom .preview {
  font-size: 13px;
  color: #64748b;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.unread-badge :deep(.el-badge__content) {
  background-color: #10b981;
}

.load-more-wrap {
  display: flex;
  justify-content: center;
  padding: 8px 0 4px;
}

.empty-icon {
  font-size: 40px;
  color: #cbd5f5;
}

.mobile-tabs :deep(.el-tabs__nav-wrap) {
  padding: 0 4px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.tab-icon {
  font-size: 16px;
}

.tab-badge :deep(.el-badge__content) {
  background: #ef4444;
}

@media (max-width: 767px) {
  .message-page {
    padding: 0 8px 24px;
  }

  .title {
    font-size: 20px;
  }
}
</style>

