import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type TransportType = 'public' | 'drive' | 'mixed'
export type IntensityType = 'relaxed' | 'moderate' | 'high'

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

function buildMockDays(variant: 'culture' | 'nature' | 'relax', startDate: string): DayPlan[] {
  const start = new Date(startDate)
  const days: DayPlan[] = []
  const count = 3
  const culturePOIs = [
    () => mockPOI(1, '故宫博物院', ['文化', '历史'], 180),
    () => mockPOI(2, '国家博物馆', ['文化', '历史'], 120),
    () => mockPOI(3, '南锣鼓巷', ['文化', '美食'], 90),
    () => mockPOI(4, '颐和园', ['文化', '自然'], 150),
    () => mockPOI(5, '雍和宫', ['文化', '宗教'], 90),
  ]
  const naturePOIs = [
    () => mockPOI(10, '西湖', ['自然', '休闲'], 120),
    () => mockPOI(11, '灵隐寺', ['自然', '文化'], 90),
    () => mockPOI(12, '西溪湿地', ['自然', '生态'], 180),
    () => mockPOI(13, '九溪烟树', ['自然', '徒步'], 90),
    () => mockPOI(14, '龙井村', ['自然', '美食'], 120),
  ]
  const relaxPOIs = [
    () => mockPOI(20, '海边栈道', ['休闲', '自然'], 90),
    () => mockPOI(21, '温泉酒店', ['休闲', '放松'], 180),
    () => mockPOI(22, '古镇漫步', ['休闲', '文化'], 120),
    () => mockPOI(23, '咖啡馆', ['休闲', '美食'], 60),
    () => mockPOI(24, '夜市', ['美食', '购物'], 90),
  ]
  const pool = variant === 'culture' ? culturePOIs : variant === 'nature' ? naturePOIs : relaxPOIs
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
  const destinations = ref<string[]>(['北京', '杭州'])
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
    variants.value = [
      { id: 'a', name: '方案 A（文化优先）', days: buildMockDays('culture', start) },
      { id: 'b', name: '方案 B（自然优先）', days: buildMockDays('nature', start) },
      { id: 'c', name: '方案 C（轻松休闲）', days: buildMockDays('relax', start) },
    ]
    if (!activeVariantId.value) activeVariantId.value = 'a'
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

  return {
    routeName,
    startDate,
    endDate,
    peopleCount,
    destinations,
    totalBudget,
    transport,
    intensity,
    interestWeights,
    variants,
    activeVariantId,
    activeVariant,
    activeDays,
    budgetSummary,
    setRouteName,
    addDestination,
    removeDestination,
    generateItinerary,
    setActiveVariant,
    reorderDayItems,
    removeDayItem,
  }
})
