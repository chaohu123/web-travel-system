<script setup lang="ts">
/**
 * 小队群聊页：与私信页一致，支持表情、图片、发送行程、发送路线、发送景点。
 */
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../store'
import { companionApi, interactionsApi, routesApi } from '../api'
import type { CompanionPostDetail } from '../api'
import type { TeamMemberItem } from '../api/types'
import type { TeamChatSpotPayload } from '../api/types'
import type { TripPlanActivity } from '../api'
import type { PlanResponse } from '../api'

/** 路线卡片 payload（与 routeJson 一致） */
interface TeamChatRoutePayload {
  id: number
  title: string
  days: number
  destination: string
}

/** 结伴行程卡片 payload（与 companionJson 一致） */
interface TeamChatCompanionPayload {
  id: number
  destination: string
  startDate: string
  endDate: string
}

interface ChatMessage {
  id: number
  fromSelf: boolean
  author: string
  time: string
  content: string
  status: 'sent' | 'pending'
  type: 'text' | 'spot' | 'image' | 'route' | 'companion'
  spotPayload?: TeamChatSpotPayload | null
  routePayload?: TeamChatRoutePayload | null
  companionPayload?: TeamChatCompanionPayload | null
  imageUrl?: string | null
  createdAt: string
}

/** 从收藏路线中收集的「可发送景点」 */
interface FavoriteSpotItem {
  routeId: number
  routeTitle: string
  dayIndex: number
  activityIndex: number
  name: string
  location?: string
  imageUrl?: string
}

/** 可发送的路线（来自收藏） */
interface FavoriteRouteItem {
  id: number
  title: string
  destination: string
  days: number
}

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const postId = computed(() => {
  const id = route.params.postId
  return id && !Array.isArray(id) ? Number(id) : 0
})

const post = ref<CompanionPostDetail | null>(null)
const teamMembers = ref<TeamMemberItem[]>([])
const memberCount = computed(() => teamMembers.value.length)

const messages = ref<ChatMessage[]>([])
const newMessage = ref('')
const chatLoading = ref(false)
const chatSending = ref(false)
const messageListRef = ref<HTMLDivElement | null>(null)

const spotDialogVisible = ref(false)
const favoriteSpots = ref<FavoriteSpotItem[]>([])
const favoriteSpotsLoading = ref(false)
const routeDialogVisible = ref(false)
const favoriteRoutes = ref<FavoriteRouteItem[]>([])
const favoriteRoutesLoading = ref(false)
const previewImageUrl = ref('')
const previewVisible = ref(false)

const canSendChat = computed(() => {
  if (!auth.userId || !post.value) return false
  if (post.value.creatorId === auth.userId) return true
  return teamMembers.value.some((m) => m.userId === auth.userId)
})

const pageTitle = computed(() => {
  const dest = post.value?.destination || '结伴活动'
  const n = memberCount.value
  return n > 0 ? `${dest}（小队 ${n} 人）` : dest
})

function formatTimeLabel(createdAt: string): string {
  if (!createdAt) return '--'
  const d = new Date(createdAt)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const oneDay = 24 * 60 * 60 * 1000
  if (diff < oneDay) return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  if (diff < 2 * oneDay) return `昨天 ${d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}`
  return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function shouldShowTimeDivider(curr: ChatMessage, prev?: ChatMessage) {
  if (!prev) return true
  const currTime = new Date(curr.createdAt).getTime()
  const prevTime = new Date(prev.createdAt).getTime()
  return currTime - prevTime > 10 * 60 * 1000
}

async function fetchPostAndTeam() {
  if (!postId.value) return
  try {
    post.value = await companionApi.getPost(postId.value)
    if (post.value?.teamId) {
      const team = await companionApi.getTeam(post.value.teamId)
      teamMembers.value = team.members ?? []
    } else {
      teamMembers.value = []
    }
  } catch {
    post.value = null
    teamMembers.value = []
  }
}

async function fetchChatHistory() {
  if (!postId.value) return
  if (!canSendChat.value) {
    messages.value = []
    chatLoading.value = false
    return
  }
  chatLoading.value = true
  try {
    const list = await companionApi.getPostChatMessages(postId.value)
    messages.value = (list || []).map((item) => {
      let spotPayload: TeamChatSpotPayload | null = null
      let routePayload: TeamChatRoutePayload | null = null
      let companionPayload: TeamChatCompanionPayload | null = null
      if (item.type === 'spot' && item.spotJson) {
        try {
          spotPayload = JSON.parse(item.spotJson) as TeamChatSpotPayload
        } catch {
          // ignore
        }
      }
      if (item.type === 'route' && item.routeJson) {
        try {
          routePayload = JSON.parse(item.routeJson) as TeamChatRoutePayload
        } catch {
          // ignore
        }
      }
      if (item.type === 'companion' && item.companionJson) {
        try {
          companionPayload = JSON.parse(item.companionJson) as TeamChatCompanionPayload
        } catch {
          // ignore
        }
      }
      const msgType = (item.type === 'spot' || item.type === 'image' || item.type === 'route' || item.type === 'companion')
        ? item.type
        : 'text'
      return {
        id: item.id,
        fromSelf: item.userId === auth.userId,
        author: item.authorNickname || '旅友',
        time: formatTimeLabel(item.createdAt),
        content: item.content,
        status: 'sent' as const,
        type: msgType as ChatMessage['type'],
        spotPayload,
        routePayload,
        companionPayload,
        imageUrl: item.type === 'image' ? item.content : undefined,
        createdAt: item.createdAt,
      } as ChatMessage
    })
    await nextTick()
    scrollToBottom()
  } catch {
    messages.value = []
  } finally {
    chatLoading.value = false
  }
}

function scrollToBottom() {
  const el = messageListRef.value
  if (el) el.scrollTop = el.scrollHeight
}

function goToCompanionDetail() {
  if (postId.value) router.push(`/companion/${postId.value}`)
}

function goToRouteDetail(routeId: number) {
  router.push(`/routes/${routeId}`)
}

async function sendTextMessage() {
  const content = newMessage.value.trim()
  if (!content) return
  if (!auth.token) {
    ElMessage.warning('请先登录后再发送消息')
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (!canSendChat.value) {
    ElMessage.info('仅小队成员可在此发送消息')
    return
  }
  if (!postId.value) return
  chatSending.value = true
  try {
    const item = await companionApi.sendPostChatMessage(postId.value, content)
    appendLocalMessage(item, 'text')
    newMessage.value = ''
    await nextTick()
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '发送失败')
  } finally {
    chatSending.value = false
  }
}

function appendLocalMessage(
  item: {
    id: number
    authorNickname?: string
    content: string
    createdAt: string
    type?: string
    spotJson?: string | null
    routeJson?: string | null
    companionJson?: string | null
  },
  msgType: ChatMessage['type']
) {
  let spotPayload: TeamChatSpotPayload | null = null
  let routePayload: TeamChatRoutePayload | null = null
  let companionPayload: TeamChatCompanionPayload | null = null
  if (msgType === 'spot' && item.spotJson) {
    try {
      spotPayload = JSON.parse(item.spotJson) as TeamChatSpotPayload
    } catch {
      // ignore
    }
  }
  if (msgType === 'route' && item.routeJson) {
    try {
      routePayload = JSON.parse(item.routeJson) as TeamChatRoutePayload
    } catch {
      // ignore
    }
  }
  if (msgType === 'companion' && item.companionJson) {
    try {
      companionPayload = JSON.parse(item.companionJson) as TeamChatCompanionPayload
    } catch {
      // ignore
    }
  }
  messages.value = [
    ...messages.value,
    {
      id: item.id,
      fromSelf: true,
      author: item.authorNickname || auth.nickname || '我',
      time: formatTimeLabel(item.createdAt),
      content: item.content,
      status: 'sent' as const,
      type: msgType,
      spotPayload,
      routePayload,
      companionPayload,
      imageUrl: msgType === 'image' ? item.content : undefined,
      createdAt: item.createdAt,
    } as ChatMessage,
  ]
}

async function loadFavoriteSpots() {
  if (!auth.token) {
    ElMessage.warning('请先登录')
    return
  }
  spotDialogVisible.value = true
  favoriteSpotsLoading.value = true
  favoriteSpots.value = []
  try {
    const favs = await interactionsApi.myFavorites()
    const routeIds = (favs || []).filter((f) => f.targetType === 'route').map((f) => f.targetId)
    const plans: PlanResponse[] = []
    for (const rid of routeIds) {
      try {
        const plan = await routesApi.getOne(rid)
        if (plan?.days?.length) plans.push(plan)
      } catch {
        // skip
      }
    }
    const spots: FavoriteSpotItem[] = []
    for (const plan of plans) {
      for (const day of plan.days || []) {
        const activities = day.activities || []
        activities.forEach((act: TripPlanActivity, idx: number) => {
          const name = act.name || act.location || '景点'
          spots.push({
            routeId: plan.id,
            routeTitle: plan.title || plan.destination || '路线',
            dayIndex: day.dayIndex,
            activityIndex: idx,
            name,
            location: act.location,
            imageUrl: (act as any).imageUrl,
          })
        })
      }
    }
    favoriteSpots.value = spots
    if (spots.length === 0) {
      ElMessage.info('暂无收藏路线中的景点，请先收藏包含景点的路线')
    }
  } catch {
    ElMessage.error('加载收藏景点失败')
  } finally {
    favoriteSpotsLoading.value = false
  }
}

async function sendSpotMessage(spot: FavoriteSpotItem) {
  spotDialogVisible.value = false
  if (!postId.value || !canSendChat.value) return
  const payload: TeamChatSpotPayload = {
    routeId: spot.routeId,
    dayIndex: spot.dayIndex,
    activityIndex: spot.activityIndex,
    name: spot.name,
    location: spot.location,
  }
  const content = `分享了一个景点：${spot.name}`
  chatSending.value = true
  try {
    const item = await companionApi.sendPostChatMessage(postId.value, content, {
      type: 'spot',
      spotJson: JSON.stringify(payload),
    })
    appendLocalMessage(
      {
        id: item.id,
        authorNickname: item.authorNickname,
        content: item.content || content,
        createdAt: item.createdAt,
        type: 'spot',
        spotJson: item.spotJson ?? undefined,
      },
      'spot'
    )
    await nextTick()
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '发送失败')
  } finally {
    chatSending.value = false
  }
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendTextMessage()
  }
}

/** 插入表情到输入框 */
function pickEmoji() {
  newMessage.value += newMessage.value ? ' 😊' : '😊'
}

/** 选择图片并发送（Base64 上传） */
async function handleImageChange(file: any) {
  const raw = file?.raw as File | undefined
  if (!raw || !postId.value || !canSendChat.value) return false
  chatSending.value = true
  try {
    const dataUrl = await readFileAsDataUrl(raw)
    const item = await companionApi.sendPostChatMessage(postId.value, dataUrl, { type: 'image' })
    appendLocalMessage(
      {
        id: item.id,
        authorNickname: item.authorNickname,
        content: item.content || '[图片]',
        createdAt: item.createdAt,
        type: 'image',
      },
      'image'
    )
    await nextTick()
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '图片发送失败')
  } finally {
    chatSending.value = false
  }
  return false
}

function readFileAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as string)
    reader.onerror = () => reject(new Error('读取图片失败'))
    reader.readAsDataURL(file)
  })
}

/** 发送当前结伴行程卡片 */
async function sendCompanionQuick() {
  if (!post.value || !postId.value || !canSendChat.value) {
    ElMessage.info('当前无结伴活动信息')
    return
  }
  const payload: TeamChatCompanionPayload = {
    id: postId.value,
    destination: post.value.destination || '结伴活动',
    startDate: post.value.startDate || '',
    endDate: post.value.endDate || '',
  }
  const content = `分享了一个结伴活动：${payload.destination}`
  chatSending.value = true
  try {
    const item = await companionApi.sendPostChatMessage(postId.value, content, {
      type: 'companion',
      companionJson: JSON.stringify(payload),
    })
    appendLocalMessage(
      {
        id: item.id,
        authorNickname: item.authorNickname,
        content: item.content || content,
        createdAt: item.createdAt,
        type: 'companion',
        companionJson: item.companionJson ?? undefined,
      },
      'companion'
    )
    await nextTick()
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '发送失败')
  } finally {
    chatSending.value = false
  }
}

/** 加载收藏的路线列表（用于发送路线） */
async function loadFavoriteRoutes() {
  if (!auth.token) {
    ElMessage.warning('请先登录')
    return
  }
  routeDialogVisible.value = true
  favoriteRoutesLoading.value = true
  favoriteRoutes.value = []
  try {
    const favs = await interactionsApi.myFavorites()
    const routeIds = (favs || []).filter((f) => f.targetType === 'route').map((f) => f.targetId)
    for (const rid of routeIds) {
      try {
        const plan = await routesApi.getOne(rid)
        if (plan) {
          favoriteRoutes.value.push({
            id: plan.id,
            title: plan.title || plan.destination || '路线',
            destination: plan.destination || '',
            days: (plan.days?.length ?? 0) || 1,
          })
        }
      } catch {
        // skip
      }
    }
    if (favoriteRoutes.value.length === 0) {
      ElMessage.info('暂无收藏路线，请先收藏路线后再发送')
    }
  } catch {
    ElMessage.error('加载收藏路线失败')
  } finally {
    favoriteRoutesLoading.value = false
  }
}

/** 发送选中的路线卡片 */
async function sendRouteMessage(r: FavoriteRouteItem) {
  routeDialogVisible.value = false
  if (!postId.value || !canSendChat.value) return
  const payload: TeamChatRoutePayload = {
    id: r.id,
    title: r.title,
    days: r.days,
    destination: r.destination,
  }
  const content = `分享了一条路线：${r.title}`
  chatSending.value = true
  try {
    const item = await companionApi.sendPostChatMessage(postId.value, content, {
      type: 'route',
      routeJson: JSON.stringify(payload),
    })
    appendLocalMessage(
      {
        id: item.id,
        authorNickname: item.authorNickname,
        content: item.content || content,
        createdAt: item.createdAt,
        type: 'route',
        routeJson: item.routeJson ?? undefined,
      },
      'route'
    )
    await nextTick()
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.message || e?.message || '发送失败')
  } finally {
    chatSending.value = false
  }
}

function openImagePreview(url: string) {
  previewImageUrl.value = url
  previewVisible.value = true
}

watch(postId, () => {
  fetchPostAndTeam().then(() => {
    if (postId.value) fetchChatHistory()
  })
}, { immediate: false })

onMounted(async () => {
  await fetchPostAndTeam()
  await fetchChatHistory()
})
</script>

<template>
  <div class="team-chat-page">
    <header class="team-chat-header">
      <div class="header-inner">
        <el-button link class="back-btn" @click="router.back()">← 返回</el-button>
        <div class="header-title">{{ pageTitle }}</div>
        <el-button type="primary" size="small" class="detail-btn" @click="goToCompanionDetail">
          查看详情
        </el-button>
      </div>
    </header>

    <main class="team-chat-main">
      <div v-if="!post" class="loading-wrap">
        <p class="text-subtle">加载中…</p>
      </div>
      <template v-else>
        <div v-if="!canSendChat" class="placeholder-wrap">
          <p class="text-subtle">仅小队成员可查看群聊，请先加入该结伴活动。</p>
          <el-button type="primary" @click="goToCompanionDetail">去结伴详情页加入</el-button>
        </div>
        <template v-else>
          <section ref="messageListRef" class="message-list" :class="{ 'is-loading': chatLoading }">
            <div v-if="chatLoading" class="chat-loading text-subtle">加载聊天记录...</div>
            <template v-else>
              <template v-for="(m, idx) in messages" :key="m.id">
                <div v-if="shouldShowTimeDivider(m, messages[idx - 1])" class="time-divider">
                  <span>{{ m.time }}</span>
                </div>
                <div class="msg-row" :class="m.fromSelf ? 'is-me' : 'is-other'">
                  <template v-if="!m.fromSelf">
                    <el-avatar class="msg-avatar msg-avatar-left" size="small">
                      {{ m.author.charAt(0).toUpperCase() }}
                    </el-avatar>
                    <div class="bubble-wrap">
                      <el-card
                        v-if="m.type === 'spot' && m.spotPayload"
                        class="bubble-card spot"
                        shadow="hover"
                        @click="goToRouteDetail(m.spotPayload!.routeId)"
                      >
                        <div class="card-title">景点：{{ m.spotPayload.name }}</div>
                        <div v-if="m.spotPayload.location" class="card-meta">{{ m.spotPayload.location }}</div>
                        <div class="card-link">查看路线详情 →</div>
                      </el-card>
                      <el-card
                        v-else-if="m.type === 'route' && m.routePayload"
                        class="bubble-card route"
                        shadow="hover"
                        @click="goToRouteDetail(m.routePayload!.id)"
                      >
                        <div class="card-title">路线：{{ m.routePayload.title }}</div>
                        <div class="card-meta">{{ m.routePayload.destination }} · {{ m.routePayload.days }} 天</div>
                        <div class="card-link">查看路线详情 →</div>
                      </el-card>
                      <el-card
                        v-else-if="m.type === 'companion' && m.companionPayload"
                        class="bubble-card companion"
                        shadow="hover"
                        @click="goToCompanionDetail()"
                      >
                        <div class="card-title">结伴：{{ m.companionPayload.destination }}</div>
                        <div class="card-meta">{{ m.companionPayload.startDate }} – {{ m.companionPayload.endDate }}</div>
                        <div class="card-link">查看结伴详情 →</div>
                      </el-card>
                      <div
                        v-else-if="m.type === 'image' && m.imageUrl"
                        class="bubble bubble-image other"
                        @click="openImagePreview(m.imageUrl!)"
                      >
                        <img :src="m.imageUrl" alt="图片" class="image-content" />
                      </div>
                      <div v-else class="bubble bubble-text other">
                        <p>{{ m.content }}</p>
                      </div>
                    </div>
                  </template>
                  <template v-else>
                    <div class="bubble-wrap from-me">
                      <el-card
                        v-if="m.type === 'spot' && m.spotPayload"
                        class="bubble-card spot"
                        shadow="hover"
                        @click="goToRouteDetail(m.spotPayload!.routeId)"
                      >
                        <div class="card-title">景点：{{ m.spotPayload.name }}</div>
                        <div v-if="m.spotPayload.location" class="card-meta">{{ m.spotPayload.location }}</div>
                        <div class="card-link">查看路线详情 →</div>
                      </el-card>
                      <el-card
                        v-else-if="m.type === 'route' && m.routePayload"
                        class="bubble-card route"
                        shadow="hover"
                        @click="goToRouteDetail(m.routePayload!.id)"
                      >
                        <div class="card-title">路线：{{ m.routePayload.title }}</div>
                        <div class="card-meta">{{ m.routePayload.destination }} · {{ m.routePayload.days }} 天</div>
                        <div class="card-link">查看路线详情 →</div>
                      </el-card>
                      <el-card
                        v-else-if="m.type === 'companion' && m.companionPayload"
                        class="bubble-card companion"
                        shadow="hover"
                        @click="goToCompanionDetail()"
                      >
                        <div class="card-title">结伴：{{ m.companionPayload.destination }}</div>
                        <div class="card-meta">{{ m.companionPayload.startDate }} – {{ m.companionPayload.endDate }}</div>
                        <div class="card-link">查看结伴详情 →</div>
                      </el-card>
                      <div
                        v-else-if="m.type === 'image' && m.imageUrl"
                        class="bubble bubble-image me"
                        @click="openImagePreview(m.imageUrl!)"
                      >
                        <img :src="m.imageUrl" alt="图片" class="image-content" />
                      </div>
                      <div v-else class="bubble bubble-text me">
                        <p>{{ m.content }}</p>
                      </div>
                    </div>
                    <el-avatar class="msg-avatar msg-avatar-right" size="small">
                      {{ (auth.nickname || '我').charAt(0).toUpperCase() }}
                    </el-avatar>
                  </template>
                </div>
              </template>
            </template>
          </section>

          <footer class="composer">
            <div class="toolbar">
              <el-button text size="small" @click="pickEmoji">表情</el-button>
              <el-upload :show-file-list="false" accept="image/*" :auto-upload="false" :on-change="handleImageChange">
                <el-button text size="small">图片</el-button>
              </el-upload>
              <el-button text size="small" @click="sendCompanionQuick">发送行程</el-button>
              <el-button text size="small" @click="loadFavoriteRoutes">发送路线</el-button>
              <el-button text size="small" @click="loadFavoriteSpots">发送景点</el-button>
            </div>
            <div class="input-row">
              <el-input
                v-model="newMessage"
                type="textarea"
                :autosize="{ minRows: 1, maxRows: 4 }"
                placeholder="输入消息，Enter 发送（Shift+Enter 换行）"
                class="chat-input"
                :disabled="!auth.token || !canSendChat"
                @keydown="handleKeydown"
              />
              <el-button type="primary" :loading="chatSending" @click="sendTextMessage">发送</el-button>
            </div>
          </footer>
        </template>
      </template>
    </main>

    <el-dialog
      v-model="spotDialogVisible"
      title="选择要发送的景点（来自收藏的路线）"
      width="90%"
      max-width="480px"
    >
      <div v-if="favoriteSpotsLoading" class="spot-dialog-loading text-subtle">加载中…</div>
      <el-empty v-else-if="!favoriteSpots.length" description="暂无收藏路线中的景点，请先收藏包含景点的路线" />
      <el-scrollbar v-else max-height="320px" class="spot-list">
        <div
          v-for="(s, i) in favoriteSpots"
          :key="`${s.routeId}-${s.dayIndex}-${s.activityIndex}-${i}`"
          class="spot-item"
          @click="sendSpotMessage(s)"
        >
          <div class="spot-item-main">
            <span class="spot-name">{{ s.name }}</span>
            <span v-if="s.location" class="spot-location">{{ s.location }}</span>
            <span class="spot-route">来自：{{ s.routeTitle }}</span>
          </div>
        </div>
      </el-scrollbar>
    </el-dialog>

    <el-dialog
      v-model="routeDialogVisible"
      title="选择要发送的路线（来自收藏）"
      width="90%"
      max-width="480px"
    >
      <div v-if="favoriteRoutesLoading" class="spot-dialog-loading text-subtle">加载中…</div>
      <el-empty v-else-if="!favoriteRoutes.length" description="暂无收藏路线，请先收藏路线后再发送" />
      <el-scrollbar v-else max-height="320px" class="spot-list">
        <div
          v-for="(r, i) in favoriteRoutes"
          :key="`${r.id}-${i}`"
          class="spot-item"
          @click="sendRouteMessage(r)"
        >
          <div class="spot-item-main">
            <span class="spot-name">{{ r.title }}</span>
            <span v-if="r.destination" class="spot-location">{{ r.destination }} · {{ r.days }} 天</span>
          </div>
        </div>
      </el-scrollbar>
    </el-dialog>

    <el-dialog v-model="previewVisible" width="520px">
      <img v-if="previewImageUrl" :src="previewImageUrl" class="preview-img" alt="预览" />
    </el-dialog>
  </div>
</template>

<style scoped>
.team-chat-page {
  min-height: 100vh;
  background: radial-gradient(circle at top left, #e0f2fe 0, transparent 55%),
    radial-gradient(circle at bottom right, #fef3c7 0, transparent 55%),
    #e5e7eb;
  display: flex;
  flex-direction: column;
}

.team-chat-header {
  background: #020617;
  color: #e5e7eb;
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.7);
}

.header-inner {
  max-width: 980px;
  margin: 0 auto;
  padding: 0 16px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.back-btn {
  color: #e5e7eb;
  flex-shrink: 0;
}

.header-title {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  font-size: 16px;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-btn {
  flex-shrink: 0;
}

.team-chat-main {
  flex: 1;
  width: 100%;
  max-width: 980px;
  margin: 12px auto 0;
  padding: 0 12px 12px;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.loading-wrap,
.placeholder-wrap {
  padding: 48px 24px;
  text-align: center;
}

.placeholder-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
}

.text-subtle {
  color: #64748b;
  font-size: 14px;
}

.message-list {
  flex: 1;
  background: rgba(248, 250, 252, 0.92);
  border-radius: 16px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.18);
  padding: 12px 12px 8px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.message-list.is-loading {
  display: flex;
  align-items: flex-start;
  justify-content: center;
}

.chat-loading {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
}

.time-divider {
  display: flex;
  justify-content: center;
  margin: 4px 0 6px;
}

.time-divider span {
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  background: #e5e7eb;
  color: #6b7280;
}

.msg-row {
  display: flex;
  align-items: flex-end;
  gap: 6px;
}

.msg-row.is-other {
  justify-content: flex-start;
}

.msg-row.is-me {
  justify-content: flex-end;
}

.msg-avatar {
  flex-shrink: 0;
  background: linear-gradient(135deg, #14b8a6, #6366f1);
  color: #fff;
  font-weight: 600;
}

.msg-avatar-left {
  order: 0;
}

.msg-avatar-right {
  order: 1;
}

.bubble-wrap {
  max-width: 72%;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.bubble {
  padding: 8px 10px;
  border-radius: 14px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}

.bubble-text.me {
  background: #4f46e5;
  color: #f9fafb;
  border-bottom-right-radius: 2px;
}

.bubble-text.other {
  background: #ffffff;
  color: #111827;
  border-bottom-left-radius: 2px;
}

.bubble-card {
  border-radius: 12px;
  padding: 8px 10px;
  cursor: pointer;
}

.bubble-card.spot {
  border-left: 3px solid #f59e0b;
}

.bubble-card.route {
  border-left: 3px solid #3b82f6;
}

.bubble-card.companion {
  border-left: 3px solid #10b981;
}

.bubble-image {
  padding: 4px;
  border-radius: 12px;
  max-width: 240px;
  cursor: pointer;
}

.bubble-image.me {
  border-bottom-right-radius: 2px;
}

.bubble-image.other {
  border-bottom-left-radius: 2px;
}

.bubble-image .image-content {
  max-width: 100%;
  height: auto;
  display: block;
  border-radius: 8px;
}

.preview-img {
  width: 100%;
  height: auto;
  display: block;
}

.card-title {
  font-size: 13px;
  font-weight: 700;
  margin-bottom: 2px;
}

.card-meta {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.card-link {
  font-size: 12px;
  color: #2563eb;
}

.composer {
  margin-top: 10px;
  background: rgba(248, 250, 252, 0.96);
  border-radius: 16px;
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.12);
  padding: 10px 10px 8px;
}

.toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.input-row {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.chat-input :deep(.el-textarea__inner) {
  border-radius: 999px;
}

.spot-dialog-loading {
  padding: 24px;
  text-align: center;
}

.spot-list {
  padding: 4px 0;
}

.spot-item {
  padding: 10px 12px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.18s ease;
}

.spot-item:hover {
  background: #f1f5f9;
}

.spot-item-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.spot-name {
  font-weight: 600;
  color: #0f172a;
}

.spot-location {
  font-size: 12px;
  color: #64748b;
}

.spot-route {
  font-size: 11px;
  color: #94a3b8;
}
</style>
