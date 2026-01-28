import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { MeDetail } from '../api'
import type { PlanResponse } from '../api'
import type { CompanionPostSummary } from '../api'
import type { FeedItem } from '../api'
import type { NoteSummary } from '../api'

export type ProfileTab =
  | 'info'
  | 'routes'
  | 'companion'
  | 'favorites'
  | 'feeds'
  | 'settings'
  | 'security'

export interface FavoriteItem {
  type: 'route' | 'note' | 'user'
  id: number
  title?: string
  destination?: string
  coverImage?: string
  authorName?: string
  nickname?: string
}

export const useProfileStore = defineStore('profile', () => {
  const me = ref<MeDetail | null>(null)
  const myRoutes = ref<PlanResponse[]>([])
  const myCompanion = ref<CompanionPostSummary[]>([])
  const myFeeds = ref<FeedItem[]>([])
  const favorites = ref<FavoriteItem[]>([])
  const loading = ref(false)

  const reputationLevelLabel = computed(() => {
    const level = me.value?.reputationLevel
    if (level == null) return '铜牌'
    const map: Record<number, string> = { 1: '铜牌', 2: '银牌', 3: '金牌', 4: '钻石' }
    return map[level] ?? '铜牌'
  })

  const reputationProgress = computed(() => {
    const score = me.value?.reputationScore ?? 0
    const level = me.value?.reputationLevel ?? 1
    const thresholds = [0, 100, 300, 600, 1000]
    const current = Math.min(level, 4)
    const base = thresholds[current]
    const nextVal = thresholds[Math.min(current + 1, 4)]
    if (current >= 4 || nextVal == null || nextVal === base) return 100
    return Math.min(100, Math.max(0, ((score - base) / (nextVal - base)) * 100))
  })

  function setMe(detail: MeDetail | null) {
    me.value = detail
  }

  function setMyRoutes(list: PlanResponse[]) {
    myRoutes.value = list
  }

  function setMyCompanion(list: CompanionPostSummary[]) {
    myCompanion.value = list
  }

  function setMyFeeds(list: FeedItem[]) {
    myFeeds.value = list
  }

  function setFavorites(list: FavoriteItem[]) {
    favorites.value = list
  }

  function removeFavorite(type: FavoriteItem['type'], id: number) {
    favorites.value = favorites.value.filter((f) => !(f.type === type && f.id === id))
  }

  function removeFeed(id: number) {
    myFeeds.value = myFeeds.value.filter((f) => f.id !== id)
  }

  return {
    me,
    myRoutes,
    myCompanion,
    myFeeds,
    favorites,
    loading,
    reputationLevelLabel,
    reputationProgress,
    setMe,
    setMyRoutes,
    setMyCompanion,
    setMyFeeds,
    setFavorites,
    removeFavorite,
    removeFeed,
  }
})
