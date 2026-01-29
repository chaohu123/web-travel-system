import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { FeedItem } from '../api'
import type { NoteSummary } from '../api'
import type { UnifiedDynamicItem, DynamicItemType } from '../api/types'

/** 动态类型标签 */
export const FEED_TYPE_TAGS = ['游记', '攻略', '经验分享'] as const

/** 旅行风格标签（话题筛选） */
export const TOPIC_TAGS = ['自然风光', '历史文化', '美食体验', '购物娱乐', '休闲放松'] as const

/** 内容分类 Tab：全部、关注、游记、路线、打卡、结伴 */
export type CategoryTab = 'all' | 'following' | 'note' | 'route' | 'checkin' | 'companion'

export const CATEGORY_TABS: { value: CategoryTab; label: string }[] = [
  { value: 'all', label: '全部' },
  { value: 'following', label: '关注' },
  { value: 'note', label: '游记' },
  { value: 'route', label: '路线' },
  { value: 'checkin', label: '打卡' },
  { value: 'companion', label: '结伴' },
]

export interface RecommendedUser {
  id: number
  nickname: string
  avatar: string
  creditLevel: string
  tags: string[]
}

export const useCommunityStore = defineStore('community', () => {
  const feeds = ref<FeedItem[]>([])
  const featuredNotes = ref<NoteSummary[]>([])
  const selectedTopic = ref<string | null>(null)
  const sortOrder = ref<'latest' | 'hot'>('latest')
  const searchKeyword = ref('')
  const recommendedUsers = ref<RecommendedUser[]>([])
  const loading = ref(false)
  const feedLoading = ref(false)
  const noteLoading = ref(false)

  /** 动态页分类 Tab */
  const categoryTab = ref<CategoryTab>('all')
  /** 统一动态流（游记+路线+打卡+结伴聚合） */
  const dynamicItems = ref<UnifiedDynamicItem[]>([])
  /** 动态流加载中 */
  const dynamicLoading = ref(false)

  const filteredFeeds = computed(() => {
    let list = [...feeds.value]
    if (selectedTopic.value) {
      list = list.filter(() => true)
    }
    if (searchKeyword.value.trim()) {
      const kw = searchKeyword.value.toLowerCase()
      list = list.filter(
        (f) =>
          f.content.toLowerCase().includes(kw) ||
          (f.authorName && f.authorName.toLowerCase().includes(kw))
      )
    }
    if (sortOrder.value === 'latest') {
      list = [...list].sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      )
    }
    return list
  })

  const filteredNotes = computed(() => {
    let list = [...featuredNotes.value]
    if (searchKeyword.value.trim()) {
      const kw = searchKeyword.value.toLowerCase()
      list = list.filter(
        (n) =>
          (n.title && n.title.toLowerCase().includes(kw)) ||
          (n.destination && n.destination.toLowerCase().includes(kw)) ||
          (n.authorName && n.authorName.toLowerCase().includes(kw))
      )
    }
    return list
  })

  /** 按分类 Tab 与排序过滤后的动态流 */
  const filteredDynamicItems = computed(() => {
    let list = [...dynamicItems.value]
    const tab = categoryTab.value
    if (tab !== 'all' && tab !== 'following') {
      const typeMap: Record<Exclude<CategoryTab, 'all' | 'following'>, DynamicItemType> = {
        note: 'note',
        route: 'route',
        checkin: 'feed',
        companion: 'companion',
      }
      const type = typeMap[tab]
      list = list.filter((item) => item.type === type)
    }
    if (tab === 'following') {
      // 关注流：后端未提供时保持原列表，可由上层传入「已关注用户 ID 列表」再筛
      list = list
    }
    if (sortOrder.value === 'hot') {
      list = [...list].sort((a, b) => (b.hotScore ?? 0) - (a.hotScore ?? 0))
    } else {
      list = [...list].sort(
        (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      )
    }
    return list
  })

  function setFeeds(list: FeedItem[]) {
    feeds.value = list
  }

  function setFeaturedNotes(list: NoteSummary[]) {
    featuredNotes.value = list
  }

  function setSelectedTopic(tag: string | null) {
    selectedTopic.value = tag
  }

  function setSortOrder(order: 'latest' | 'hot') {
    sortOrder.value = order
  }

  function setSearchKeyword(kw: string) {
    searchKeyword.value = kw
  }

  function setRecommendedUsers(users: RecommendedUser[]) {
    recommendedUsers.value = users
  }

  function setCategoryTab(tab: CategoryTab) {
    categoryTab.value = tab
  }

  function setDynamicItems(items: UnifiedDynamicItem[]) {
    dynamicItems.value = items
  }

  function setDynamicLoading(v: boolean) {
    dynamicLoading.value = v
  }

  function prependFeed(feed: FeedItem) {
    feeds.value = [feed, ...feeds.value]
    const item: UnifiedDynamicItem = {
      type: 'feed',
      id: feed.id,
      createdAt: feed.createdAt,
      authorName: feed.authorName,
      feed,
    }
    dynamicItems.value = [item, ...dynamicItems.value]
  }

  function setFeedLoading(v: boolean) {
    feedLoading.value = v
  }

  function setNoteLoading(v: boolean) {
    noteLoading.value = v
  }

  return {
    feeds,
    featuredNotes,
    selectedTopic,
    sortOrder,
    searchKeyword,
    recommendedUsers,
    loading,
    feedLoading,
    noteLoading,
    categoryTab,
    dynamicItems,
    dynamicLoading,
    filteredFeeds,
    filteredNotes,
    filteredDynamicItems,
    setFeeds,
    setFeaturedNotes,
    setSelectedTopic,
    setSortOrder,
    setSearchKeyword,
    setRecommendedUsers,
    setCategoryTab,
    setDynamicItems,
    setDynamicLoading,
    prependFeed,
    setFeedLoading,
    setNoteLoading,
  }
})
