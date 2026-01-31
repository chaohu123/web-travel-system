<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import RouteHeader from './RouteHeader.vue'
import InterestSlider from './InterestSlider.vue'
import SelectPlanDialog from './SelectPlanDialog.vue'
import { useRoutePlanningStore } from '../../store/routePlanning'
import type { TransportType, IntensityType, PlanVariant } from '../../store/routePlanning'
import { routesApi, buildAiGenerateRequest } from '../../api'
import { loadAmapScript, initAmapMap, addMarker, addPolyline } from '../../utils/amap'
import type { POIItem, DayPlan } from '../../store/routePlanning'

const router = useRouter()
const store = useRoutePlanningStore()
/** 预算与 store 双向绑定，统一为数字避免滑块/输入导致类型不一致或未同步 */
const totalBudgetSync = computed({
  get: () => Number(store.totalBudget) || 0,
  set: (v: number) => { store.totalBudget = Number(v) || 0 },
})
const generating = ref(false)
const newDest = ref('')
const generateError = ref('')
const expandedDays = ref<Set<number>>(new Set([1]))

/** 是否有可选方案（至少一个方案有行程天数） */
const hasPlanVariants = computed(() =>
  store.variants.some((v) => v.days && v.days.length > 0)
)

const selectPlanVisible = ref(false)
const savingPlan = ref(false)
type PlanAction = 'saveRoute' | 'publishCompanion' | 'exportItinerary' | 'saveAndGoCompanion'
const planAction = ref<PlanAction>('saveRoute')

/** 与 RouteGenerateForm 一一对应：交通偏好 */
const transportOptions: { value: TransportType; label: string }[] = [
  { value: 'public', label: '公共交通' },
  { value: 'drive', label: '自驾' },
  { value: 'mixed', label: '混合' },
]
/** 与 RouteGenerateForm.pace 对应：出行节奏 */
const intensityOptions: { value: IntensityType; label: string }[] = [
  { value: 'relaxed', label: '轻松' },
  { value: 'moderate', label: '适中' },
  { value: 'high', label: '高强度' },
]

const activeDays = computed(() => store.activeDays)
const variants = computed(() => store.variants)

function addDestination() {
  const t = newDest.value.trim()
  if (t) {
    store.addDestination(t)
    newDest.value = ''
  }
}

async function generateRoute() {
  if (!store.destinations.length) {
    generateError.value = '请至少添加一个目的地'
    return
  }
  generating.value = true
  generateError.value = ''
  try {
    const body = buildAiGenerateRequest(
      store.routeGenerateForm,
      store.startDate,
      store.endDate,
      store.peopleCount
    )
    console.log('[AI路线] 请求体（发送给后端）:', JSON.stringify(body, null, 2))
    const res = await routesApi.aiGenerate(body)
    console.log('[AI路线] 后端返回完整数据:', res)
    console.log('[AI路线] 方案数:', res?.variants?.length ?? 0, '各方案天数:', res?.variants?.map((v) => v.days?.length ?? 0))
    store.setVariantsFromApi(res)
    expandedDays.value = new Set(activeDays.value.map((d) => d.dayIndex))
  } catch (e: unknown) {
    console.warn('[AI路线] 请求失败:', e)
    const msg = e instanceof Error ? e.message : '生成失败，请稍后重试'
    generateError.value = msg
    store.generateItinerary()
    expandedDays.value = new Set(activeDays.value.map((d) => d.dayIndex))
  } finally {
    generating.value = false
  }
}

function toggleDay(dayIndex: number) {
  const next = new Set(expandedDays.value)
  if (next.has(dayIndex)) next.delete(dayIndex)
  else next.add(dayIndex)
  expandedDays.value = next
}

function formatDuration(m: number) {
  const h = Math.floor(m / 60)
  const min = m % 60
  return min ? `${h} 小时 ${min} 分钟` : `${h} 小时`
}

function onDragStart(e: DragEvent, dayIndex: number, index: number) {
  if (!e.dataTransfer) return
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', JSON.stringify({ dayIndex, index }))
  e.dataTransfer.dropEffect = 'move'
  ;(e.target as HTMLElement).classList.add('opacity-50')
}

function onDragEnd(e: DragEvent) {
  ;(e.target as HTMLElement).classList.remove('opacity-50')
}

function onDragOver(e: DragEvent) {
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
}

function onDrop(e: DragEvent, dayIndex: number, toIndex: number) {
  e.preventDefault()
  try {
    const raw = e.dataTransfer?.getData('text/plain')
    if (!raw) return
    const { dayIndex: fromDay, index: fromIndex } = JSON.parse(raw)
    if (fromDay !== dayIndex) return
    store.reorderDayItems(dayIndex, fromIndex, toIndex)
  } catch {
    // ignore
  }
}

function removeItem(dayIndex: number, itemId: string) {
  store.removeDayItem(dayIndex, itemId)
}

// ---------- 右侧地图：高德地图展示路线与每日行程 ----------
const mapContainerRef = ref<HTMLDivElement | null>(null)
let amapInstance: any = null
const mapMarkers: any[] = []
const mapPolylines: any[] = []
const DAY_COLORS = ['#22c55e', '#3b82f6', '#8b5cf6', '#ec4899', '#f59e0b']

function getPointsFromDays(days: DayPlan[]): { path: [number, number][]; markers: { pos: [number, number]; item: POIItem }[] } {
  const path: [number, number][] = []
  const markers: { pos: [number, number]; item: POIItem }[] = []
  for (const day of days) {
    for (const item of day.items) {
      if (item.lng != null && item.lat != null) {
        const pos: [number, number] = [item.lng, item.lat]
        path.push(pos)
        markers.push({ pos, item })
      }
    }
  }
  return { path, markers }
}

async function initRouteMap() {
  const container = mapContainerRef.value
  if (!container) return
  const days = activeDays.value
  const { path, markers } = getPointsFromDays(days)
  if (path.length === 0) {
    // 无坐标时只显示占位，不加载地图
    if (amapInstance) {
      try {
        mapMarkers.forEach((m) => amapInstance.remove(m))
        mapPolylines.forEach((p) => amapInstance.remove(p))
        amapInstance.destroy()
      } catch (_) {}
      amapInstance = null
      mapMarkers.length = 0
      mapPolylines.length = 0
    }
    return
  }
  try {
    await loadAmapScript()
  } catch (e) {
    console.warn('地图加载失败', e)
    return
  }
  if (amapInstance) {
    try {
      mapMarkers.forEach((m) => amapInstance.remove(m))
      mapPolylines.forEach((p) => amapInstance.remove(p))
      amapInstance.destroy()
    } catch (_) {}
    amapInstance = null
    mapMarkers.length = 0
    mapPolylines.length = 0
  }
  amapInstance = initAmapMap(container, path[0], 12, { mapStyle: 'amap://styles/normal' })
  const AMap = (window as any).AMap
  for (const { pos, item } of markers) {
    const marker = new AMap.Marker({
      position: pos,
      title: item.name,
    })
    amapInstance.add(marker)
    mapMarkers.push(marker)
    const content = `<div style="padding:8px;min-width:120px;"><b>${item.name}</b><br/>建议停留 ${item.stayMinutes} 分钟</div>`
    const infoWindow = new AMap.InfoWindow({ content, offset: new AMap.Pixel(0, -30) })
    marker.on('click', () => infoWindow.open(amapInstance, marker.getPosition()))
  }
  // 按天画折线，每天一种颜色
  let idx = 0
  for (const day of days) {
    const dayPath: [number, number][] = []
    for (const item of day.items) {
      if (item.lng != null && item.lat != null) dayPath.push([item.lng, item.lat])
    }
    if (dayPath.length >= 2) {
      const color = DAY_COLORS[idx % DAY_COLORS.length]
      const line = addPolyline(amapInstance, dayPath, color)
      mapPolylines.push(line)
    }
    idx++
  }
  amapInstance.on('complete', () => {
    if (mapMarkers.length) amapInstance.setFitView(mapMarkers)
    amapInstance.resize()
  })
}

watch(
  () => [activeDays.value, store.activeVariantId],
  () => initRouteMap(),
  { deep: true }
)
onMounted(() => initRouteMap())
onBeforeUnmount(() => {
  if (amapInstance) {
    try {
      mapMarkers.forEach((m) => amapInstance.remove(m))
      mapPolylines.forEach((p) => amapInstance.remove(p))
      amapInstance.destroy()
    } catch (_) {}
    amapInstance = null
  }
  mapMarkers.length = 0
  mapPolylines.length = 0
})

/** 将 store 强度映射为后端 pace 字符串 */
function paceFromStore(): string {
  const i = store.intensity
  if (i === 'relaxed') return 'relax'
  if (i === 'high') return 'rush'
  return 'normal'
}

/** 将分钟数转为 "HH:mm"；用于计算当日游玩时间段 */
function minutesToTime(minutes: number): string {
  const h = Math.floor(minutes / 60) % 24
  const m = minutes % 60
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`
}

/** 将所选方案的每日行程转为 API 的 days 格式（含每个景点的 startTime/endTime，由 stayMinutes 推算） */
function buildDaysFromVariant(variantId: string) {
  const v = store.variants.find((x) => x.id === variantId)
  if (!v?.days?.length) return undefined
  const DAY_START_MINUTES = 9 * 60 // 09:00 开始
  const GAP_MINUTES = 30 // 景点间间隔 30 分钟
  return v.days.map((d) => {
    let cursor = DAY_START_MINUTES
    const activities = (d.items ?? []).map((it) => {
      const stay = Math.max(30, it.stayMinutes || 60)
      const startTime = minutesToTime(cursor)
      cursor += stay
      const endTime = minutesToTime(cursor)
      cursor += GAP_MINUTES
      return {
        type: it.tags?.[0] ?? 'sight',
        name: it.name,
        location: it.name,
        startTime,
        endTime,
        estimatedCost: stay ? Math.round((stay / 60) * 50) : 0,
        lng: it.lng,
        lat: it.lat,
      }
    })
    return {
      dayIndex: d.dayIndex,
      date: d.date,
      activities,
    }
  })
}

/** 从当前 store 构建创建路线请求体（可选包含所选方案的每日活动） */
function buildCreatePlanRequest(variantId?: string) {
  const dest = store.destinations.length ? store.destinations.join('、') : '未设置目的地'
  const body: import('../../api/types').CreatePlanRequest = {
    destination: dest,
    startDate: store.startDate,
    endDate: store.endDate,
    budget: Number(store.totalBudget) || 0,
    peopleCount: store.peopleCount ?? 2,
    pace: paceFromStore(),
    preferenceWeightsJson: JSON.stringify(store.interestWeights),
  }
  if (variantId) {
    const days = buildDaysFromVariant(variantId)
    if (days?.length) body.days = days
  }
  return body
}

/** 选择方案后的统一确认：根据当前 action 执行保存/跳转/导出 */
async function onPlanSelected(variantId: string) {
  store.setActiveVariant(variantId)
  const action = planAction.value

  if (action === 'saveRoute') {
    savingPlan.value = true
    try {
      const body = buildCreatePlanRequest(variantId)
      await routesApi.create(body)
      ElMessage.success('路线已保存')
      selectPlanVisible.value = false
      router.push('/routes')
    } catch (e: any) {
      const msg = e?.message || e?.response?.data?.message || '保存失败'
      ElMessage.error(msg)
    } finally {
      savingPlan.value = false
    }
    return
  }

  if (action === 'publishCompanion') {
    selectPlanVisible.value = false
    const dest = store.destinations.length ? store.destinations.join('、') : ''
    router.push({
      path: '/companion/create',
      query: {
        destination: dest,
        startDate: store.startDate,
        endDate: store.endDate,
      },
    })
    return
  }

  if (action === 'exportItinerary') {
    const v = store.variants.find((x) => x.id === variantId)
    if (!v || !v.days?.length) {
      ElMessage.warning('该方案暂无行程数据')
      return
    }
    const lines: string[] = [
      `# ${v.name}`,
      '',
      `目的地：${store.destinations.join('、') || '-'}`,
      `日期：${store.startDate} ~ ${store.endDate}`,
      '',
    ]
    for (const day of v.days) {
      lines.push(`## Day ${day.dayIndex} ${day.date}`)
      lines.push('')
      for (const item of day.items ?? []) {
        lines.push(`- **${item.name}**（建议停留 ${item.stayMinutes} 分钟）${item.tags?.length ? ` [${item.tags.join(', ')}]` : ''}`)
      }
      lines.push('')
    }
    const blob = new Blob([lines.join('\n')], { type: 'text/markdown;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `行程-${v.name.replace(/\s+/g, '-')}-${store.startDate}.md`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('行程已导出')
    selectPlanVisible.value = false
    return
  }

  if (action === 'saveAndGoCompanion') {
    savingPlan.value = true
    try {
      const body = buildCreatePlanRequest(variantId)
      const id = await routesApi.create(body)
      ElMessage.success('路线已保存')
      selectPlanVisible.value = false
      router.push(`/companion/create?planId=${id}`)
    } catch (e: any) {
      const msg = e?.message || e?.response?.data?.message || '保存失败'
      ElMessage.error(msg)
    } finally {
      savingPlan.value = false
    }
  }
}

function openSelectPlan(action: PlanAction) {
  if (!hasPlanVariants.value) {
    ElMessage.warning('请先生成路线（点击「AI 生成我的路线」）')
    return
  }
  planAction.value = action
  selectPlanVisible.value = true
}

function saveRoute() {
  openSelectPlan('saveRoute')
}
function inviteBuddies() {
  router.push('/companion')
}
function publishCompanion() {
  openSelectPlan('publishCompanion')
}
function exportItinerary() {
  openSelectPlan('exportItinerary')
}
function saveAndGoCompanion() {
  openSelectPlan('saveAndGoCompanion')
}
</script>

<template>
  <div class="route-planning min-h-screen flex flex-col bg-gradient-to-br from-slate-50 via-teal-50/30 to-indigo-50/30">
    <RouteHeader class="mx-4 mt-4 mb-2" />

    <div class="flex-1 flex flex-col lg:flex-row gap-4 px-4 pb-4 min-h-0">
      <!-- 左侧配置区 -->
      <aside class="w-full lg:w-[30%] lg:min-w-[280px] lg:max-w-[380px] flex-shrink-0 space-y-4 overflow-y-auto">
        <el-card shadow="never" class="rounded-2xl bg-white/95 border border-slate-200/80">
          <h3 class="text-sm font-semibold text-slate-800 mb-3">行程基础信息</h3>
          <div class="space-y-3">
            <div>
              <div class="text-xs text-slate-500 mb-1">出发地</div>
              <el-input
                v-model="store.departureCity"
                placeholder="例如：上海"
                size="small"
              />
            </div>
            <div>
              <div class="text-xs text-slate-500 mb-1">目的地</div>
              <div class="flex flex-wrap gap-2 mb-2">
                <span
                  v-for="d in store.destinations"
                  :key="d"
                  class="inline-flex items-center gap-1 px-3 py-1.5 rounded-full bg-teal-100 text-teal-800 text-sm font-medium"
                >
                  {{ d }}
                  <button
                    type="button"
                    class="ml-0.5 rounded-full p-0.5 hover:bg-teal-200/80 text-teal-700"
                    aria-label="删除"
                    @click="store.removeDestination(d)"
                  >
                    <span class="sr-only">删除</span>×
                  </button>
                </span>
              </div>
              <div class="flex gap-2">
                <input
                  v-model="newDest"
                  type="text"
                  placeholder="输入目的地后点击添加"
                  class="flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm focus:ring-2 focus:ring-teal-500/50 focus:border-teal-500"
                  @keydown.enter.prevent="addDestination"
                />
                <button
                  type="button"
                  class="px-3 py-2 rounded-lg bg-slate-100 text-slate-700 text-sm font-medium hover:bg-slate-200"
                  @click="addDestination"
                >
                  添加
                </button>
              </div>
            </div>
            <div v-if="store.departureCity || store.destinations.length" class="text-sm text-slate-600 pt-1 border-t border-slate-100">
              <span class="font-medium">{{ store.departureCity || '出发地' }}</span>
              <span class="mx-1">→</span>
              <span class="font-medium">{{ store.destinations.length ? store.destinations.join('、') : '目的地' }}</span>
            </div>
          </div>
        </el-card>

        <el-card shadow="never" class="rounded-2xl bg-white/95 border border-slate-200/80">
          <h3 class="text-sm font-semibold text-slate-800 mb-2">总预算（元）</h3>
          <el-slider
            v-model="totalBudgetSync"
            :min="1000"
            :max="50000"
            :step="500"
            show-input
          />
        </el-card>

        <el-card shadow="never" class="rounded-2xl bg-white/95 border border-slate-200/80">
          <h3 class="text-sm font-semibold text-slate-800 mb-2">交通偏好</h3>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="opt in transportOptions"
              :key="opt.value"
              type="button"
              class="px-3 py-2 rounded-xl text-sm font-medium transition-all"
              :class="store.transport === opt.value
                ? 'bg-teal-500 text-white shadow-md'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'"
              @click="store.transport = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
        </el-card>

        <el-card shadow="never" class="rounded-2xl bg-white/95 border border-slate-200/80">
          <h3 class="text-sm font-semibold text-slate-800 mb-2">每日游玩强度</h3>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="opt in intensityOptions"
              :key="opt.value"
              type="button"
              class="px-3 py-2 rounded-xl text-sm font-medium transition-all"
              :class="store.intensity === opt.value
                ? 'bg-indigo-500 text-white shadow-md'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'"
              @click="store.intensity = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
        </el-card>

        <el-card shadow="never" class="rounded-2xl bg-white/95 border border-slate-200/80">
          <h3 class="text-sm font-semibold text-slate-800 mb-3">兴趣权重</h3>
          <div class="space-y-4">
            <InterestSlider v-model="store.interestWeights.nature" label="自然风光" />
            <InterestSlider v-model="store.interestWeights.culture" label="历史文化" />
            <InterestSlider v-model="store.interestWeights.food" label="美食体验" />
            <InterestSlider v-model="store.interestWeights.shopping" label="购物娱乐" />
            <InterestSlider v-model="store.interestWeights.relax" label="休闲放松" />
          </div>
        </el-card>

        <p v-if="generateError" class="text-sm text-red-600 mb-2">{{ generateError }}</p>
        <button
          type="button"
          class="w-full py-3.5 rounded-2xl font-semibold text-white shadow-lg transition-all bg-gradient-to-r from-teal-500 to-indigo-500 hover:from-teal-600 hover:to-indigo-600 hover:shadow-xl disabled:opacity-70"
          :disabled="generating"
          @click="generateRoute"
        >
          {{ generating ? 'AI 生成中…' : 'AI 生成我的路线' }}
        </button>
      </aside>

      <!-- 右侧：方案 Tabs + 地图 + 行程 -->
      <div class="flex-1 flex flex-col min-h-0 rounded-2xl bg-white/95 border border-slate-200/80 shadow-sm overflow-hidden">
        <div class="flex border-b border-slate-200 bg-slate-50/80">
          <button
            v-for="v in variants"
            :key="v.id"
            type="button"
            class="flex-1 py-3 px-4 text-sm font-medium transition-colors"
            :class="store.activeVariantId === v.id
              ? 'text-teal-600 border-b-2 border-teal-500 bg-white'
              : 'text-slate-600 hover:text-slate-800 hover:bg-white/50'"
            @click="store.setActiveVariant(v.id)"
          >
            {{ v.name }}
          </button>
        </div>

        <div class="p-4 border-b border-slate-100">
          <div class="rounded-xl overflow-hidden border border-slate-200 bg-slate-50/80 min-h-[220px] relative">
            <div ref="mapContainerRef" class="w-full h-[220px]" />
            <span
              v-if="!activeDays.length || !activeDays.some((d) => d.items.some((it) => it.lng != null))"
              class="absolute inset-0 flex items-center justify-center text-slate-500 text-sm pointer-events-none"
            >
              生成路线后将在此显示地图路径
            </span>
          </div>
        </div>

        <div class="flex-1 overflow-y-auto p-4">
          <template v-if="activeDays.length">
            <div class="space-y-2">
              <div
                v-for="day in activeDays"
                :key="day.dayIndex"
                class="rounded-2xl border border-slate-200/80 bg-white shadow-sm overflow-hidden"
              >
                <button
                  type="button"
                  class="w-full flex items-center justify-between p-4 text-left hover:bg-slate-50/80 transition-colors"
                  @click="toggleDay(day.dayIndex)"
                >
                  <div class="flex items-center gap-3">
                    <span class="text-lg font-bold text-teal-600">Day {{ day.dayIndex }}</span>
                    <span class="text-slate-600">{{ day.date }}</span>
                  </div>
                  <div class="text-sm text-slate-500">
                    约 {{ formatDuration(day.durationMinutes) }} · {{ day.distanceKm }} km · 通勤约 {{ day.commuteMinutes }} 分钟
                  </div>
                  <span class="text-slate-400 transform transition-transform" :class="expandedDays.has(day.dayIndex) ? 'rotate-180' : ''">▼</span>
                </button>
                <div v-show="expandedDays.has(day.dayIndex)" class="border-t border-slate-100">
                  <div class="p-3 space-y-3">
                    <div
                      v-for="(item, index) in day.items"
                      :key="item.id"
                      draggable="true"
                      class="flex gap-3 p-3 rounded-xl bg-slate-50/80 border border-slate-100 hover:border-teal-200/80 transition-colors"
                      @dragstart="onDragStart($event, day.dayIndex, index)"
                      @dragend="onDragEnd"
                      @dragover="onDragOver"
                      @drop="onDrop($event, day.dayIndex, index)"
                    >
                      <img
                        :src="item.image"
                        :alt="item.name"
                        class="w-20 h-20 object-cover rounded-lg flex-shrink-0"
                      />
                      <div class="flex-1 min-w-0">
                        <div class="font-medium text-slate-800">{{ item.name }}</div>
                        <div class="text-sm text-slate-500 mt-0.5">建议停留 {{ item.stayMinutes }} 分钟</div>
                        <div class="flex flex-wrap gap-1.5 mt-1.5">
                          <span
                            v-for="tag in item.tags"
                            :key="tag"
                            class="px-2 py-0.5 rounded-full bg-teal-100/80 text-teal-700 text-xs font-medium"
                          >
                            {{ tag }}
                          </span>
                        </div>
                      </div>
                      <div class="flex flex-col gap-2 flex-shrink-0">
                        <span class="text-slate-400 cursor-grab text-sm" title="拖拽排序">⋮⋮</span>
                        <button
                          type="button"
                          class="text-slate-400 hover:text-red-500 text-sm"
                          aria-label="删除"
                          @click="removeItem(day.dayIndex, item.id)"
                        >
                          删除
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="flex flex-col items-center justify-center py-16 text-slate-500">
            <p class="mb-2">尚未生成行程</p>
            <p class="text-sm">在左侧完成配置后点击「AI 生成我的路线」</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部固定操作栏 -->
    <div class="sticky bottom-0 left-0 right-0 z-40 flex items-center justify-between gap-4 px-4 py-3 bg-white/95 backdrop-blur-md border-t border-slate-200 shadow-[0_-4px_12px_rgba(0,0,0,0.06)]">
      <div class="flex flex-wrap items-center gap-2">
        <button
          type="button"
          class="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 transition-colors"
          @click="saveRoute"
        >
          保存路线
        </button>
        <button
          type="button"
          class="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 transition-colors"
          @click="inviteBuddies"
        >
          邀请旅友
        </button>
        <button
          type="button"
          class="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 transition-colors"
          @click="publishCompanion"
        >
          发布结伴
        </button>
        <button
          type="button"
          class="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 transition-colors"
          @click="exportItinerary"
        >
          导出行程
        </button>
        <button
          type="button"
          class="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 transition-colors"
          @click="store.resetPlan()"
        >
          重置
        </button>
      </div>
      <button
        type="button"
        class="px-6 py-2.5 rounded-xl text-sm font-semibold text-white shadow-md bg-gradient-to-r from-teal-500 to-indigo-500 hover:from-teal-600 hover:to-indigo-600 hover:shadow-lg transition-all"
        @click="saveAndGoCompanion"
      >
        保存并进入结伴
      </button>
    </div>

    <!-- 选择方案弹窗：保存路线 / 发布结伴 / 导出行程 / 保存并进入结伴 -->
    <SelectPlanDialog
      v-model:visible="selectPlanVisible"
      :variants="variants"
      :loading="savingPlan"
      @confirm="onPlanSelected"
    />
  </div>
</template>
