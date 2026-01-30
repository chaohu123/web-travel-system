<script setup lang="ts">
import { computed, nextTick, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, Star, Share, ChatDotRound } from '@element-plus/icons-vue'
import { useAuthStore } from '../store'
import { api, companionApi, routesApi, userApi } from '../api'
import { loadAmapScript, initAmapMap, addMarker, addPolyline, geocode } from '../utils/amap'

const auth = useAuthStore()
import type { CompanionPostDetail, CompanionPostSummary } from '../api'
import type { PlanResponse, TripPlanActivity } from '../api'
import type { UserPublicProfile } from '../api/types'

import type { TeamMemberItem } from '../api/types'

interface ChatMessage {
  id: number
  fromSelf: boolean
  author: string
  time: string
  content: string
  status: 'sent' | 'pending'
  type: 'text' | 'image' | 'voice'
}

const route = useRoute()
const router = useRouter()

// 返回上一页（与其它详情页一致）
function goBack() {
  if (window.history.length > 1) router.go(-1)
  else router.push('/')
}

const loading = ref(true)
const post = ref<CompanionPostDetail | null>(null)
const plan = ref<PlanResponse | null>(null)
const teamMembers = ref<TeamMemberItem[]>([])
const recommended = ref<CompanionPostSummary[]>([])
const applyLoading = ref(false)
const memberDrawerVisible = ref(false)
const selectedMember = ref<TeamMemberItem | null>(null)
const creatorProfile = ref<UserPublicProfile | null>(null)

const activeAccordion = ref<string | number>('')
const activeTab = ref<'itinerary' | 'map' | 'chat'>('itinerary')

// 聊天与协作
const messages = ref<ChatMessage[]>([])
const newMessage = ref('')
const chatLoading = ref(false)
const collaborationEvents = ref<string[]>([
  '队长 小鹿 更新了第 2 天的行程顺序',
  '新成员 行者老张 加入了活动',
  '旅友 桃桃 在聊天中发起了拼房讨论',
])

// 地图（高德地图）
const mapRef = ref<HTMLDivElement | null>(null)
let amapInstance: any = null
const mapMarkers: any[] = []
const mapPolylines: any[] = []
/** 用于 initMap 竞态：只有最新一次调用才允许创建/绘制地图 */
let mapRunId = 0
/** 地图当前展示的天：0 = 全部，否则为 dayIndex（如 1、2、3） */
const activeMapDay = ref(0)

const postId = computed(() => {
  const id = route.params.id
  return id && !Array.isArray(id) ? Number(id) : 0
})

const isFull = computed(() => {
  if (!post.value?.maxPeople || !teamMembers.value.length) return false
  return teamMembers.value.length >= post.value.maxPeople
})

const isJoined = computed(() => {
  if (!auth.userId) return false
  return teamMembers.value.some((m) => m.userId === auth.userId)
})

const isLeader = computed(() => {
  if (!auth.userId) return false
  return teamMembers.value.some((m) => m.userId === auth.userId && m.role === 'leader')
})

function tripDays(): number {
  const p = post.value
  if (!p?.startDate || !p?.endDate) return 0
  const a = new Date(p.startDate)
  const b = new Date(p.endDate)
  return Math.max(1, Math.ceil((b.getTime() - a.getTime()) / (24 * 60 * 60 * 1000)) + 1)
}

function reputationLabel(level?: number): string {
  if (level == null) return '普通'
  if (level >= 4) return '优质'
  if (level >= 3) return '良好'
  if (level >= 2) return '一般'
  return '普通'
}

function statusLabel(status?: string): string {
  if (!status) return '报名中'
  if (status === 'FULL') return '已满'
  if (status === 'FINISHED') return '已结束'
  return '报名中'
}

function statusType(status?: string): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'FULL') return 'danger'
  if (status === 'FINISHED') return 'info'
  return 'success'
}

const displayCreatorName = computed(() => {
  const p = creatorProfile.value
  if (p?.nickname?.trim() && !p.nickname.includes('@')) return p.nickname.trim()
  const name = (post.value?.creatorNickname || '').trim()
  if (name && !name.includes('@')) return name
  const id = post.value?.creatorId
  if (id != null) return `旅人${id}`
  return '旅人'
})

const displayCreatorAvatar = computed(() => creatorProfile.value?.avatar || post.value?.creatorAvatar || '')

const displayCreatorReputation = computed(() => creatorProfile.value?.reputationLevel ?? post.value?.creatorReputationLevel)

function memberDisplayName(m: TeamMemberItem): string {
  const name = (m.userName || '').trim()
  if (name && !name.includes('@')) return name
  return `用户${m.userId}`
}

function stateLabel(state?: string): string {
  if (!state) return '—'
  const map: Record<string, string> = { joined: '已加入', pending: '待确认', left: '已退出' }
  return map[state] ?? state
}

const creatorTagList = computed(() => {
  const raw = post.value?.creatorTags || ''
  const arr = raw.split(',').map(s => s.trim()).filter(Boolean)
  return arr.slice(0, 4)
})

const budgetText = computed(() => {
  const min = post.value?.budgetMin
  const max = post.value?.budgetMax
  if (!min && !max) return '待定'
  if (min != null && max != null) return `¥${min} - ${max}`
  if (min != null) return `¥${min}起`
  if (max != null) return `¥${max}内`
  return '待定'
})

function activityImageUrl(act: TripPlanActivity, dayIndex: number, idx: number): string {
  const anyAct = act as any
  if (anyAct?.imageUrl) return String(anyAct.imageUrl)
  const seed = encodeURIComponent(`${act.name || 'spot'}-${dayIndex}-${idx}`)
  return `https://picsum.photos/seed/${seed}/260/180`
}

function toggleFavorite() {
  ElMessage.info('收藏功能待接入（已加 icon 与布局）')
}

function shareCompanion() {
  const url = window.location.href
  navigator.clipboard
    .writeText(url)
    .then(() => ElMessage.success('已复制分享链接'))
    .catch(() => ElMessage.info('复制失败，请手动复制地址栏链接'))
}

async function fetchPost() {
  if (!postId.value) return
  try {
    post.value = await companionApi.getPost(postId.value)
  } catch {
    post.value = null
    return
  }
  if (post.value?.relatedPlanId) {
    try {
      plan.value = await routesApi.getOne(post.value.relatedPlanId)
    } catch {
      plan.value = null
    }
  } else {
    plan.value = null
  }

  // 兜底拉取发起人主页信息（用于昵称/头像更准确展示）
  creatorProfile.value = null
  if (post.value?.creatorId) {
    try {
      creatorProfile.value = await userApi.getPublicProfile(post.value.creatorId)
    } catch {
      creatorProfile.value = null
    }
  }

  if (post.value?.teamId) {
    try {
      const team = await companionApi.getTeam(post.value.teamId)
      teamMembers.value = team.members ?? []
    } catch {
      teamMembers.value = []
    }
  } else {
    teamMembers.value = []
  }
}

async function fetchRecommended() {
  try {
    const list = await companionApi.listPosts({ destination: post.value?.destination || undefined })
    recommended.value = (list || []).filter((p) => p.id !== postId.value).slice(0, 6)
  } catch {
    recommended.value = []
  }
}

// 模拟加载历史聊天记录（真实项目可对接后端接口）
async function fetchChatHistory() {
  chatLoading.value = true
  try {
    messages.value = [
      {
        id: 1,
        fromSelf: false,
        author: post.value?.creatorNickname || '发起人',
        time: '昨天 21:03',
        content: '大家好，我计划第 2 天主要安排市区景点，如果有推荐可以一起讨论～',
        status: 'sent',
        type: 'text',
      },
      {
        id: 2,
        fromSelf: true,
        author: auth.nickname || '我',
        time: '昨天 21:10',
        content: '我想在行程里加一个本地市集，可以帮忙看看是否合适吗？',
        status: 'sent',
        type: 'text',
      },
    ]
  } finally {
    chatLoading.value = false
  }
}

async function applyCompanion() {
  if (!post.value || applyLoading.value) return
  if (!auth.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    ElMessage.warning('请先登录后再申请结伴')
    return
  }
  if (isFull.value) {
    ElMessage.info('该活动人数已满，暂无法加入')
    return
  }
  applyLoading.value = true
  try {
    const res = await api.post<{ data: { data: number } }>('/companion/teams', null, { params: { postId: post.value.id } })
    const teamId = res.data.data
    await api.post(`/companion/teams/${teamId}/join`)
    ElMessage.success('已申请结伴，正在跳转小队…')
    router.push(`/teams/${teamId}`)
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '申请失败')
  } finally {
    applyLoading.value = false
  }
}

async function quitCompanion() {
  if (!post.value?.teamId) return
  if (!auth.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  try {
    await ElMessageBox.confirm('确认退出当前结伴活动？退出后需要重新申请才能加入。', '确认退出', {
      type: 'warning',
      confirmButtonText: '确认退出',
      cancelButtonText: '再想想',
    })
    await companionApi.quitTeam(post.value.teamId)
    ElMessage.success('已退出当前活动')
    await fetchPost()
  } catch {
    // 用户取消
  }
}

function openMemberDetail(member: TeamMemberItem) {
  selectedMember.value = member
  memberDrawerVisible.value = true
}

function activityTags(act: TripPlanActivity): string[] {
  const tags: string[] = []
  const t = (act.type || '').toLowerCase()
  if (t === 'sight') tags.push('文化')
  else if (t === 'food') tags.push('美食')
  else if (t === 'hotel') tags.push('休闲')
  else tags.push('小众')
  return tags
}

function goRecommend(item: CompanionPostSummary) {
  router.push(`/companion/${item.id}`)
}

// 发送文本消息（示例：仅前端入队）
function sendTextMessage() {
  const content = newMessage.value.trim()
  if (!content) return
  const now = new Date()
  const msg: ChatMessage = {
    id: Date.now(),
    fromSelf: true,
    author: auth.nickname || '我',
    time: now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }),
    content,
    status: 'sent',
    type: 'text',
  }
  messages.value = [...messages.value, msg]
  newMessage.value = ''
}

// 初始化高德地图：景点映射为真实坐标，每日行程为一条时间轴折线
async function initMap() {
  const days = plan.value?.days || []
  const container = mapRef.value
  if (!container || !days.length) return

  const myRunId = ++mapRunId

  try {
    await loadAmapScript()
    if (myRunId !== mapRunId) return

    const waitForSize = async (el: HTMLElement, maxFrames = 30) => {
      for (let i = 0; i < maxFrames; i++) {
        const rect = el.getBoundingClientRect()
        if (rect.width > 0 && rect.height > 0) return
        await new Promise<void>((r) => requestAnimationFrame(() => r()))
      }
    }
    await waitForSize(container)
    if (myRunId !== mapRunId) return

    // 控制台：当前选择与行程结构
    const selectedDayRaw = activeMapDay.value
    const selectedDay = Number(selectedDayRaw) || 0
    console.log('[地图折线] initMap 开始', {
      selectedDayRaw,
      selectedDay,
      daysCount: days.length,
      daysSummary: days.map((d) => ({
        dayIndex: d.dayIndex,
        dayIndexType: typeof d.dayIndex,
        activitiesCount: d.activities?.length ?? 0,
        activityNames: (d.activities || []).map((a) => a.name || a.location || '?').slice(0, 3),
      })),
    })

    if (amapInstance) {
      try {
        if (mapMarkers.length) amapInstance.remove(mapMarkers)
        if (mapPolylines.length) amapInstance.remove(mapPolylines)
        amapInstance.destroy()
      } catch (e) {
        console.warn('[地图折线] 清理旧地图时异常', e)
      }
      amapInstance = null
      mapMarkers.length = 0
      mapPolylines.length = 0
    }

    // 坐标映射：优先用后端返回的每项活动经纬度（key=act-dayIndex-idx），缺省时用地点名地理编码（key=location|name）
    const coordsMap = new Map<string, [number, number]>()
    const needGeocodeSet = new Set<string>()
    const dest = (plan.value?.destination || post.value?.destination || '').trim()

    days.forEach((day) => {
      day.activities?.forEach((act, idx) => {
        const nameKey = (act.location || act.name || '').trim()
        const lng = (act as any).lng
        const lat = (act as any).lat
        const hasCoord = typeof lng === 'number' && typeof lat === 'number' && !Number.isNaN(lng) && !Number.isNaN(lat)
        if (hasCoord) {
          coordsMap.set(`act-${day.dayIndex}-${idx}`, [lng, lat])
        } else if (nameKey) {
          needGeocodeSet.add(nameKey)
        }
      })
    })

    for (const addr of needGeocodeSet) {
      if (coordsMap.has(addr)) continue
      const query = dest ? `${addr} ${dest}` : addr
      const coords = await geocode(query)
      if (coords) coordsMap.set(addr, coords)
      await new Promise<void>((r) => setTimeout(r, 100))
    }
    if (myRunId !== mapRunId) return

    const selectedDayForFilter = Number(activeMapDay.value) || 0
    let center: [number, number] = [116.397428, 39.90923]
    if (selectedDayForFilter !== 0) {
      const selDay = days.find((d) => Number(d.dayIndex) === selectedDayForFilter)
      const firstAct = selDay?.activities?.[0]
      if (firstAct) {
        const nameKey = (firstAct.location || firstAct.name || '').trim()
        const c = coordsMap.get(`act-${selDay.dayIndex}-0`) ?? (nameKey ? coordsMap.get(nameKey) : undefined)
        if (c) center = c
      }
    }
    if (center[0] === 116.397428 && center[1] === 39.90923) {
      const firstCoords = Array.from(coordsMap.values())[0]
      if (firstCoords) center = firstCoords
    }

    amapInstance = initAmapMap(container, center, 12, {
      viewMode: '2D',
      mapStyle: 'amap://styles/normal',
      forceTileLayer: true,
    })

    const palette = ['#22c55e', '#06b6d4', '#6366f1', '#f59e0b', '#ef4444', '#a855f7']

    days.forEach((day, dayIdx) => {
      const dayIndexNum = Number(day.dayIndex)
      const skip = selectedDayForFilter !== 0 && dayIndexNum !== selectedDayForFilter
      if (skip) {
        console.log('[地图折线] 跳过天', { dayIndex: day.dayIndex, dayIndexNum, selectedDayForFilter })
        return
      }
      const dayActivities = day.activities || []
      console.log('[地图折线] 绘制天', {
        dayIndex: day.dayIndex,
        dayIndexNum,
        selectedDayForFilter,
        activitiesCount: dayActivities.length,
        activityNames: dayActivities.map((a) => a.name || a.location || '?'),
      })
      const dayPath: [number, number][] = []
      const coordRepeatMap = new Map<string, number>()

      dayActivities.forEach((act, idx) => {
        const nameKey = (act.location || act.name || '').trim()
        const coords = coordsMap.get(`act-${day.dayIndex}-${idx}`) ?? (nameKey ? coordsMap.get(nameKey) : undefined)
        if (!coords) return

        const repeatKey = `${coords[0].toFixed(6)}_${coords[1].toFixed(6)}`
        const usedCount = (coordRepeatMap.get(repeatKey) ?? 0) + 1
        coordRepeatMap.set(repeatKey, usedCount)

        let finalCoords: [number, number] = coords
        if (usedCount > 1) {
          const angle = ((usedCount - 1) * 2 * Math.PI) / 6
          const radius = 0.002
          finalCoords = [coords[0] + radius * Math.cos(angle), coords[1] + radius * Math.sin(angle)]
        }
        dayPath.push(finalCoords)

        const color = palette[dayIdx % palette.length]
        const orderLabel = `第${day.dayIndex}天·${idx + 1}`
        const marker = addMarker(
          amapInstance,
          finalCoords,
          act.name || '景点',
          [
            `<b>${act.name || '景点'}</b>`,
            `第 ${day.dayIndex} 天 · 第 ${idx + 1} 站`,
            act.startTime || act.endTime ? `时间：${act.startTime || ''} – ${act.endTime || ''}` : '',
            act.location ? `位置：${act.location}` : '',
            act.transport ? `交通：${act.transport}` : '',
            act.estimatedCost != null ? `费用：约 ${act.estimatedCost} 元` : '',
          ]
            .filter(Boolean)
            .join('<br/>')
        )

        marker.setIcon(
          new window.AMap.Icon({
            size: new window.AMap.Size(16, 16),
            image: `data:image/svg+xml;base64,${btoa(
              `<svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 16 16">
                <circle cx="8" cy="8" r="7" fill="${color}" stroke="#ffffff" stroke-width="2" />
              </svg>`
            )}`,
            imageOffset: new window.AMap.Pixel(-8, -8),
          })
        )
        marker.setLabel({
          direction: 'top',
          offset: new window.AMap.Pixel(0, -18),
          content: `<div style="
            padding: 2px 6px;
            border-radius: 999px;
            background: rgba(15,23,42,0.9);
            color: #f9fafb;
            font-size: 11px;
            line-height: 1;
            white-space: nowrap;
          ">${orderLabel}</div>`,
        })
        mapMarkers.push(marker)
      })

      if (dayPath.length > 1) {
        const color = palette[dayIdx % palette.length]
        const polyline = addPolyline(amapInstance, dayPath, color)
        mapPolylines.push(polyline)
        console.log('[地图折线] 已添加折线', {
          dayIndex: day.dayIndex,
          dayPathLength: dayPath.length,
          color,
          firstCoord: dayPath[0],
          lastCoord: dayPath[dayPath.length - 1],
          bounds: [Math.min(...dayPath.map((p) => p[0])), Math.min(...dayPath.map((p) => p[1])), Math.max(...dayPath.map((p) => p[0])), Math.max(...dayPath.map((p) => p[1]))],
        })
      } else {
        console.log('[地图折线] 该天点位不足，未画折线', { dayIndex: day.dayIndex, dayPathLength: dayPath.length })
      }
    })

    console.log('[地图折线] initMap 结束', { markersCount: mapMarkers.length, polylinesCount: mapPolylines.length })

    amapInstance.on('complete', () => {
      try {
        if (mapMarkers.length > 0) amapInstance.setFitView(mapMarkers)
        amapInstance.resize()
      } catch {
        // ignore
      }
    })
  } catch (error) {
    console.error('地图初始化失败:', error)
    ElMessage.warning('地图加载失败，请检查高德地图API配置')
  }
}

function resizeMap() {
  try {
    amapInstance?.resize?.()
  } catch {
    // ignore
  }
}

watch(
  postId,
  () => {
    fetchPost()
  },
  { immediate: false }
)

watch(activeTab, (tab) => {
  if (tab === 'map' && plan.value?.days?.length) {
    nextTick().then(() => initMap())
  }
})

watch(activeMapDay, () => {
  if (activeTab.value === 'map' && plan.value?.days?.length) {
    nextTick().then(() => initMap())
  }
})

onMounted(async () => {
  loading.value = true
  await fetchPost()
  await fetchRecommended()
  await fetchChatHistory()
  loading.value = false
  if (plan.value?.days?.length) activeAccordion.value = `day-${plan.value.days[0].dayIndex}`

  if (activeTab.value === 'map' && plan.value?.days?.length) {
    await nextTick()
    await initMap()
  }
  window.addEventListener('resize', resizeMap)
})

onBeforeUnmount(() => {
  try {
    window.removeEventListener('resize', resizeMap)
    if (amapInstance) {
      try {
        if (mapMarkers.length) amapInstance.remove(mapMarkers)
        if (mapPolylines.length) amapInstance.remove(mapPolylines)
      } catch {
        // ignore
      }
      amapInstance.destroy()
      amapInstance = null
    }
    mapMarkers.length = 0
    mapPolylines.length = 0
  } catch (e) {
    console.warn('CompanionDetailView onBeforeUnmount:', e)
  }
})
</script>

<template>
  <div class="detail-page">
    <!-- 返回按钮 -->
    <div class="back-button-container">
      <ElButton :icon="ArrowLeft" circle @click="goBack" class="back-button" />
    </div>

    <template v-if="loading">
      <div class="loading-wrap">
        <p class="text-subtle">加载中…</p>
      </div>
    </template>

    <template v-else-if="post">
      <!-- 1. 活动基础信息 -->
      <section class="top-card">
        <div class="top-inner">
          <div class="top-main">
            <div class="user-row">
              <div class="avatar-wrap">
                <img v-if="displayCreatorAvatar" :src="displayCreatorAvatar" :alt="displayCreatorName" class="avatar-img" />
                <span v-else class="avatar-placeholder">{{ displayCreatorName.charAt(0) }}</span>
              </div>
              <div class="user-info">
                <div class="user-name-row">
                  <span class="nickname">{{ displayCreatorName }}</span>
                  <ElTag size="small" type="warning" effect="plain" class="reputation-badge">
                    {{ reputationLabel(displayCreatorReputation as any) }}
                  </ElTag>
                  <span class="verified-icon" title="已认证">✓</span>
                </div>
                <p class="user-sub">
                  发起目的地：<strong>{{ post.destination }}</strong>
                  <span v-if="post.startDate && post.endDate">
                    ｜ 出发时间：{{ post.startDate }} – {{ post.endDate }}
                  </span>
                </p>
              </div>
            </div>

            <div class="trip-brief">
              <h1 class="dest-title">{{ post.destination }} 结伴 · 共赴旅程</h1>
              <p class="date-line">
                {{ post.startDate }} – {{ post.endDate }}
                <span class="days-badge">{{ tripDays() }} 天</span>
              </p>
              <div class="meta-row">
                <div class="people-meta">
                  <span class="meta-label">人数</span>
                  <span class="meta-value">
                    {{ teamMembers.length || 0 }}/{{ post.maxPeople || '待定' }}
                  </span>
                </div>
                <div class="style-meta">
                  <span class="meta-label">旅行风格</span>
                  <div class="tag-row">
                    <template v-if="creatorTagList.length">
                      <ElTag v-for="t in creatorTagList" :key="t" size="small" type="success" effect="plain">{{ t }}</ElTag>
                    </template>
                    <ElTag v-else size="small" type="info" effect="plain">自由行</ElTag>
                  </div>
                </div>
                <div class="budget-meta">
                  <span class="meta-label">预算</span>
                  <span class="meta-value">{{ budgetText }}</span>
                </div>
                <div class="status-meta">
                  <span class="meta-label">状态</span>
                  <ElTag :type="statusType(post.status)" size="small" effect="dark">
                    {{ statusLabel(post.status) }}
                  </ElTag>
                </div>
              </div>
            </div>
          </div>

          <div class="top-actions">
            <div class="top-actions-row">
              <ElButton
                type="primary"
                size="large"
                :loading="applyLoading"
                :disabled="isFull || isJoined"
                class="btn-apply"
                @click="applyCompanion"
              >
                {{ isJoined ? '已加入' : (isFull ? '活动已满' : '加入活动') }}
              </ElButton>
              <ElButton v-if="post.teamId" size="large" class="btn-outline" @click="router.push(`/teams/${post.teamId}`)">
                查看小队
              </ElButton>
            </div>
            <ElButton v-if="isJoined && !isLeader" size="large" class="btn-outline" @click="quitCompanion">
              退出活动
            </ElButton>
            <div class="top-mini-actions">
              <ElButton text :icon="Star" @click="toggleFavorite">收藏</ElButton>
              <ElButton text :icon="Share" @click="shareCompanion">分享</ElButton>
            </div>
          </div>
        </div>

        <p v-if="post.expectedMateDesc" class="mate-desc">
          活动说明：{{ post.expectedMateDesc }}
        </p>
      </section>

      <!-- 2/3/5 组合：行程 / 地图 / 聊天区域 -->
      <div class="content-layout">
        <main class="main-col">
          <section class="section">
            <ElTabs v-model="activeTab" class="main-tabs">
              <ElTabPane label="行程概览" name="itinerary">
                <section v-if="plan?.days?.length" class="trip-section">
                  <h2 class="section-title">每日行程</h2>
                  <ElCollapse v-model="activeAccordion" class="trip-accordion">
                    <ElCollapseItem
                      v-for="day in plan.days"
                      :key="day.dayIndex"
                      :name="`day-${day.dayIndex}`"
                      class="day-item"
                    >
                      <template #title>
                        <span class="day-title">第 {{ day.dayIndex }} 天</span>
                        <span class="day-date">{{ day.date }}</span>
                      </template>
                      <div class="day-content">
                        <article
                          v-for="(act, idx) in day.activities"
                          :key="idx"
                          class="activity-card"
                        >
                          <div class="act-image">
                            <img
                              class="act-image-img"
                              :src="activityImageUrl(act, day.dayIndex, idx)"
                              :alt="act.name || '景点图片'"
                              loading="lazy"
                              @error="(e) => ((e.target as HTMLImageElement).src = 'https://picsum.photos/seed/spot-fallback/260/180')"
                            />
                          </div>
                          <div class="act-body">
                            <h4 class="act-name">{{ act.name || '行程点' }}</h4>
                            <p class="act-time">
                              {{ act.startTime || '--' }} – {{ act.endTime || '--' }}
                              <span class="act-stay">停留</span>
                            </p>
                            <div class="act-tags">
                              <span v-for="tag in activityTags(act)" :key="tag" class="act-tag">{{ tag }}</span>
                            </div>
                            <div class="act-desc">
                              <span v-if="act.location">{{ act.location }}</span>
                              <span v-if="act.transport"> · {{ act.transport }}</span>
                              <span v-if="act.estimatedCost"> · 约 {{ act.estimatedCost }} 元</span>
                            </div>
                          </div>
                        </article>
                      </div>
                    </ElCollapseItem>
                  </ElCollapse>
                </section>
                <section v-else class="trip-section">
                  <h2 class="section-title">每日行程</h2>
                  <div class="empty-trip text-subtle">
                    暂无关联行程，加入活动后可与发布者协作编辑路线。
                  </div>
                </section>
              </ElTabPane>

              <ElTabPane label="地图路线" name="map">
                <section class="map-section">
                  <h2 class="section-title">可视化路线</h2>
                  <div class="map-day-options">
                    <span class="map-day-label">选择行程：</span>
                    <ElRadioGroup v-model.number="activeMapDay" class="map-day-radios">
                      <ElRadioButton :value="0">全部</ElRadioButton>
                      <ElRadioButton
                        v-for="d in (plan?.days || [])"
                        :key="d.dayIndex"
                        :value="d.dayIndex"
                      >
                        第{{ d.dayIndex }}天
                      </ElRadioButton>
                    </ElRadioGroup>
                  </div>
                  <div class="map-card">
                    <div ref="mapRef" class="map-container" />
                    <p class="map-tip">
                      已接入高德地图：每个景点已映射为真实地图坐标；每日行程按时间顺序连成一条折线，不同日期使用不同颜色。
                    </p>
                  </div>
                </section>
              </ElTabPane>

              <ElTabPane label="内置沟通" name="chat">
                <section class="chat-section">
                  <h2 class="section-title">站内安全聊天</h2>
                  <div class="chat-grid">
                    <div class="chat-panel">
                      <div class="chat-messages" :class="{ 'is-loading': chatLoading }">
                        <div v-if="chatLoading" class="chat-loading text-subtle">加载聊天记录...</div>
                        <template v-else>
                          <div
                            v-for="m in messages"
                            :key="m.id"
                            class="chat-item"
                            :class="{ self: m.fromSelf }"
                          >
                            <div class="chat-meta">
                              <span class="chat-author">{{ m.fromSelf ? '我' : m.author }}</span>
                              <span class="chat-time">{{ m.time }}</span>
                            </div>
                            <div class="chat-bubble">
                              <span>{{ m.content }}</span>
                            </div>
                          </div>
                        </template>
                      </div>
                      <div class="chat-input-bar">
                        <ElInput
                          v-model="newMessage"
                          type="textarea"
                          :rows="2"
                          resize="none"
                          placeholder="和旅友安全沟通行程、预算与分工…"
                          class="chat-input"
                          @keyup.enter.exact.prevent="sendTextMessage"
                        />
                        <div class="chat-actions">
                          <ElButton type="primary" size="small" @click="sendTextMessage">
                            发送
                          </ElButton>
                        </div>
                      </div>
                    </div>

                    <aside class="notify-panel">
                      <h3 class="notify-title">行程协作通知</h3>
                      <ul class="notify-list">
                        <li
                          v-for="(item, idx) in collaborationEvents"
                          :key="idx"
                          class="notify-item"
                        >
                          {{ item }}
                        </li>
                      </ul>
                    </aside>
                  </div>
                </section>
              </ElTabPane>
            </ElTabs>
          </section>

          <!-- 底部安全提示 + 推荐 -->
          <section class="section safety-section">
            <h2 class="section-title">活动安全与注意事项</h2>
            <div class="safety-card">
              <ul class="safety-list">
                <li>请勿在未见面确认前泄露身份证、银行卡等敏感信息。</li>
                <li>建议通过平台官方私信/聊天功能沟通，便于留存记录与申诉。</li>
                <li>线下见面请选择公共场所，并告知亲友行程与对方信息。</li>
                <li>如遇可疑行为或诈骗，请及时举报并联系平台客服。</li>
              </ul>
            </div>
          </section>

          <section v-if="recommended.length" class="section recommend-section">
            <h2 class="section-title">热门推荐结伴</h2>
            <div class="recommend-scroll">
              <article
                v-for="item in recommended"
                :key="item.id"
                class="recommend-card"
                @click="goRecommend(item)"
              >
                <div class="rec-dest">{{ item.destination }}</div>
                <p class="rec-date">{{ item.startDate }} – {{ item.endDate }}</p>
                <p class="rec-creator text-subtle">发起：{{ item.creatorNickname || '旅友' }}</p>
              </article>
            </div>
          </section>
        </main>

        <!-- 4. 旅友信息与活动操作区 -->
        <aside class="aside-col">
          <section class="section members-section">
            <h2 class="section-title">已加入旅友</h2>
            <div v-if="teamMembers.length" class="members-card">
              <div
                v-for="m in teamMembers"
                :key="m.userId"
                class="member-row"
                @click="openMemberDetail(m)"
              >
                <ElAvatar
                  :size="40"
                  :src="m.avatar || undefined"
                  class="member-avatar member-avatar-clickable"
                  @click.stop="router.push(`/users/${m.userId}`)"
                >
                  {{ memberDisplayName(m).charAt(0).toUpperCase() }}
                </ElAvatar>
                <div class="member-info">
                  <span class="member-name">{{ memberDisplayName(m) }}</span>
                  <ElTag v-if="m.role === 'leader'" size="small" type="danger" effect="plain">队长</ElTag>
                  <span v-else class="member-role">成员</span>
                </div>
                <ElButton
                  type="primary"
                  size="small"
                  :icon="ChatDotRound"
                  class="member-chat-btn"
                  @click.stop="router.push({ path: `/chat/${m.userId}`, query: { nickname: memberDisplayName(m) } })"
                >
                  私信
                </ElButton>
              </div>
            </div>
            <div v-else class="members-empty text-subtle">
              申请结伴并成行后，将在此展示小队成员。点击成员可查看详情与信誉信息。
            </div>
          </section>

          <section class="section ops-section">
            <h2 class="section-title">活动操作</h2>
            <div class="ops-card">
              <ElButton type="primary" text>编辑行程（管理员）</ElButton>
              <ElButton type="primary" text>生成社区分享帖子</ElButton>
              <ElButton type="primary" text>查看历史活动评价</ElButton>
            </div>
          </section>
        </aside>
      </div>

      <!-- 旅友详情抽屉 -->
      <ElDrawer
        v-model="memberDrawerVisible"
        title="旅友详情"
        direction="rtl"
        size="360px"
      >
        <div v-if="selectedMember" class="drawer-member">
          <ElAvatar :size="56" :src="selectedMember.avatar || undefined" class="drawer-avatar">
            {{ memberDisplayName(selectedMember).charAt(0).toUpperCase() }}
          </ElAvatar>
          <h3 class="drawer-name">{{ memberDisplayName(selectedMember) }}</h3>
          <p class="drawer-role">{{ selectedMember.role === 'leader' ? '队长' : '成员' }}</p>
          <p class="drawer-state text-subtle">状态：{{ stateLabel(selectedMember.state) }}</p>
        </div>
      </ElDrawer>
    </template>

    <div v-else class="empty-wrap">
      <p class="text-subtle">未找到该结伴信息</p>
      <ElButton @click="router.push('/companion')">返回结伴广场</ElButton>
    </div>
  </div>
</template>

<style scoped>
.detail-page {
  min-height: 100vh;
  background: radial-gradient(circle at top left, #e0f2fe 0, transparent 55%),
    radial-gradient(circle at bottom right, #fef3c7 0, transparent 55%),
    #f9fafb;
  padding: 24px 16px 40px;
}

.back-button-container {
  position: fixed;
  top: 80px;
  left: 20px;
  z-index: 100;
}

.back-button {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(226, 232, 240, 0.8);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.15);
  transition: all 0.2s ease;
}

.back-button:hover {
  background: rgba(255, 255, 255, 1);
  transform: translateX(-2px);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.2);
}

.loading-wrap,
.empty-wrap {
  max-width: 960px;
  margin: 0 auto;
  padding: 48px 24px;
  text-align: center;
}

/* ----- 1. 顶部信息区 ----- */
.top-card {
  max-width: 1100px;
  margin: 0 auto 24px;
  background: #ffffff;
  border-radius: 24px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.15);
  padding: 24px 26px 18px;
}

.top-inner {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
}

.top-main {
  flex: 1;
  min-width: 0;
}

.user-row {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 14px;
}

.avatar-wrap {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, #5eead4, #0d9488);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 22px;
  font-weight: 600;
}

.user-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.nickname {
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

.reputation-badge {
  font-size: 12px;
}

.verified-icon {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #0d9488;
  color: #fff;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
}

.user-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #64748b;
}

.trip-brief {
  margin-bottom: 4px;
}

.dest-title {
  margin: 0;
  font-size: 24px;
  font-weight: 750;
  color: #0f172a;
}

.date-line {
  margin: 6px 0 0;
  font-size: 14px;
  color: #64748b;
}

.days-badge {
  margin-left: 10px;
  padding: 2px 10px;
  border-radius: 20px;
  background: #ccfbf1;
  color: #0d9488;
  font-weight: 500;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin-top: 10px;
}

.meta-label {
  display: block;
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.meta-value {
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.top-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}

.top-actions-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 10px;
}

.top-mini-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: flex-end;
  width: 100%;
}

.btn-apply {
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}
.btn-apply:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 30px rgba(13, 148, 136, 0.45);
}

.btn-outline {
  border-radius: 999px;
}

.mate-desc {
  margin: 10px 4px 0;
  font-size: 13px;
  color: #475569;
}

/* ----- 主体布局：行程 / 地图 / 聊天 + 旅友侧栏 ----- */
.content-layout {
  max-width: 1100px;
  margin: 18px auto 0;
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(260px, 0.9fr);
  gap: 18px;
}

@media (max-width: 960px) {
  .content-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .top-inner {
    flex-direction: column;
    align-items: flex-start;
  }

  .top-actions {
    flex-direction: row;
    align-items: center;
  }

  .back-button-container {
    top: 70px;
    left: 12px;
  }

  .back-button {
    width: 36px;
    height: 36px;
  }
}

.main-col {
  min-width: 0;
}

.section {
  margin-bottom: 22px;
}

.section-title {
  margin: 0 0 14px;
  font-size: 17px;
  font-weight: 600;
  color: #1e293b;
}

/* ----- Tabs ----- */
.main-tabs :deep(.el-tabs__header) {
  margin-bottom: 12px;
}

.main-tabs :deep(.el-tabs__item.is-active) {
  color: #0d9488;
}

.main-tabs :deep(.el-tabs__active-bar) {
  background-color: #0d9488;
}

/* ----- 行程 Accordion ----- */
.trip-accordion {
  border: none;
}
.trip-accordion :deep(.el-collapse-item__header) {
  background: #fff;
  border-radius: 16px;
  padding: 14px 18px;
  margin-bottom: 10px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.1);
  border: none;
  transition: box-shadow 0.2s ease, transform 0.18s ease;
}
.trip-accordion :deep(.el-collapse-item__header:hover) {
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.18);
  transform: translateY(-1px);
}
.trip-accordion :deep(.el-collapse-item__wrap) {
  border: none;
  margin-bottom: 10px;
}
.trip-accordion :deep(.el-collapse-item__content) {
  padding: 0 0 12px;
}

.day-title {
  font-weight: 600;
  color: #1e293b;
}

.day-date {
  margin-left: 12px;
  font-size: 14px;
  color: #64748b;
}

.day-content {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 4px 4px 0;
}

.activity-card {
  display: flex;
  gap: 16px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.1);
  overflow: hidden;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.activity-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.18);
}

.act-image {
  width: 120px;
  flex-shrink: 0;
}

.act-image-img {
  width: 100%;
  height: 100%;
  min-height: 90px;
  display: block;
  object-fit: cover;
  border-radius: 16px;
}

.act-body {
  flex: 1;
  padding: 12px 14px 12px 0;
  min-width: 0;
}

.act-name {
  margin: 0 0 6px;
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.act-time {
  margin: 0 0 8px;
  font-size: 13px;
  color: #64748b;
}

.act-stay {
  margin-left: 6px;
  color: #0d9488;
  font-size: 12px;
}

.act-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 6px;
}

.act-tag {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  background: #f0fdfa;
  color: #0d9488;
}

.act-desc {
  font-size: 13px;
  color: #64748b;
}

.empty-trip {
  padding: 24px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.1);
}

/* ----- 地图区域 ----- */
.map-day-options {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.map-day-label {
  font-size: 14px;
  color: #475569;
  font-weight: 500;
}

.map-day-radios {
  flex-wrap: wrap;
}

.map-section .map-card {
  background: #020617;
  border-radius: 18px;
  padding: 14px 14px 10px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.6);
}

.map-container {
  width: 100%;
  height: 260px;
  border-radius: 12px;
}

.map-tip {
  margin: 8px 2px 0;
  font-size: 12px;
  color: #e2e8f0;
}

/* ----- 聊天区 ----- */
.chat-grid {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(200px, 1.1fr);
  gap: 16px;
}

@media (max-width: 900px) {
  .chat-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.chat-panel {
  background: #ffffff;
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12);
  padding: 12px 12px 10px;
  display: flex;
  flex-direction: column;
  height: 320px;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding-right: 4px;
}

.chat-loading {
  padding: 20px 0;
  text-align: center;
  font-size: 13px;
}

.chat-item {
  margin-bottom: 10px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.chat-item.self {
  align-items: flex-end;
}

.chat-meta {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 2px;
}

.chat-bubble {
  max-width: 80%;
  padding: 6px 10px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.5;
  background: #f1f5f9;
  color: #0f172a;
}

.chat-item.self .chat-bubble {
  background: #0f172a;
  color: #f9fafb;
}

.chat-input-bar {
  margin-top: 8px;
}

.chat-input :deep(textarea) {
  border-radius: 10px;
}

.chat-actions {
  margin-top: 6px;
  display: flex;
  justify-content: flex-end;
}

.notify-panel {
  background: #f8fafc;
  border-radius: 16px;
  padding: 12px 14px;
  border: 1px dashed #cbd5e1;
}

.notify-title {
  margin: 0 0 8px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.notify-list {
  margin: 0;
  padding-left: 16px;
  font-size: 13px;
  color: #475569;
  line-height: 1.6;
}

.notify-item + .notify-item {
  margin-top: 4px;
}

/* ----- 旅友信息与操作区 ----- */
.members-section {
  position: sticky;
  top: 12px;
}

.members-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12);
  padding: 14px;
}

.member-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid #e2e8f0;
  cursor: pointer;
  transition: background 0.18s ease, transform 0.18s ease;
}

.member-row:last-child {
  border-bottom: none;
}

.member-row:hover {
  background: #f8fafc;
  border-radius: 10px;
  margin: 0 -6px;
  padding-left: 6px;
  padding-right: 6px;
  transform: translateY(-1px);
}

.member-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
}

.member-avatar-clickable {
  cursor: pointer;
}

.member-chat-btn {
  margin-left: auto;
  flex-shrink: 0;
}

.member-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.member-name {
  font-weight: 500;
  color: #1e293b;
}

.member-role {
  font-size: 12px;
  color: #64748b;
}

.members-empty {
  padding: 16px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12);
  font-size: 14px;
  line-height: 1.6;
}

.ops-section .ops-card {
  background: #0f172a;
  color: #e5e7eb;
  border-radius: 16px;
  padding: 14px 14px 10px;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.7);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.ops-section :deep(.el-button.is-text) {
  justify-content: flex-start;
  padding-left: 0;
  color: #e5e7eb;
}

/* ----- 安全提示与推荐 ----- */
.safety-card {
  background: linear-gradient(135deg, #fffbeb 0%, #fef3c7 100%);
  border-radius: 16px;
  padding: 20px 24px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.08);
}

.safety-list {
  margin: 0;
  padding-left: 20px;
  color: #92400e;
  font-size: 14px;
  line-height: 1.8;
}

.recommend-scroll {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 8px;
}

.recommend-scroll::-webkit-scrollbar {
  height: 6px;
}

.recommend-scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

.recommend-card {
  flex: 0 0 200px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(15, 23, 42, 0.12);
  padding: 18px;
  cursor: pointer;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.recommend-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.18);
}

.rec-dest {
  font-weight: 600;
  font-size: 16px;
  color: #1e293b;
}

.rec-date {
  margin: 6px 0 2px;
  font-size: 13px;
  color: #64748b;
}

.rec-creator {
  font-size: 12px;
}

/* ----- 抽屉 ----- */
.drawer-member {
  text-align: center;
  padding: 20px 0;
}

.drawer-avatar {
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 600;
}

.drawer-name {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

.drawer-role {
  margin: 0 0 8px;
  font-size: 14px;
  color: #64748b;
}
</style>
