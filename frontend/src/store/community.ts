import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { FeedItem } from '../api'
import type { NoteSummary } from '../api'

/** 动态类型标签 */
export const FEED_TYPE_TAGS = ['游记', '攻略', '经验分享'] as const

/** 旅行风格标签（话题筛选） */
export const TOPIC_TAGS = ['自然风光', '历史文化', '美食体验', '购物娱乐', '休闲放松'] as const

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

  function prependFeed(feed: FeedItem) {
    feeds.value = [feed, ...feeds.value]
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
    filteredFeeds,
    filteredNotes,
    setFeeds,
    setFeaturedNotes,
    setSelectedTopic,
    setSortOrder,
    setSearchKeyword,
    setRecommendedUsers,
    prependFeed,
    setFeedLoading,
    setNoteLoading,
  }
})
