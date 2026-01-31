<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  useChatStore,
  type ChatMessage,
  type ChatRoutePayload,
  type ChatCompanionPayload,
  type ChatSpotPayload,
} from '../store/chat'
import { useAuthStore } from '../store'
import { useMessageStore } from '../store/message'
import { messageApi, interactionsApi, routesApi, userApi } from '../api'
import { useSpotStore } from '../store/spot'
import { loadAmapScript, initAmapMap, addMarker } from '../utils/amap'
import { getSpotFavoriteDisplay } from '../utils/spot_favorite_display'
import type { PlanResponse, TripPlanActivity } from '../api/types'

const route = useRoute()
const router = useRouter()
const chatStore = useChatStore()
const auth = useAuthStore()
const messageStore = useMessageStore()
const spotStore = useSpotStore()

const contactId = computed(() => {
  const raw = route.params.id
  return raw && !Array.isArray(raw) ? String(raw) : '0'
})

const contactName = ref('旅友')

async function loadContactNickname() {
  const q = route.query.nickname
  if (typeof q === 'string' && q.trim()) {
    contactName.value = q.trim()
    return
  }
  const peerId = Number(contactId.value)
  if (!peerId || contactId.value === '0') return
  try {
    const profile = await userApi.getPublicProfile(peerId)
    contactName.value = profile?.nickname?.trim() || '旅友'
  } catch {
    contactName.value = '旅友'
  }
}

const contactRole = computed(() => {
  const q = route.query.role
  return typeof q === 'string' && q.trim() ? q.trim() : '结伴发起人'
})

const companionPayload = computed<ChatCompanionPayload | null>(() => {
  const dest = typeof route.query.destination === 'string' ? route.query.destination : ''
  const start = typeof route.query.startDate === 'string' ? route.query.startDate : ''
  const end = typeof route.query.endDate === 'string' ? route.query.endDate : ''
  const cid = route.query.companionId
  if (!dest || !start || !end || !cid || Array.isArray(cid)) return null
  return { id: Number(cid), destination: dest, startDate: start, endDate: end }
})

const sessionId = computed(() => `user-${contactId.value}`)
const messages = computed<ChatMessage[]>(() => chatStore.getMessages(sessionId.value))

const draft = ref('')
const sending = ref(false)
const previewVisible = ref(false)
const previewImageUrl = ref('')
const guideVisible = ref(false)

/** 景点位置地图弹窗 */
const spotMapVisible = ref(false)
const spotMapPayload = ref<{ name: string; lng: number; lat: number } | null>(null)
const spotMapContainerRef = ref<HTMLDivElement | null>(null)
let spotMapInstance: any = null
let spotMapMarker: any = null

/** 发送景点：弹窗与收藏景点列表 */
const spotDialogVisible = ref(false)
const favoriteSpots = ref<FavoriteSpotItem[]>([])
const favoriteSpotsLoading = ref(false)

const messageListRef = ref<HTMLDivElement | null>(null)

/** 从收藏路线或收藏景点中收集的「可发送景点」 */
interface FavoriteSpotItem {
  routeId: number
  routeTitle: string
  dayIndex: number
  activityIndex: number
  name: string
  location?: string
  /** 独立收藏的景点 ID（来自景点详情页），有值时 routeId 为 0 */
  spotId?: number
  /** 景点预览/封面图 */
  imageUrl?: string
  lng?: number
  lat?: number
}

async function ensureLogin() {
  if (auth.token) return true
  try {
    await ElMessageBox.confirm('登录后才可以与旅友私信沟通和发送路线～', '请先登录', {
      confirmButtonText: '去登录',
      cancelButtonText: '取消',
      type: 'warning',
    })
    router.push({ name: 'login', query: { redirect: route.fullPath } })
  } catch {
    // 用户取消
  }
  return false
}

function scrollToBottom(immediate = false) {
  const fn = () => {
    const el = messageListRef.value
    if (!el) return
    el.scrollTop = el.scrollHeight
  }
  if (immediate) fn()
  else nextTick(fn)
}

function formatTimeLabel(iso: string) {
  const d = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const oneDay = 24 * 60 * 60 * 1000
  if (diff < oneDay) return d.toTimeString().slice(0, 5)
  if (diff < 2 * oneDay) return `昨天 ${d.toTimeString().slice(0, 5)}`
  return d.toLocaleString('zh-CN', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function shouldShowTimeDivider(curr: ChatMessage, prev?: ChatMessage) {
  if (!prev) return true
  const currTime = new Date(curr.createdAt).getTime()
  const prevTime = new Date(prev.createdAt).getTime()
  return currTime - prevTime > 10 * 60 * 1000
}

function detectSensitive(text: string) {
  const lowered = text.toLowerCase()
  const patterns = [/wechat|微信/, /qq/, /alipay|支付宝/, /转账|打款/, /银行卡/, /身份证/]
  return patterns.some((re) => re.test(lowered))
}

async function sendText() {
  const content = draft.value.trim()
  if (!content || sending.value) return
  const ok = await ensureLogin()
  if (!ok) return
  const peerId = Number(contactId.value)
  if (!peerId || !auth.userId) return

  if (detectSensitive(content)) {
    await ElMessageBox.alert(
      '为保障资金与人身安全，请优先在平台内沟通并确认行程后，再考虑交换微信、QQ 等第三方联系方式或转账。',
      '安全提醒',
      { confirmButtonText: '我已了解', type: 'warning' },
    )
  }

  sending.value = true
  draft.value = ''
  try {
    await messageApi.sendChatMessage(peerId, content)
    const list = await messageApi.getChatMessages(peerId)
    chatStore.setMessagesFromApi(sessionId.value, list, auth.userId!)
    // 发送消息后刷新未读数（因为对方会收到新消息，未读数可能变化）
    await messageStore.fetchOverview().catch(() => {})
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e?.message || '发送失败，请稍后重试')
    draft.value = content
  } finally {
    sending.value = false
  }
}

function retrySend(m: ChatMessage) {
  if (m.from !== 'me' || m.type !== 'text' || !m.failed) return
  m.failed = false
  ElMessage.success('已重试发送（示例）')
}

function handleKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendText()
  }
}

function pickEmoji() {
  draft.value += draft.value ? ' 😊' : '😊'
}

function openImagePreview(url: string) {
  previewImageUrl.value = url
  previewVisible.value = true
}

async function handleImageChange(file: any) {
  const ok = await ensureLogin()
  if (!ok) return false
  const raw = file.raw as File | undefined
  if (!raw) return false
  const peerId = Number(contactId.value)
  if (!peerId || !auth.userId) return false
  sending.value = true
  try {
    const dataUrl = await readFileAsDataUrl(raw)
    await messageApi.sendChatMessage(peerId, dataUrl, 'image')
    const list = await messageApi.getChatMessages(peerId)
    chatStore.setMessagesFromApi(sessionId.value, list, auth.userId)
    await messageStore.fetchOverview().catch(() => {})
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e?.message || e?.response?.data?.message || '图片发送失败')
  } finally {
    sending.value = false
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

async function sendRouteQuick() {
  const ok = await ensureLogin()
  if (!ok) return
  const payload: ChatRoutePayload = {
    id: Number(route.query.routeId || 2),
    title: typeof route.query.routeTitle === 'string' ? route.query.routeTitle : '推荐路线：轻松慢游 6 日',
    days: Number(route.query.routeDays || 6),
    destination: typeof route.query.destination === 'string' ? route.query.destination : '目的地待定',
  }
  chatStore.addRouteCard(sessionId.value, 'me', payload)
  scrollToBottom()
}

async function sendCompanionQuick() {
  const ok = await ensureLogin()
  if (!ok) return
  if (!companionPayload.value) {
    ElMessage.info('当前会话未关联结伴活动')
    return
  }
  chatStore.addCompanionCard(sessionId.value, 'me', companionPayload.value)
  scrollToBottom()
}

/** 打开「发送景点」弹窗，加载收藏路线中的景点 + 收藏的独立景点 */
async function loadFavoriteSpots() {
  const ok = await ensureLogin()
  if (!ok) return
  spotDialogVisible.value = true
  await fetchFavoriteSpots()
}

async function fetchFavoriteSpots() {
  favoriteSpotsLoading.value = true
  favoriteSpots.value = []
  try {
    const favs = await interactionsApi.myFavorites()
    const spots: FavoriteSpotItem[] = []
    const favList = Array.isArray(favs) ? favs : []

    // 1. 收藏路线中的景点（targetType 兼容大小写）
    const routeIds = favList
      .filter((f) => (f.targetType || '').toLowerCase() === 'route')
      .map((f) => Number(f.targetId))
      .filter((id) => id > 0)
    for (const rid of routeIds) {
      try {
        const plan = await routesApi.getOne(rid)
        if (plan?.days?.length) {
          for (const day of plan.days) {
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
                imageUrl: `https://picsum.photos/seed/route-${plan.id}-${day.dayIndex}-${idx}/400/250`,
              })
            })
          }
        }
      } catch {
        // skip
      }
    }

    // 2. 收藏的独立景点（来自景点详情页；优先用收藏时保存的展示信息，避免同一 ID 显示错误名称）
    const spotIds = favList
      .filter((f) => (f.targetType || '').toLowerCase() === 'spot')
      .map((f) => Number(f.targetId))
      .filter((id) => id > 0)
    for (const sid of spotIds) {
      const saved = getSpotFavoriteDisplay(sid)
      const brief = saved
        ? {
            name: saved.name,
            location: saved.location,
            imageUrl: saved.imageUrl,
            lng: saved.lng,
            lat: saved.lat,
          }
        : spotStore.getSpotBrief(sid)
      spots.push({
        routeId: 0,
        routeTitle: '收藏的景点',
        dayIndex: 0,
        activityIndex: 0,
        name: brief.name,
        location: brief.location,
        spotId: sid,
        imageUrl: brief.imageUrl,
        lng: brief.lng,
        lat: brief.lat,
      })
    }

    favoriteSpots.value = spots
    if (spots.length === 0) {
      ElMessage.info('暂无收藏景点，请先收藏路线中的景点或前往景点详情页收藏')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载收藏景点失败')
  } finally {
    favoriteSpotsLoading.value = false
  }
}

/** 发送选中的景点卡片（调用后端 API 并刷新消息列表） */
async function sendSpotMessage(spot: FavoriteSpotItem) {
  spotDialogVisible.value = false
  const peerId = Number(contactId.value)
  if (!peerId || !auth.userId) return
  const payload: ChatSpotPayload & { spotId?: number } = {
    routeId: spot.routeId,
    dayIndex: spot.dayIndex,
    activityIndex: spot.activityIndex,
    name: spot.name,
    location: spot.location,
    imageUrl: spot.imageUrl,
    lng: spot.lng,
    lat: spot.lat,
  }
  if (spot.spotId) payload.spotId = spot.spotId
  const content = `分享了一个景点：${spot.name}`
  sending.value = true
  try {
    await messageApi.sendChatMessage(peerId, content, 'spot', JSON.stringify(payload))
    const list = await messageApi.getChatMessages(peerId)
    chatStore.setMessagesFromApi(sessionId.value, list, auth.userId)
    await messageStore.fetchOverview().catch(() => {})
    scrollToBottom()
  } catch (e: any) {
    ElMessage.error(e?.message || e?.response?.data?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

function goRouteDetail(payload?: ChatRoutePayload) {
  if (!payload) return
  router.push(`/routes/${payload.id}`)
}

/** 点击景点卡片跳转路线详情或景点详情（独立收藏景点有 spotId） */
function goSpotRoute(spotPayload?: { routeId: number; spotId?: number }) {
  if (!spotPayload) return
  if (spotPayload.spotId) {
    router.push(`/spots/${spotPayload.spotId}`)
  } else if (spotPayload.routeId) {
    router.push(`/routes/${spotPayload.routeId}`)
  }
}

/** 打开景点位置地图弹窗（高德地图） */
async function openSpotMap(payload: { name: string; lng?: number; lat?: number }) {
  if (payload.lng == null || payload.lat == null) {
    ElMessage.info('该景点暂无位置信息')
    return
  }
  spotMapPayload.value = { name: payload.name, lng: payload.lng, lat: payload.lat }
  spotMapVisible.value = true
  await nextTick()
  await new Promise((r) => setTimeout(r, 100))
  const container = spotMapContainerRef.value
  if (!container) return
  try {
    await loadAmapScript()
    if (spotMapInstance) {
      spotMapInstance.destroy()
      spotMapInstance = null
      spotMapMarker = null
    }
    const center: [number, number] = [payload.lng, payload.lat]
    spotMapInstance = initAmapMap(container, center, 15, {
      viewMode: '2D',
      mapStyle: 'amap://styles/normal',
      forceTileLayer: true,
    })
    spotMapMarker = addMarker(spotMapInstance, center, payload.name, payload.name)
  } catch (e: any) {
    ElMessage.warning(e?.message || '地图加载失败')
  }
}

function closeSpotMap() {
  spotMapVisible.value = false
  spotMapPayload.value = null
  if (spotMapInstance) {
    spotMapInstance.destroy()
    spotMapInstance = null
  }
  spotMapMarker = null
}

function goCompanionDetail(payload?: ChatCompanionPayload) {
  if (!payload) return
  router.push(`/companion/${payload.id}`)
}

function goCurrentCompanion() {
  if (!companionPayload.value) return
  goCompanionDetail(companionPayload.value)
}

function goUserProfile() {
  if (!contactId.value || contactId.value === '0') return
  router.push({ name: 'user-profile', params: { id: contactId.value } })
}

function handleReport() {
  ElMessageBox.confirm('确认要举报该用户吗？我们会优先处理涉及诈骗、辱骂等行为的举报。', '举报确认', {
    confirmButtonText: '确认举报',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => ElMessage.success('已提交举报，我们会尽快处理。'))
    .catch(() => {})
}

function handleBlock() {
  ElMessageBox.confirm('拉黑后将不再收到对方的新消息，确认继续吗？', '拉黑确认', {
    confirmButtonText: '确认拉黑',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => ElMessage.success('已拉黑该用户，可在账户设置中解除。'))
    .catch(() => {})
}

function handleClear() {
  ElMessageBox.confirm('确定清空当前聊天记录吗？此操作仅影响本设备显示。', '清空聊天记录', {
    confirmButtonText: '清空',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      ;(chatStore.sessions as any)[sessionId.value] = []
    })
    .catch(() => {})
}

async function loadChatMessages() {
  const peerId = Number(contactId.value)
  if (!peerId || contactId.value === '0') return
  if (auth.userId) {
    try {
      const list = await messageApi.getChatMessages(peerId)
      const oldLength = messages.value.length
      chatStore.setMessagesFromApi(sessionId.value, list, auth.userId)
      await messageApi.clearChatUnread(peerId).catch(() => {})
      // 如果有新消息，滚动到底部
      if (list.length > oldLength) {
        scrollToBottom()
      }
    } catch {
      // 未登录或网络错误，保持空列表
    }
  }
  if (messages.value.length === 0) {
    chatStore.addSystemTip(sessionId.value, '提示：平台不展示第三方联系方式，请勿在未确认前添加微信或转账。')
  }
  scrollToBottom(true)
}

let pollTimer: number | null = null

watch([contactId, () => route.query.nickname], () => {
  loadContactNickname()
})

onMounted(() => {
  loadContactNickname()
  loadChatMessages()
  // 每5秒轮询一次新消息（仅在聊天页时）
  pollTimer = window.setInterval(() => {
    const peerId = Number(contactId.value)
    if (peerId && auth.userId) {
      messageApi.getChatMessages(peerId).then((list) => {
        const oldLength = messages.value.length
        chatStore.setMessagesFromApi(sessionId.value, list, auth.userId!)
        if (list.length > oldLength) {
          scrollToBottom()
        }
      }).catch(() => {})
    }
  }, 5000)
})

onBeforeUnmount(() => {
  if (previewImageUrl.value) URL.revokeObjectURL(previewImageUrl.value)
  if (pollTimer != null) {
    clearInterval(pollTimer)
    pollTimer = null
  }
  closeSpotMap()
})
</script>

<template>
  <div class="chat-page">
    <el-header class="chat-header" height="64px">
      <div class="header-inner">
        <el-button link class="back-btn" @click="router.back()">← 返回</el-button>

        <div class="contact" @click="goUserProfile">
          <el-avatar class="contact-avatar" size="small">
            {{ contactName.charAt(0).toUpperCase() }}
          </el-avatar>
          <div class="contact-meta">
            <div class="contact-name">
              {{ contactName }}
              <el-tag size="small" type="warning" effect="plain" class="contact-role">
                {{ contactRole }}
              </el-tag>
            </div>
            <div class="contact-subtitle">结伴沟通 · 私信聊天</div>
          </div>
        </div>

        <el-dropdown>
          <span class="more-trigger">更多</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="goCurrentCompanion">查看结伴活动</el-dropdown-item>
              <el-dropdown-item divided @click="handleReport">举报</el-dropdown-item>
              <el-dropdown-item @click="handleBlock">拉黑</el-dropdown-item>
              <el-dropdown-item divided @click="handleClear">清空聊天记录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </el-header>

    <main class="chat-main">
      <section v-if="companionPayload" class="companion-hint">
        <el-card shadow="hover" class="companion-hint-card">
          <div class="hint-title">当前正在沟通的结伴活动</div>
          <div class="hint-body">
            <div class="hint-left">
              <div class="hint-dest">{{ companionPayload.destination }}</div>
              <div class="hint-date">{{ companionPayload.startDate }} – {{ companionPayload.endDate }}</div>
            </div>
            <el-button type="primary" plain @click="goCurrentCompanion">查看详情</el-button>
          </div>
        </el-card>
      </section>

      <section ref="messageListRef" class="message-list">
        <template v-for="(m, idx) in messages" :key="m.id">
          <div v-if="shouldShowTimeDivider(m, messages[idx - 1])" class="time-divider">
            <span>{{ formatTimeLabel(m.createdAt) }}</span>
          </div>

          <div v-if="m.type === 'system'" class="system-tip">
            {{ m.content }}
          </div>

          <div v-else class="msg-row" :class="m.from === 'me' ? 'is-me' : 'is-other'">
            <!-- 左侧消息：头像在左，气泡在右 -->
            <template v-if="m.from === 'other'">
              <el-avatar class="msg-avatar msg-avatar-left" size="small">
                {{ contactName.charAt(0).toUpperCase() }}
              </el-avatar>
              <div class="bubble-wrap">
                <el-card
                  v-if="m.type === 'route' && m.routePayload"
                  class="bubble-card route"
                  shadow="hover"
                  @click="goRouteDetail(m.routePayload)"
                >
                  <div class="card-title">路线：{{ m.routePayload.title }}</div>
                  <div class="card-meta">{{ m.routePayload.destination }} · {{ m.routePayload.days }} 天</div>
                  <div class="card-link">查看路线详情</div>
                </el-card>

                <el-card
                  v-else-if="m.type === 'spot' && m.spotPayload"
                  class="bubble-card spot bubble-card-spot"
                  shadow="hover"
                >
                  <div v-if="m.spotPayload.imageUrl" class="spot-card-cover">
                    <img :src="m.spotPayload.imageUrl" :alt="m.spotPayload.name" />
                  </div>
                  <div class="spot-card-body">
                    <div class="card-title">景点：{{ m.spotPayload.name }}</div>
                    <div v-if="m.spotPayload.location" class="card-meta spot-address">{{ m.spotPayload.location }}</div>
                    <div class="spot-card-actions">
                      <el-button
                        v-if="m.spotPayload.lng != null && m.spotPayload.lat != null"
                        type="primary"
                        size="small"
                        text
                        @click.stop="openSpotMap(m.spotPayload!)"
                      >
                        查看位置
                      </el-button>
                      <span class="card-link" @click.stop="goSpotRoute(m.spotPayload)">
                        {{ m.spotPayload.spotId ? '查看景点详情' : '查看路线详情' }}
                      </span>
                    </div>
                  </div>
                </el-card>

                <el-card
                  v-else-if="m.type === 'companion' && m.companionPayload"
                  class="bubble-card companion"
                  shadow="hover"
                  @click="goCompanionDetail(m.companionPayload)"
                >
                  <div class="card-title">结伴：{{ m.companionPayload.destination }}</div>
                  <div class="card-meta">{{ m.companionPayload.startDate }} – {{ m.companionPayload.endDate }}</div>
                  <div class="card-link">查看结伴详情 →</div>
                </el-card>

                <div
                  v-else-if="m.type === 'image' && m.imageUrl"
                  class="bubble bubble-image other"
                  @click="openImagePreview(m.imageUrl)"
                >
                  <img :src="m.imageUrl" alt="图片" class="image-content" />
                </div>

                <div v-else class="bubble bubble-text other">
                  <p>{{ m.content }}</p>
                </div>
              </div>
            </template>

            <!-- 右侧消息：气泡在左，头像在右（仿微信/QQ） -->
            <template v-else>
              <div class="bubble-wrap from-me">
                <el-card
                  v-if="m.type === 'route' && m.routePayload"
                  class="bubble-card route"
                  shadow="hover"
                  @click="goRouteDetail(m.routePayload)"
                >
                  <div class="card-title">路线：{{ m.routePayload.title }}</div>
                  <div class="card-meta">{{ m.routePayload.destination }} · {{ m.routePayload.days }} 天</div>
                  <div class="card-link">查看路线详情</div>
                </el-card>

                <el-card
                  v-else-if="m.type === 'spot' && m.spotPayload"
                  class="bubble-card spot bubble-card-spot"
                  shadow="hover"
                >
                  <div v-if="m.spotPayload.imageUrl" class="spot-card-cover">
                    <img :src="m.spotPayload.imageUrl" :alt="m.spotPayload.name" />
                  </div>
                  <div class="spot-card-body">
                    <div class="card-title">景点：{{ m.spotPayload.name }}</div>
                    <div v-if="m.spotPayload.location" class="card-meta spot-address">{{ m.spotPayload.location }}</div>
                    <div class="spot-card-actions">
                      <el-button
                        v-if="m.spotPayload.lng != null && m.spotPayload.lat != null"
                        type="primary"
                        size="small"
                        text
                        @click.stop="openSpotMap(m.spotPayload!)"
                      >
                        查看位置
                      </el-button>
                      <span class="card-link" @click.stop="goSpotRoute(m.spotPayload)">
                        {{ m.spotPayload.spotId ? '查看景点详情' : '查看路线详情' }}
                      </span>
                    </div>
                  </div>
                </el-card>

                <el-card
                  v-else-if="m.type === 'companion' && m.companionPayload"
                  class="bubble-card companion"
                  shadow="hover"
                  @click="goCompanionDetail(m.companionPayload)"
                >
                  <div class="card-title">结伴：{{ m.companionPayload.destination }}</div>
                  <div class="card-meta">{{ m.companionPayload.startDate }} – {{ m.companionPayload.endDate }}</div>
                  <div class="card-link">查看结伴详情 →</div>
                </el-card>

                <div
                  v-else-if="m.type === 'image' && m.imageUrl"
                  class="bubble bubble-image me"
                  @click="openImagePreview(m.imageUrl)"
                >
                  <img :src="m.imageUrl" alt="图片" class="image-content" />
                </div>

                <div v-else class="bubble bubble-text me" @click="retrySend(m)">
                  <p>{{ m.content }}</p>
                  <span v-if="m.failed" class="failed-tag">发送失败，点击重试</span>
                </div>
              </div>
              <el-avatar class="msg-avatar msg-avatar-right" size="small">
                {{ (auth.nickname || '我').charAt(0).toUpperCase() }}
              </el-avatar>
            </template>
          </div>
        </template>
      </section>

      <footer class="composer">
        <div class="toolbar">
          <el-button text size="small" @click="pickEmoji">表情</el-button>
          <el-upload :show-file-list="false" accept="image/*" :auto-upload="false" :on-change="handleImageChange">
            <el-button text size="small">图片</el-button>
          </el-upload>
          <el-button text size="small" @click="sendCompanionQuick">发送行程</el-button>
          <el-button text size="small" @click="sendRouteQuick">发送路线</el-button>
          <el-button text size="small" @click="loadFavoriteSpots">发送景点</el-button>
        </div>

        <div class="input-row">
          <el-input
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 4 }"
            placeholder="输入消息，Enter 发送（Shift+Enter 换行）"
            @keydown="handleKeydown"
          />
          <el-button type="primary" :loading="sending" @click="sendText">发送</el-button>
        </div>

        <div class="safety">
          为保障安全，请勿在未确认前添加微信或转账
          <a href="javascript:void(0)" @click="guideVisible = true">查看《安全指引》</a>
        </div>
      </footer>
    </main>

    <el-dialog v-model="previewVisible" width="520px">
      <img v-if="previewImageUrl" :src="previewImageUrl" class="preview-img" alt="预览" />
    </el-dialog>

    <el-dialog
      v-model="spotDialogVisible"
      title="选择要发送的景点（来自收藏的路线或景点）"
      width="90%"
      max-width="480px"
    >
      <div v-if="favoriteSpotsLoading" class="spot-dialog-loading text-subtle">加载中…</div>
      <div v-else-if="!favoriteSpots.length" class="spot-dialog-empty">
        <el-empty description="暂无收藏景点，请先收藏路线中的景点或前往景点详情页收藏">
          <template #extra>
            <el-button type="primary" size="small" :loading="favoriteSpotsLoading" @click="fetchFavoriteSpots">
              刷新
            </el-button>
          </template>
        </el-empty>
      </div>
      <el-scrollbar v-else max-height="380px" class="spot-list">
        <div
          v-for="(s, i) in favoriteSpots"
          :key="s.spotId ? `spot-${s.spotId}` : `route-${s.routeId}-${s.dayIndex}-${s.activityIndex}-${i}`"
          class="spot-item spot-item-form"
        >
          <div class="spot-item-left">
            <div class="spot-item-cover">
              <img :src="s.imageUrl || 'https://picsum.photos/seed/spot/400/250'" :alt="s.name" />
            </div>
            <div class="spot-item-main">
              <span class="spot-name">{{ s.name }}</span>
              <span v-if="s.location" class="spot-location">{{ s.location }}</span>
              <span class="spot-route">来自：{{ s.routeTitle }}</span>
            </div>
          </div>
          <el-button type="primary" size="small" class="spot-item-share" @click="sendSpotMessage(s)">
            分享
          </el-button>
        </div>
      </el-scrollbar>
    </el-dialog>

    <el-dialog v-model="guideVisible" title="出行安全指引" width="520px">
      <ul class="guide-list">
        <li>尽量在平台内完成沟通与结伴确认，谨慎线下交易与转账。</li>
        <li>不要轻易提供身份证号、银行卡号等敏感信息。</li>
        <li>如对方频繁催促付款或索要押金，请提高警惕并及时举报。</li>
        <li>线下见面请选择公共场所，提前告知亲友行程。</li>
      </ul>
      <template #footer>
        <el-button type="primary" @click="guideVisible = false">我已了解</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="spotMapVisible"
      :title="spotMapPayload ? `${spotMapPayload.name} · 位置` : '景点位置'"
      width="90%"
      max-width="560px"
      destroy-on-close
      @close="closeSpotMap"
    >
      <div ref="spotMapContainerRef" class="spot-map-container" />
      <template #footer>
        <el-button type="primary" @click="closeSpotMap">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.chat-page {
  min-height: 100vh;
  background: radial-gradient(circle at top left, #e0f2fe 0, transparent 55%),
    radial-gradient(circle at bottom right, #fef3c7 0, transparent 55%),
    #e5e7eb;
  display: flex;
  flex-direction: column;
}

.chat-header {
  background: #020617;
  color: #e5e7eb;
  box-shadow: 0 2px 12px rgba(15, 23, 42, 0.7);
}

.header-inner {
  max-width: 980px;
  margin: 0 auto;
  padding: 0 12px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.back-btn {
  color: #e5e7eb;
}

.contact {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.contact-avatar {
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
}

.contact-meta {
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.contact-name {
  display: flex;
  gap: 6px;
  align-items: center;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.contact-role {
  font-size: 11px;
}

.contact-subtitle {
  font-size: 12px;
  color: #9ca3af;
}

.more-trigger {
  cursor: pointer;
  font-size: 13px;
  color: #e5e7eb;
}

.chat-main {
  flex: 1;
  width: 100%;
  max-width: 980px;
  margin: 12px auto 0;
  padding: 0 12px 12px;
  display: flex;
  flex-direction: column;
}

.companion-hint {
  margin-bottom: 10px;
}

.companion-hint-card {
  border-radius: 16px;
}

.hint-title {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.hint-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.hint-dest {
  font-weight: 700;
  color: #0f172a;
}

.hint-date {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
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

.system-tip {
  align-self: center;
  max-width: 92%;
  padding: 6px 10px;
  border-radius: 999px;
  background: #fef9c3;
  color: #854d0e;
  font-size: 12px;
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
  order: 1; /* 确保在右侧消息时，头像在气泡右侧 */
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
  animation: fadeInUp 0.18s ease-out;
}

.bubble.me {
  background: #4f46e5;
  color: #f9fafb;
  border-bottom-right-radius: 2px;
}

.bubble.other {
  background: #ffffff;
  color: #111827;
  border-bottom-left-radius: 2px;
}

.bubble-image {
  padding: 4px;
  background: transparent;
}

.image-content {
  max-width: 220px;
  border-radius: 12px;
  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.25);
  animation: imgFade 0.25s ease-out;
}

.bubble-card {
  border-radius: 12px;
  padding: 8px 10px;
  cursor: pointer;
  animation: fadeInUp 0.18s ease-out;
}

.bubble-card.route {
  border-left: 3px solid #4f46e5;
}

.bubble-card.companion {
  border-left: 3px solid #0d9488;
}

.bubble-card.spot {
  border-left: 3px solid #f59e0b;
}

.bubble-card-spot {
  padding: 0;
  overflow: hidden;
  max-width: 260px;
}

.bubble-card-spot .spot-card-cover {
  width: 100%;
  height: 100px;
  overflow: hidden;
  background: #f1f5f9;
}

.bubble-card-spot .spot-card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.bubble-card-spot .spot-card-body {
  padding: 6px 10px 8px;
}

.bubble-card-spot .spot-card-body .card-title {
  font-size: 12px;
  margin-bottom: 2px;
}

.bubble-card-spot .spot-address {
  margin-bottom: 4px;
  font-size: 11px;
}

.bubble-card-spot .spot-card-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.bubble-card-spot .spot-card-actions .el-button,
.bubble-card-spot .spot-card-actions .card-link {
  font-size: 11px;
}

.bubble-card-spot .spot-card-actions .card-link {
  cursor: pointer;
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

.text-subtle {
  color: #64748b;
  font-size: 14px;
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
  transition: background 0.18s ease;
}

.spot-item-form {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
}

.spot-item-form:hover {
  background: #f1f5f9;
}

.spot-item-left {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
}

.spot-item-cover {
  width: 72px;
  height: 72px;
  flex-shrink: 0;
  border-radius: 10px;
  overflow: hidden;
  background: #e2e8f0;
}

.spot-item-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.spot-item-share {
  flex-shrink: 0;
}

.spot-item-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
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

.failed-tag {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #fecaca;
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
  align-items: center;
}

.input-row {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-row :deep(.el-textarea__inner) {
  border-radius: 999px;
}

.safety {
  margin-top: 6px;
  font-size: 11px;
  color: #94a3b8;
}

.safety a {
  color: #2563eb;
}

.preview-img {
  width: 100%;
  border-radius: 12px;
}

.guide-list {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.spot-map-container {
  width: 100%;
  height: 320px;
  background: #e2e8f0;
  border-radius: 12px;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(4px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes imgFade {
  from {
    opacity: 0;
    transform: scale(0.98);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

@media (max-width: 768px) {
  .chat-main {
    margin-top: 0;
    padding-bottom: 0;
  }

  .message-list {
    border-radius: 0;
  }

  .composer {
    border-radius: 0;
    position: sticky;
    bottom: 0;
  }

  .contact {
    justify-content: flex-start;
  }
}
</style>
