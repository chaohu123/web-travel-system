<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { routesApi, commentsApi, interactionsApi } from '../api'
import { useAuthStore } from '../store'
import type { CommentItem, PlanResponse, TripPlanDay, TripPlanActivity } from '../api'
import { loadAmapScript, initAmapMap, addMarker, addPolyline, geocode } from '../utils/amap'
import { formatDateTime } from '../utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// 返回上一页
function goBack() {
  if (window.history.length > 1) {
    router.go(-1)
  } else {
    router.push('/')
  }
}

const loading = ref(true)
const plan = ref<PlanResponse | null>(null)


// 操作状态：收藏 / 点赞
const isFavorited = ref(false)
const isLiked = ref(false)
const likeCount = ref(0)
const actionLoading = ref(false)

// 行程折叠 & 移动端 tabs
const activeAccordion = ref<string | number>('')
const activeTab = ref<'itinerary' | 'map'>('itinerary')

// 评价
const commentsLoading = ref(false)
const comments = ref<CommentItem[]>([])
const mockUseCount = ref(128) // 展示用
const avgRate = computed(() => {
  const scored = comments.value.filter((c) => typeof c.score === 'number') as Array<CommentItem & { score: number }>
  if (!scored.length) return 4.6
  const sum = scored.reduce((s, c) => s + (c.score ?? 0), 0)
  return Math.round((sum / scored.length) * 10) / 10
})

// 推荐路线（没有 list 接口，先用前端 mock；后续可替换为后端推荐接口）
const similarRoutes = computed(() => {
  const base = plan.value
  if (!base) return []
  const days = calcDays(base.startDate, base.endDate)
  return [
    { id: base.id + 101, title: `${base.destination} 轻松慢游 ${Math.max(3, days)} 日`, cover: `https://picsum.photos/seed/sim${base.id}a/520/320`, days: Math.max(3, days), budget: (base.budget ?? 6000) + 1200 },
    { id: base.id + 102, title: `${base.destination} 美食打卡 ${Math.max(2, days - 1)} 日`, cover: `https://picsum.photos/seed/sim${base.id}b/520/320`, days: Math.max(2, days - 1), budget: (base.budget ?? 6000) - 800 },
    { id: base.id + 103, title: `${base.destination} 文化深度游 ${Math.max(4, days + 1)} 日`, cover: `https://picsum.photos/seed/sim${base.id}c/520/320`, days: Math.max(4, days + 1), budget: (base.budget ?? 6000) + 2000 },
  ]
})

const planId = computed(() => {
  const id = route.params.id
  return id && !Array.isArray(id) ? Number(id) : 0
})

function calcDays(start: string, end: string): number {
  if (!start || !end) return 0
  const a = new Date(start).getTime()
  const b = new Date(end).getTime()
  return Math.max(1, Math.round((b - a) / (24 * 3600 * 1000)) + 1)
}

function paceLabel(pace?: string) {
  if (!pace) return '适中'
  if (pace === 'fast') return '暴走'
  if (pace === 'slow') return '悠闲'
  return '适中'
}

function styleTagsFromDays(days?: TripPlanDay[]): string[] {
  if (!days?.length) return ['自由行']
  const flat: TripPlanActivity[] = []
  days.forEach((d) => (d.activities || []).forEach((a) => flat.push(a)))
  const tags = new Set<string>()
  flat.forEach((a) => {
    const t = (a.type || '').toLowerCase()
    if (t === 'sight') tags.add('文化')
    else if (t === 'food') tags.add('美食')
    else if (t === 'hotel') tags.add('休闲')
    else if (t) tags.add('小众')
  })
  if (tags.size === 0) tags.add('自由行')
  return [...tags].slice(0, 4)
}

const totalDays = computed(() => (plan.value ? calcDays(plan.value.startDate, plan.value.endDate) : 0))
const poiCount = computed(() => {
  const days = plan.value?.days || []
  return days.reduce((sum, d) => sum + (d.activities?.length || 0), 0)
})
const totalCost = computed(() => plan.value?.budget ?? 0)
const avgPlayHours = computed(() => {
  // 没有真实时长字段：按“每天 6 小时”做展示占位（可后续从后端补充）
  const d = totalDays.value || 0
  if (!d) return 0
  return Math.round((d * 6) * 10) / 10
})
const totalDistance = computed(() => {
  // 无距离字段：展示占位（可后续改为真实里程）
  const count = poiCount.value
  return Math.round(Math.max(8, count * 5.2))
})

async function fetchDetail() {
  if (!planId.value) return
  loading.value = true
  try {
    // 从后端获取真实路线详情（包含每天行程点及其经纬度）
    plan.value = await routesApi.getOne(planId.value)
    if (plan.value?.days?.length) {
      const firstDayIndex = plan.value.days[0].dayIndex
      // 默认高亮并展开第 1 天，使右侧地图只展示对应天的折线与点位
      activeAccordion.value = `day-${firstDayIndex}`
      activeDayHighlight.value = firstDayIndex
    }
    // 调试：输出后端返回的经纬度，便于联调
    if (import.meta.env.DEV) {
      // eslint-disable-next-line no-console
      console.debug('[RouteDetail] plan days from backend:', plan.value.days)
    }
  } catch (error) {
    // API调用失败，如果仍然没有数据，设置为null
    if (!plan.value) {
      plan.value = null
    }
  } finally {
    loading.value = false
  }
}

async function fetchComments() {
  if (!planId.value) return
  commentsLoading.value = true
  try {
    // targetType 约定：这里用 "route"
    comments.value = await commentsApi.list('route', planId.value)
  } catch {
    comments.value = []
  } finally {
    commentsLoading.value = false
  }
}

async function fetchInteractions() {
  if (!planId.value) return
  try {
    const summary = await interactionsApi.summary('route', planId.value)
    likeCount.value = summary.likeCount ?? 0
    isLiked.value = !!summary.likedByCurrentUser
    isFavorited.value = !!summary.favoritedByCurrentUser
  } catch {
    likeCount.value = 0
  }
}

function ensureLogin() {
  if (!auth.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}

async function toggleFavorite() {
  if (!ensureLogin()) return
  if (actionLoading.value) return
  actionLoading.value = true
  try {
    if (isFavorited.value) {
      await interactionsApi.unfavorite('route', planId.value)
      isFavorited.value = false
      ElMessage.success('已取消收藏')
    } else {
      await interactionsApi.favorite('route', planId.value)
      isFavorited.value = true
      ElMessage.success('已收藏路线')
    }
  } finally {
    actionLoading.value = false
  }
}

async function toggleLike() {
  if (!ensureLogin()) return
  if (actionLoading.value) return
  actionLoading.value = true
  try {
    if (isLiked.value) {
      await interactionsApi.unlike('route', planId.value)
      isLiked.value = false
      likeCount.value = Math.max(0, likeCount.value - 1)
    } else {
      await interactionsApi.like('route', planId.value)
      isLiked.value = true
      likeCount.value += 1
    }
  } finally {
    actionLoading.value = false
  }
}

async function shareRoute() {
  // 简化：复制链接
  try {
    await navigator.clipboard.writeText(window.location.href)
    ElMessage.success('已复制分享链接')
  } catch {
    ElMessage.info('复制失败，请手动复制浏览器地址')
  }
}

function editRoute() {
  // 项目中没有“编辑路线详情”的页面，这里先跳到路线规划页复用（可带上 id 做预加载）
  router.push({ name: 'route-create', query: { from: String(planId.value) } })
}

function startCompanion() {
  const p = plan.value
  if (!p) return
  // 跳转到结伴发布页，并预填“关联行程”
  router.push({
    name: 'companion-create',
    query: {
      planId: String(p.id),
      destination: p.destination,
      startDate: p.startDate,
      endDate: p.endDate,
    },
  })
}

function goSimilar(id: number) {
  router.push(`/routes/${id}`)
}

function spotIdFromName(name?: string): number {
  const s = (name || '').trim()
  if (!s) return 0
  let hash = 0
  for (let i = 0; i < s.length; i++) {
    hash = (hash * 31 + s.charCodeAt(i)) >>> 0
  }
  return (hash % 9000) + 1000
}

function goSpot(act: TripPlanActivity) {
  const id = spotIdFromName(act.name)
  if (!id) return
  router.push({
    path: `/spots/${id}`,
    query: {
      name: act.name || '',
      location: act.location || '',
      fromRouteId: String(planId.value || ''),
    },
  })
}

// 地图：高德地图
const mapRef = ref<HTMLDivElement | null>(null)
let amapInstance: any = null
const markers: any[] = []
const polylines: any[] = []
// 当前高亮/展示的 day（点击 Day N 或悬停某行程时设置）
const activeDayHighlight = ref<number | null>(null)

// 初始化高德地图
async function initMap() {
  const days = plan.value?.days || []
  const container = mapRef.value
  if (!container || !days.length) return

  try {
    await loadAmapScript()

    // 等待地图容器真正有尺寸，避免 0x0 导致黑屏或异常
    const waitForSize = async (el: HTMLElement, maxFrames = 30) => {
      for (let i = 0; i < maxFrames; i++) {
        const rect = el.getBoundingClientRect()
        if (rect.width > 0 && rect.height > 0) return
        await new Promise<void>((r) => requestAnimationFrame(() => r()))
      }
    }
    await waitForSize(container)

    // 如有历史标记和折线，先从地图上移除，避免叠加
    if (amapInstance) {
      try {
        if (markers.length) {
          amapInstance.remove(markers)
        }
        if (polylines.length) {
          amapInstance.remove(polylines)
        }
      } catch {
        // ignore
      }
      markers.length = 0
      polylines.length = 0
    }

    // 构建“地址/名称 → 坐标”的映射：
    // 1. 优先使用后端返回的 lng/lat（真实坐标）
    // 2. 若某些点缺失 lng/lat，再退回到地理编码
    const coordsMap = new Map<string, [number, number]>()
    const needGeocode: string[] = []

    days.forEach((day) => {
      day.activities?.forEach((act) => {
        const key = (act.location || act.name || '').trim()
        if (!key || coordsMap.has(key)) return

        const lng = (act as any).lng
        const lat = (act as any).lat
        if (typeof lng === 'number' && typeof lat === 'number' && !Number.isNaN(lng) && !Number.isNaN(lat)) {
          coordsMap.set(key, [lng, lat])
        } else {
          needGeocode.push(key)
        }
      })
    })

    for (const addr of needGeocode) {
      if (coordsMap.has(addr)) continue
      const coords = await geocode(addr)
      if (coords) {
        coordsMap.set(addr, coords)
      }
      await new Promise<void>((resolve) => setTimeout(resolve, 100))
    }

    // 初始化地图，使用第一个有效坐标作为中心点
    let center: [number, number] = [116.397428, 39.90923] // 默认北京
    const firstCoords = Array.from(coordsMap.values())[0]
    if (firstCoords) {
      center = firstCoords
    }

    amapInstance = initAmapMap(container, center, 12, {
      viewMode: '2D',
      mapStyle: 'amap://styles/normal',
      forceTileLayer: true,
    })

    // 按天绘制路线
    const palette = ['#22c55e', '#06b6d4', '#6366f1', '#f59e0b', '#ef4444', '#a855f7']

    days.forEach((day, dayIdx) => {
      // 如果设置了高亮 day，只绘制该天的路线与点位
      if (activeDayHighlight.value != null && day.dayIndex !== activeDayHighlight.value) {
        return
      }
      const dayActivities = day.activities || []
      const dayPath: [number, number][] = []
      // 为避免同一经纬度的多个景点完全重叠，这里对相同坐标做轻微偏移（仅用于可视化）
      const coordRepeatMap = new Map<string, number>()

      dayActivities.forEach((act, idx) => {
        const key = (act.location || act.name || '').trim()
        if (!key) return
        const coords = coordsMap.get(key)
        if (!coords) return

        const repeatKey = `${coords[0].toFixed(6)}_${coords[1].toFixed(6)}`
        const usedCount = (coordRepeatMap.get(repeatKey) ?? 0) + 1
        coordRepeatMap.set(repeatKey, usedCount)

        // 第一个点使用原始坐标，其余点按圆形轻微散开（约数百米级别，不影响整体城市范围）
        let finalCoords: [number, number] = coords
        if (usedCount > 1) {
          const angle = ((usedCount - 1) * 2 * Math.PI) / 6
          const radius = 0.002 // 经纬度偏移，大约几百米
          finalCoords = [coords[0] + radius * Math.cos(angle), coords[1] + radius * Math.sin(angle)]
        }

        dayPath.push(finalCoords)

        // 添加标记点
        const color = palette[dayIdx % palette.length]
        const orderLabel = `D${idx + 1}`
        const marker = addMarker(
          amapInstance,
          finalCoords,
          act.name || '景点',
          [
            `<b>${act.name || '景点'}</b>`,
            `Day ${day.dayIndex}`,
            act.location ? `位置：${act.location}` : '',
            act.transport ? `交通：${act.transport}` : '',
            act.estimatedCost != null ? `费用：约 ${act.estimatedCost} 元` : '',
          ]
            .filter(Boolean)
            .join('<br/>')
        )

        // 设置标记样式（彩色圆点）
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

        // 在图标上方叠加 D1/D2/D3 文字标签，保证在任何缩放级别都清晰可见
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

        markers.push(marker)
      })

      // 绘制路线
      if (dayPath.length > 1) {
        const color = palette[dayIdx % palette.length]
        const polyline = addPolyline(amapInstance, dayPath, color)
        polylines.push(polyline)
      }
    })

    // 调整地图视野以包含所有标记
    amapInstance.on('complete', () => {
      try {
        if (markers.length > 0) {
          amapInstance.setFitView(markers)
        }
        amapInstance.resize()
      } catch {}
    })
  } catch (error) {
    console.error('初始化地图失败:', error)
    ElMessage.warning('地图加载失败，请检查高德地图API配置')
  }
}

function resizeMap() {
  // 调整地图尺寸以适配容器变化
  try {
    amapInstance?.resize?.()
  } catch {
    // ignore
  }
}

onMounted(async () => {
  await fetchDetail()
  await fetchComments()
  // 等待DOM更新后再初始化地图
  await nextTick()
  // 初始化地图（内部会处理高德地图API加载）
  try {
    await initMap()
  } catch (error) {
    console.warn('高德地图加载失败:', error)
    ElMessage.warning('地图加载失败，请检查高德地图API配置')
  }
  window.addEventListener('resize', resizeMap)
  await fetchInteractions()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeMap)
  // 清理高德地图实例
  if (amapInstance) {
    amapInstance.destroy()
    amapInstance = null
  }
  markers.length = 0
  polylines.length = 0
})
</script>

<template>
  <div class="route-detail-page">
    <!-- 返回按钮 -->
    <div class="back-button-container">
      <el-button :icon="ArrowLeft" circle @click="goBack" class="back-button" />
    </div>

    <div v-if="loading" class="loading-wrap text-subtle">加载中…</div>

    <template v-else-if="plan">
      <!-- 2. 路线基础信息区 -->
      <section class="hero-card">
        <div class="hero-grid">
          <div class="hero-media">
            <el-carousel height="260px" indicator-position="outside" class="hero-carousel">
              <el-carousel-item v-for="i in 3" :key="i">
                <div class="cover" :style="{ backgroundImage: `url(https://picsum.photos/seed/route${plan.id}-${i}/1200/520)` }" />
              </el-carousel-item>
            </el-carousel>
          </div>

          <div class="hero-info">
            <div class="title-row">
              <h1 class="route-title">{{ plan.title || `${plan.destination} 路线` }}</h1>
              <div class="tag-row">
                <el-tag v-for="t in styleTagsFromDays(plan.days)" :key="t" size="small" effect="plain">{{ t }}</el-tag>
                <el-tag size="small" type="info" effect="plain">{{ paceLabel(plan.pace) }}</el-tag>
              </div>
            </div>

            <div class="meta-line">
              <span class="meta-item">出发地 → <b>{{ plan.destination }}</b></span>
              <span class="dot">·</span>
              <span class="meta-item">{{ plan.startDate }} ~ {{ plan.endDate }}</span>
            </div>

            <div class="kpis">
              <div class="kpi">
                <div class="kpi-label">总天数</div>
                <div class="kpi-value">{{ totalDays }} 天</div>
              </div>
              <div class="kpi">
                <div class="kpi-label">总预算</div>
                <div class="kpi-value">{{ totalCost }} 元</div>
              </div>
              <div class="kpi">
                <div class="kpi-label">总里程</div>
                <div class="kpi-value">{{ totalDistance }} km</div>
              </div>
            </div>

            <div class="actions">
              <el-button :loading="actionLoading" :type="isLiked ? 'success' : 'primary'" @click="toggleLike">
                {{ isLiked ? '已点赞' : '点赞' }} · {{ likeCount }}
              </el-button>
              <el-button :loading="actionLoading" :type="isFavorited ? 'success' : 'primary'" @click="toggleFavorite">
                {{ isFavorited ? '已收藏' : '收藏路线' }}
              </el-button>
              <el-button @click="shareRoute">分享路线</el-button>
              <el-button type="warning" plain @click="editRoute">编辑路线</el-button>
              <el-button type="primary" plain @click="startCompanion">发起结伴</el-button>
            </div>
          </div>
        </div>
      </section>

      <!-- 3. 路线概览统计区 -->
      <section class="stats-grid">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon">🗓️</div>
          <div class="stat-main">
            <div class="stat-num">{{ totalDays }}</div>
            <div class="stat-text">行程天数</div>
          </div>
        </el-card>
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon">📍</div>
          <div class="stat-main">
            <div class="stat-num">{{ poiCount }}</div>
            <div class="stat-text">景点数量</div>
          </div>
        </el-card>
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon">💰</div>
          <div class="stat-main">
            <div class="stat-num">{{ totalCost }}</div>
            <div class="stat-text">预计总花费（元）</div>
          </div>
        </el-card>
        <el-card shadow="hover" class="stat-card">
          <div class="stat-icon">⏱️</div>
          <div class="stat-main">
            <div class="stat-num">{{ avgPlayHours }}</div>
            <div class="stat-text">日均游玩时长（h）</div>
          </div>
        </el-card>
      </section>

      <!-- 4/5：PC 双栏，移动 tabs -->
      <section class="main-layout">
        <div class="mobile-tabs">
          <el-tabs v-model="activeTab">
            <el-tab-pane label="行程" name="itinerary" />
            <el-tab-pane label="地图" name="map" />
          </el-tabs>
        </div>

        <!-- 行程详情 -->
        <div class="itinerary-col" :class="{ hiddenOnMobile: activeTab !== 'itinerary' }">
          <h2 class="block-title">每日行程</h2>
          <el-collapse v-if="plan.days?.length" v-model="activeAccordion" class="day-collapse">
            <el-collapse-item
              v-for="day in plan.days"
              :key="day.dayIndex"
              :name="`day-${day.dayIndex}`"
            >
              <template #title>
                <div
                  class="day-title"
                  @click.stop="
                    activeAccordion = `day-${day.dayIndex}`;
                    activeDayHighlight = day.dayIndex;
                    initMap();
                  "
                >
                  <span class="day-badge">Day {{ day.dayIndex }}</span>
                  <span class="day-date">{{ day.date }}</span>
                  <span class="day-meta">{{ day.activities?.length || 0 }} 个景点</span>
                </div>
              </template>

              <el-card shadow="never" class="day-card">
                <div class="day-sections">
                  <div class="day-section">
                    <div class="sec-title">景点列表</div>
                    <div class="poi-list">
                      <div
                        v-for="(act, idx) in day.activities"
                        :key="idx"
                        class="poi-row clickable"
                        @click="goSpot(act)"
                        @mouseenter="activeDayHighlight = day.dayIndex; initMap()"
                        @mouseleave="activeDayHighlight = null; initMap()"
                      >
                        <div class="poi-time">{{ act.startTime || '--:--' }} - {{ act.endTime || '--:--' }}</div>
                        <div class="poi-main">
                          <div class="poi-name">{{ act.name || '行程点' }}</div>
                          <div class="poi-sub">
                            <span v-if="act.location">位置：{{ act.location }}</span>
                            <span v-if="act.transport"> · 交通：{{ act.transport }}</span>
                            <span v-if="act.estimatedCost != null"> · 门票/费用：约 {{ act.estimatedCost }} 元</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>

                  <div class="day-section side">
                    <div class="sec-title">当日建议</div>
                    <ul class="tips">
                      <li>餐饮推荐：当地特色小吃 + 夜市/商圈</li>
                      <li>住宿区域：靠近核心景点/交通枢纽，减少通勤</li>
                      <li>交通方式：公共交通优先，必要时打车</li>
                    </ul>
                  </div>
                </div>
              </el-card>
            </el-collapse-item>
          </el-collapse>
          <el-empty v-else description="暂无行程内容" />
        </div>

        <!-- 地图可视化 -->
        <div class="map-col" :class="{ hiddenOnMobile: activeTab !== 'map' }">
          <h2 class="block-title">地图路线可视化</h2>
          <div class="map-card">
            <div ref="mapRef" class="map-canvas" />
            <div class="map-hint">
              提示：点击标记点可查看景点详细信息；地图使用高德地图API展示真实地理位置。
            </div>
          </div>
        </div>
      </section>

      <!-- 6. 用户评价与使用情况 -->
      <section class="reviews-section">
        <div class="reviews-head">
          <h2 class="block-title">用户评价与使用情况</h2>
          <div class="reviews-kpis">
            <span class="use-count">使用次数：{{ mockUseCount }}</span>
            <el-rate :model-value="avgRate" disabled allow-half />
            <span class="avg-rate">{{ avgRate }}</span>
          </div>
        </div>

        <el-card shadow="hover" class="reviews-card">
          <div v-if="commentsLoading" class="text-subtle">评价加载中...</div>
          <div v-else-if="comments.length === 0" class="text-subtle">暂无评价，成为第一个使用该路线的人吧～</div>
          <div v-else class="comment-list">
            <article v-for="c in comments.slice(0, 3)" :key="c.id" class="comment-item">
              <div class="comment-avatar">{{ (c.userName || 'U').charAt(0).toUpperCase() }}</div>
              <div class="comment-main">
                <div class="comment-top">
                  <span class="comment-name">{{ c.userName }}</span>
                  <el-rate v-if="c.score != null" :model-value="c.score" disabled allow-half size="small" />
                  <span class="comment-time">{{ formatDateTime(c.createdAt) }}</span>
                </div>
                <p class="comment-content">{{ c.content }}</p>
              </div>
            </article>
            <div class="more-row">
              <el-button text type="primary" @click="ElMessage.info('查看全部评价：可扩展为弹窗或单独页面')">
                查看全部评价
              </el-button>
            </div>
          </div>
        </el-card>
      </section>

      <!-- 7. 相似路线推荐 -->
      <section class="similar-section">
        <h2 class="block-title">相似路线推荐</h2>
        <div class="similar-grid">
          <el-card
            v-for="r in similarRoutes"
            :key="r.id"
            shadow="hover"
            class="similar-card"
            @click="goSimilar(r.id)"
          >
            <div class="similar-cover" :style="{ backgroundImage: `url(${r.cover})` }" />
            <div class="similar-body">
              <div class="similar-title">{{ r.title }}</div>
              <div class="similar-meta text-subtle">{{ r.days }} 天 · 预算约 {{ r.budget }} 元</div>
            </div>
          </el-card>
        </div>
      </section>
    </template>

    <div v-else class="empty-wrap text-subtle">未找到路线信息</div>
  </div>
</template>

<style scoped>
.route-detail-page {
  min-height: 100vh;
  background: radial-gradient(circle at top left, #e0f2fe 0, transparent 55%),
    radial-gradient(circle at bottom right, #fef3c7 0, transparent 55%),
    #f8fafc;
  padding: 20px 16px 40px;
  position: relative;
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

@media (max-width: 768px) {
  .back-button-container {
    top: 70px;
    left: 12px;
  }
  
  .back-button {
    width: 36px;
    height: 36px;
  }
}

.loading-wrap,
.empty-wrap {
  max-width: 1100px;
  margin: 0 auto;
  padding: 50px 16px;
  text-align: center;
}

.hero-card {
  max-width: 1100px;
  margin: 0 auto 16px;
  background: #fff;
  border-radius: 24px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.12);
  padding: 16px;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
}

.hero-carousel :deep(.el-carousel__container) {
  border-radius: 18px;
}

.cover {
  width: 100%;
  height: 260px;
  background-size: cover;
  background-position: center;
  border-radius: 18px;
}

.hero-info {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;
  padding: 8px 6px;
}

.route-title {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 750;
  color: #0f172a;
}

.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.meta-line {
  color: #64748b;
  font-size: 13px;
  margin-top: 6px;
}

.dot {
  margin: 0 6px;
}

.kpis {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 10px;
}

.kpi {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 10px 12px;
  background: #f8fafc;
}

.kpi-label {
  font-size: 12px;
  color: #94a3b8;
}

.kpi-value {
  margin-top: 2px;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 10px;
}

.stats-grid {
  max-width: 1100px;
  margin: 0 auto 18px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.stat-card {
  border-radius: 18px;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
}

.stat-icon {
  font-size: 22px;
  margin-bottom: 8px;
}

.stat-num {
  font-size: 20px;
  font-weight: 750;
  color: #0f172a;
  line-height: 1.1;
}

.stat-text {
  font-size: 12px;
  color: #64748b;
}

.main-layout {
  max-width: 1100px;
  margin: 0 auto 18px;
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
  gap: 16px;
}

.block-title {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 650;
  color: #0f172a;
}

.day-collapse :deep(.el-collapse-item__header) {
  border-radius: 14px;
  margin-bottom: 10px;
  background: #fff;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.08);
  border: 1px solid #e2e8f0;
}

.day-collapse :deep(.el-collapse-item__wrap) {
  border: none;
  margin-bottom: 10px;
}

.day-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.day-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 2px 10px;
  border-radius: 999px;
  background: #ccfbf1;
  color: #0d9488;
  font-weight: 700;
}

.day-date {
  color: #475569;
  font-size: 13px;
}

.day-meta {
  color: #94a3b8;
  font-size: 12px;
}

.day-card {
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  background: #fff;
}

.day-sections {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.8fr);
  gap: 14px;
}

.day-section .sec-title {
  font-size: 13px;
  font-weight: 650;
  color: #0f172a;
  margin-bottom: 8px;
}

.poi-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.poi-row {
  display: flex;
  gap: 12px;
  padding: 10px 12px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #eef2ff;
  cursor: default;
  transition: transform 0.18s ease, border-color 0.18s ease;
}

.poi-row:hover {
  transform: translateY(-1px);
  border-color: #99f6e4;
}

.poi-row.clickable {
  cursor: pointer;
}

.poi-time {
  width: 110px;
  font-size: 12px;
  color: #64748b;
  flex-shrink: 0;
}

.poi-name {
  font-weight: 600;
  color: #0f172a;
  font-size: 14px;
}

.poi-sub {
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
}

.tips {
  margin: 0;
  padding-left: 16px;
  color: #475569;
  font-size: 13px;
  line-height: 1.7;
}

.map-card {
  background: #020617;
  border-radius: 18px;
  padding: 12px;
  box-shadow: 0 18px 50px rgba(2, 6, 23, 0.35);
}

.map-canvas {
  height: 420px;
  border-radius: 12px;
}

.map-hint {
  margin-top: 8px;
  font-size: 12px;
  color: #cbd5f5;
}

.reviews-section,
.similar-section {
  max-width: 1100px;
  margin: 0 auto 18px;
}

.reviews-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.reviews-kpis {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #475569;
}

.avg-rate {
  font-weight: 700;
  color: #0f172a;
}

.reviews-card {
  border-radius: 18px;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.comment-item {
  display: flex;
  gap: 12px;
}

.comment-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-weight: 700;
  flex-shrink: 0;
}

.comment-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.comment-name {
  font-weight: 650;
  color: #0f172a;
}

.comment-time {
  font-size: 12px;
  color: #94a3b8;
}

.comment-content {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.more-row {
  padding-top: 4px;
  text-align: right;
}

.similar-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.similar-card {
  border-radius: 18px;
  cursor: pointer;
  transition: transform 0.18s ease;
}

.similar-card:hover {
  transform: translateY(-2px);
}

.similar-cover {
  height: 140px;
  border-radius: 14px;
  background-size: cover;
  background-position: center;
}

.similar-body {
  padding-top: 10px;
}

.similar-title {
  font-weight: 650;
  color: #0f172a;
  font-size: 14px;
  line-height: 1.4;
}

.text-subtle {
  color: #64748b;
}

.mobile-tabs {
  display: none;
}

@media (max-width: 980px) {
  .hero-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .stats-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .main-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .mobile-tabs {
    display: block;
    margin-bottom: 10px;
  }

  .hiddenOnMobile {
    display: none;
  }

  .map-canvas {
    height: 320px;
  }

  .day-sections {
    grid-template-columns: minmax(0, 1fr);
  }

  .similar-grid {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }
}
</style>