import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { RouteGenerateForm } from '../api/types'

export type TransportType = 'public' | 'drive' | 'mixed'
export type IntensityType = 'relaxed' | 'moderate' | 'high'

/** 与 RouteGenerateForm.pace 对应：easy -> relaxed, medium -> moderate, hard -> high */
export const PACE_TO_INTENSITY: Record<string, IntensityType> = {
  easy: 'relaxed',
  medium: 'moderate',
  hard: 'high',
}
/** 与 RouteGenerateForm.transportType 对应：car -> drive */
export const TRANSPORT_FORM_TO_STORE: Record<string, TransportType> = {
  public: 'public',
  car: 'drive',
  mixed: 'mixed',
}

export interface InterestWeights {
  nature: number      // 自然风光
  culture: number     // 历史文化
  food: number        // 美食体验
  shopping: number    // 购物娱乐
  relax: number       // 休闲放松
}

export interface POIItem {
  id: string
  image: string
  name: string
  stayMinutes: number
  tags: string[]
  /** 经度，供地图展示 */
  lng?: number
  /** 纬度，供地图展示 */
  lat?: number
}

export interface DayPlan {
  dayIndex: number
  date: string
  durationMinutes: number
  distanceKm: number
  commuteMinutes: number
  items: POIItem[]
}

export interface PlanVariant {
  id: string
  name: string
  days: DayPlan[]
}

function randomId(): string {
  return Math.random().toString(36).slice(2, 11)
}

function mockPOI(seed: number, name: string, tags: string[], stay = 60): POIItem {
  return {
    id: randomId(),
    image: `https://picsum.photos/seed/poi${seed}/320/180`,
    name,
    stayMinutes: stay,
    tags,
  }
}

/** 按目的地城市选择 mock POI 池，与后端逻辑一致（上海→苏州显示苏州/上海景点） */
function getMockPoolsByDestination(destination: string): {
  culture: (() => POIItem)[]
  nature: (() => POIItem)[]
  relax: (() => POIItem)[]
} {
  const city = (destination || '').trim()
  if (city.includes('苏州')) {
    return {
      culture: [
        () => mockPOI(1, '拙政园', ['文化', '园林'], 150),
        () => mockPOI(2, '苏州博物馆', ['文化', '历史'], 120),
        () => mockPOI(3, '狮子林', ['文化', '园林'], 90),
        () => mockPOI(4, '虎丘', ['文化', '自然'], 120),
        () => mockPOI(5, '寒山寺', ['文化', '宗教'], 90),
      ],
      nature: [
        () => mockPOI(10, '金鸡湖', ['自然', '休闲'], 120),
        () => mockPOI(11, '阳澄湖', ['自然', '美食'], 150),
        () => mockPOI(12, '太湖湿地', ['自然', '生态'], 180),
        () => mockPOI(13, '平江路', ['自然', '文化'], 90),
        () => mockPOI(14, '同里古镇', ['自然', '古镇'], 150),
      ],
      relax: [
        () => mockPOI(20, '平江路漫步', ['休闲', '文化'], 90),
        () => mockPOI(21, '山塘街', ['休闲', '美食'], 120),
        () => mockPOI(22, '观前街', ['休闲', '购物'], 90),
        () => mockPOI(23, '苏州评弹', ['休闲', '文化'], 60),
        () => mockPOI(24, '苏帮菜馆', ['美食', '文化'], 90),
      ],
    }
  }
  if (city.includes('上海')) {
    return {
      culture: [
        () => mockPOI(1, '豫园', ['文化', '园林'], 120),
        () => mockPOI(2, '上海博物馆', ['文化', '历史'], 150),
        () => mockPOI(3, '田子坊', ['文化', '创意'], 90),
        () => mockPOI(4, '新天地', ['文化', '休闲'], 120),
        () => mockPOI(5, '中共一大会址', ['文化', '历史'], 90),
      ],
      nature: [
        () => mockPOI(10, '外滩', ['自然', '景观'], 120),
        () => mockPOI(11, '世纪公园', ['自然', '休闲'], 150),
        () => mockPOI(12, '朱家角古镇', ['自然', '古镇'], 180),
        () => mockPOI(13, '滨江森林公园', ['自然', '生态'], 120),
        () => mockPOI(14, '东方明珠', ['自然', '地标'], 90),
      ],
      relax: [
        () => mockPOI(20, '南京路步行街', ['休闲', '购物'], 120),
        () => mockPOI(21, '田子坊', ['休闲', '美食'], 90),
        () => mockPOI(22, '新天地', ['休闲', '文化'], 90),
        () => mockPOI(23, '外滩夜景', ['休闲', '景观'], 60),
        () => mockPOI(24, '城隍庙小吃', ['美食', '文化'], 90),
      ],
    }
  }
  if (city.includes('北京')) {
    return {
      culture: [
        () => mockPOI(1, '故宫博物院', ['文化', '历史'], 180),
        () => mockPOI(2, '国家博物馆', ['文化', '历史'], 120),
        () => mockPOI(3, '南锣鼓巷', ['文化', '美食'], 90),
        () => mockPOI(4, '颐和园', ['文化', '自然'], 150),
        () => mockPOI(5, '雍和宫', ['文化', '宗教'], 90),
      ],
      nature: [
        () => mockPOI(10, '颐和园', ['自然', '文化'], 150),
        () => mockPOI(11, '北海公园', ['自然', '休闲'], 120),
        () => mockPOI(12, '香山', ['自然', '徒步'], 180),
        () => mockPOI(13, '奥森公园', ['自然', '生态'], 120),
        () => mockPOI(14, '什刹海', ['自然', '文化'], 90),
      ],
      relax: [
        () => mockPOI(20, '古镇漫步', ['休闲', '文化'], 120),
        () => mockPOI(21, '温泉酒店', ['休闲', '放松'], 180),
        () => mockPOI(22, '咖啡馆', ['休闲', '美食'], 60),
        () => mockPOI(23, '夜市', ['美食', '购物'], 90),
        () => mockPOI(24, '海边栈道', ['休闲', '自然'], 90),
      ],
    }
  }
  if (city.includes('杭州')) {
    return {
      culture: [
        () => mockPOI(1, '灵隐寺', ['文化', '宗教'], 90),
        () => mockPOI(2, '宋城', ['文化', '演艺'], 180),
        () => mockPOI(3, '河坊街', ['文化', '美食'], 90),
        () => mockPOI(4, '中国美院', ['文化', '艺术'], 120),
        () => mockPOI(5, '六和塔', ['文化', '历史'], 60),
      ],
      nature: [
        () => mockPOI(10, '西湖', ['自然', '休闲'], 120),
        () => mockPOI(11, '灵隐寺', ['自然', '文化'], 90),
        () => mockPOI(12, '西溪湿地', ['自然', '生态'], 180),
        () => mockPOI(13, '九溪烟树', ['自然', '徒步'], 90),
        () => mockPOI(14, '龙井村', ['自然', '美食'], 120),
      ],
      relax: [
        () => mockPOI(20, '古镇漫步', ['休闲', '文化'], 120),
        () => mockPOI(21, '温泉酒店', ['休闲', '放松'], 180),
        () => mockPOI(22, '咖啡馆', ['休闲', '美食'], 60),
        () => mockPOI(23, '夜市', ['美食', '购物'], 90),
        () => mockPOI(24, '海边栈道', ['休闲', '自然'], 90),
      ],
    }
  }
  // 默认（未填或其它城市）：通用池
  return {
    culture: [
      () => mockPOI(1, '拙政园', ['文化', '园林'], 150),
      () => mockPOI(2, '苏州博物馆', ['文化', '历史'], 120),
      () => mockPOI(3, '狮子林', ['文化', '园林'], 90),
      () => mockPOI(4, '虎丘', ['文化', '自然'], 120),
      () => mockPOI(5, '寒山寺', ['文化', '宗教'], 90),
    ],
    nature: [
      () => mockPOI(10, '金鸡湖', ['自然', '休闲'], 120),
      () => mockPOI(11, '阳澄湖', ['自然', '美食'], 150),
      () => mockPOI(12, '平江路', ['自然', '文化'], 90),
      () => mockPOI(13, '同里古镇', ['自然', '古镇'], 150),
      () => mockPOI(14, '太湖湿地', ['自然', '生态'], 180),
    ],
    relax: [
      () => mockPOI(20, '平江路漫步', ['休闲', '文化'], 90),
      () => mockPOI(21, '山塘街', ['休闲', '美食'], 120),
      () => mockPOI(22, '观前街', ['休闲', '购物'], 90),
      () => mockPOI(23, '苏帮菜馆', ['美食', '文化'], 90),
      () => mockPOI(24, '苏州评弹', ['休闲', '文化'], 60),
    ],
  }
}

function buildMockDays(variant: 'culture' | 'nature' | 'relax', startDate: string, destination?: string): DayPlan[] {
  const start = new Date(startDate)
  const days: DayPlan[] = []
  const count = 3
  const pools = getMockPoolsByDestination(destination || '')
  const pool = variant === 'culture' ? pools.culture : variant === 'nature' ? pools.nature : pools.relax
  for (let i = 0; i < count; i++) {
    const d = new Date(start)
    d.setDate(d.getDate() + i)
    const items: POIItem[] = []
    const n = 2 + (i % 2)
    for (let j = 0; j < n; j++) {
      items.push(pool[(i * 2 + j) % pool.length]())
    }
    const durationMinutes = items.reduce((s, it) => s + it.stayMinutes, 0) + 30 * (items.length - 1)
    days.push({
      dayIndex: i + 1,
      date: d.toISOString().slice(0, 10),
      durationMinutes,
      distanceKm: 12 + i * 8,
      commuteMinutes: 20 + i * 10,
      items,
    })
  }
  return days
}

export const useRoutePlanningStore = defineStore('routePlanning', () => {
  const routeName = ref('我的旅行路线')
  const today = new Date().toISOString().slice(0, 10)
  const defaultEnd = (() => {
    const d = new Date(today)
    d.setDate(d.getDate() + 4)
    return d.toISOString().slice(0, 10)
  })()
  const startDate = ref(today)
  const endDate = ref(defaultEnd)
  const peopleCount = ref(2)
  /** 出发地（出发城市） */
  const departureCity = ref('')
  const destinations = ref<string[]>([])
  const totalBudget = ref(8000)
  const transport = ref<TransportType>('mixed')
  const intensity = ref<IntensityType>('moderate')
  const interestWeights = ref<InterestWeights>({
    nature: 60,
    culture: 80,
    food: 70,
    shopping: 40,
    relax: 50,
  })

  const variants = ref<PlanVariant[]>([
    { id: 'a', name: '方案 A（文化优先）', days: [] },
    { id: 'b', name: '方案 B（自然优先）', days: [] },
    { id: 'c', name: '方案 C（轻松休闲）', days: [] },
  ])
  const activeVariantId = ref('a')

  const activeVariant = computed(() =>
    variants.value.find((v) => v.id === activeVariantId.value) ?? variants.value[0]
  )
  const activeDays = computed(() => activeVariant.value.days)

  const budgetSummary = computed(() => {
    const days = activeDays.value
    const total = totalBudget.value
    const people = peopleCount.value
    if (!days.length) return { perDay: 0, perPerson: 0, total }
    const perDay = Math.round(total / days.length)
    const perPerson = Math.round(total / people)
    return { perDay, perPerson, total }
  })

  /** 与页面 UI 一一对应的表单数据结构（RouteGenerateForm），用于 AI 生成路线 */
  const routeGenerateForm = computed<RouteGenerateForm>(() => ({
    startCity: departureCity.value,
    destinations: [...destinations.value],
    budget: totalBudget.value,
    transportType: transport.value === 'drive' ? 'car' : transport.value,
    pace: intensity.value === 'relaxed' ? 'easy' : intensity.value === 'high' ? 'hard' : 'medium',
    interests: { ...interestWeights.value },
  }))

  /** 从 RouteGenerateForm 写回 store（表单绑定用） */
  function applyRouteGenerateForm(form: Partial<RouteGenerateForm>) {
    if (form.startCity !== undefined) departureCity.value = form.startCity
    if (form.destinations !== undefined) destinations.value = [...form.destinations]
    if (form.budget !== undefined) totalBudget.value = form.budget
    if (form.transportType !== undefined) transport.value = TRANSPORT_FORM_TO_STORE[form.transportType] ?? transport.value
    if (form.pace !== undefined) intensity.value = PACE_TO_INTENSITY[form.pace] ?? intensity.value
    if (form.interests !== undefined) interestWeights.value = { ...form.interests }
  }

  function setRouteName(name: string) {
    routeName.value = name
  }

  function addDestination(d: string) {
    const t = d.trim()
    if (t && !destinations.value.includes(t)) destinations.value = [...destinations.value, t]
  }

  function removeDestination(d: string) {
    destinations.value = destinations.value.filter((x) => x !== d)
  }

  function generateItinerary() {
    const start = startDate.value || new Date().toISOString().slice(0, 10)
    const destination = destinations.value[0] || ''
    variants.value = [
      { id: 'a', name: '方案 A（文化优先）', days: buildMockDays('culture', start, destination) },
      { id: 'b', name: '方案 B（自然优先）', days: buildMockDays('nature', start, destination) },
      { id: 'c', name: '方案 C（轻松休闲）', days: buildMockDays('relax', start, destination) },
    ]
    if (!activeVariantId.value) activeVariantId.value = 'a'
  }

  /** 用后端 AI 生成接口返回的数据覆盖当前方案（与 AiGenerateRouteResponse 结构一致），保留 POI 的 lng/lat */
  function setVariantsFromApi(response: { variants: PlanVariant[] }) {
    variants.value = response.variants.map((v) => ({
      id: v.id,
      name: v.name,
      days: (v.days || []).map((d) => ({
        dayIndex: d.dayIndex,
        date: typeof d.date === 'string' ? d.date : String(d.date ?? ''),
        durationMinutes: d.durationMinutes ?? 0,
        distanceKm: d.distanceKm ?? 0,
        commuteMinutes: d.commuteMinutes ?? 0,
        items: (d.items ?? []).map((it) => ({
          id: it.id,
          image: it.image,
          name: it.name,
          stayMinutes: it.stayMinutes,
          tags: it.tags ?? [],
          lng: it.lng,
          lat: it.lat,
        })),
      })),
    }))
    if (variants.value.length && (!activeVariantId.value || !variants.value.some((x) => x.id === activeVariantId.value))) {
      activeVariantId.value = variants.value[0].id
    }
  }

  function setActiveVariant(id: string) {
    activeVariantId.value = id
  }

  function reorderDayItems(dayIndex: number, fromIndex: number, toIndex: number) {
    const variant = variants.value.find((v) => v.id === activeVariantId.value)
    if (!variant) return
    const day = variant.days.find((d) => d.dayIndex === dayIndex)
    if (!day || fromIndex === toIndex) return
    const items = [...day.items]
    const [removed] = items.splice(fromIndex, 1)
    items.splice(toIndex, 0, removed)
    day.items = items
    day.durationMinutes = items.reduce((s, it) => s + it.stayMinutes, 0) + 30 * Math.max(0, items.length - 1)
  }

  function removeDayItem(dayIndex: number, itemId: string) {
    const variant = variants.value.find((v) => v.id === activeVariantId.value)
    if (!variant) return
    const day = variant.days.find((d) => d.dayIndex === dayIndex)
    if (!day) return
    day.items = day.items.filter((it) => it.id !== itemId)
    day.durationMinutes = day.items.reduce((s, it) => s + it.stayMinutes, 0) + 30 * Math.max(0, day.items.length - 1)
  }

  /** 重置：清除已生成的行程方案（三个方案清空为无日程），便于重新生成 */
  function resetPlan() {
    variants.value = [
      { id: 'a', name: '方案 A（文化优先）', days: [] },
      { id: 'b', name: '方案 B（自然优先）', days: [] },
      { id: 'c', name: '方案 C（轻松休闲）', days: [] },
    ]
    activeVariantId.value = 'a'
  }

  return {
    routeName,
    startDate,
    endDate,
    peopleCount,
    departureCity,
    destinations,
    totalBudget,
    transport,
    intensity,
    interestWeights,
    routeGenerateForm,
    applyRouteGenerateForm,
    variants,
    activeVariantId,
    activeVariant,
    activeDays,
    budgetSummary,
    setRouteName,
    addDestination,
    removeDestination,
    generateItinerary,
    setVariantsFromApi,
    setActiveVariant,
    reorderDayItems,
    removeDayItem,
    resetPlan,
  }
})
