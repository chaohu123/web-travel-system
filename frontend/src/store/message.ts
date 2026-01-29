import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { PageResult, InteractionMessageDTO, ConversationSummaryDTO } from '../api'
import { messageApi } from '../api'

export interface InteractionMessage {
  id: number
  type: 'LIKE' | 'COMMENT' // 前端内部使用大写，与后端交互时可能需要转换
  fromUserId: number
  fromUserName: string
  fromUserAvatar?: string
  targetType: 'NOTE' | 'ROUTE' // 前端内部使用大写，与后端交互时可能需要转换
  targetId: number
  targetTitle: string
  contentPreview?: string
  createdAt: string
  read: boolean
}

export interface PrivateConversation {
  id: number
  peerUserId: number
  peerNickname: string
  peerAvatar?: string
  lastMessagePreview: string
  lastMessageTime: string
  unreadCount: number
  pinned?: boolean
}

export type InteractionCategory = 'all' | 'like' | 'comment'

export const useMessageStore = defineStore('message', () => {
  /** 顶部全局未读数 */
  const totalUnread = ref(0)

  /** 互动消息列表 */
  const interactionMessages = ref<InteractionMessage[]>([])
  const interactionPage = ref(1)
  const interactionPageSize = ref(10)
  const interactionTotal = ref(0)
  const interactionLoading = ref(false)

  /** 私信会话列表 */
  const conversations = ref<PrivateConversation[]>([])
  const convoPage = ref(1)
  const convoPageSize = ref(10)
  const convoTotal = ref(0)
  const convoLoading = ref(false)

  const interactionUnreadCount = computed(() => interactionMessages.value.filter((m) => !m.read).length)
  const privateUnreadCount = computed(() =>
    conversations.value.reduce((sum, c) => sum + (c.unreadCount || 0), 0)
  )

  function setTotalUnread(count: number) {
    totalUnread.value = count
  }

  async function fetchOverview() {
    const data = await messageApi.overview()
    totalUnread.value = data.totalUnread
  }

  async function fetchInteractionMessages(category: InteractionCategory = 'all') {
    interactionLoading.value = true
    try {
      const res: PageResult<InteractionMessageDTO> = await messageApi.interactionList({
        page: interactionPage.value,
        pageSize: interactionPageSize.value,
        category,
      })
      // 转换后端返回的数据格式：统一转换为大写（前端内部使用）
      const converted: InteractionMessage[] = res.list.map((item) => ({
        ...item,
        type: (typeof item.type === 'string' ? item.type.toUpperCase() : item.type) as 'LIKE' | 'COMMENT',
        targetType: (typeof item.targetType === 'string' ? item.targetType.toUpperCase() : item.targetType) as 'NOTE' | 'ROUTE',
      }))
      interactionMessages.value =
        interactionPage.value === 1 ? converted : [...interactionMessages.value, ...converted]
      interactionTotal.value = res.total
      totalUnread.value = converted.filter((m) => !m.read).length + privateUnreadCount.value
    } finally {
      interactionLoading.value = false
    }
  }

  async function fetchConversations() {
    convoLoading.value = true
    try {
      const res: PageResult<ConversationSummaryDTO> = await messageApi.conversationList({
        page: convoPage.value,
        pageSize: convoPageSize.value,
      })
      // 直接使用后端返回的数据（字段已对齐）
      const converted: PrivateConversation[] = res.list.map((item) => ({
        ...item,
      }))
      conversations.value = convoPage.value === 1 ? converted : [...conversations.value, ...converted]
      convoTotal.value = res.total
      totalUnread.value = interactionUnreadCount.value + privateUnreadCount.value
    } finally {
      convoLoading.value = false
    }
  }

  async function markInteractionRead(id: number) {
    await messageApi.markInteractionRead(id)
    const msg = interactionMessages.value.find((m) => m.id === id)
    if (msg && !msg.read) {
      msg.read = true
      if (totalUnread.value > 0) totalUnread.value--
    }
  }

  async function markAllInteractionRead() {
    await messageApi.markAllInteractionRead()
    interactionMessages.value.forEach((m) => {
      m.read = true
    })
    totalUnread.value = privateUnreadCount.value
  }

  async function clearConversationUnread(conversationId: number) {
    await messageApi.clearConversationUnread(conversationId)
    const convo = conversations.value.find((c) => c.id === conversationId)
    if (convo) {
      if (convo.unreadCount && totalUnread.value > 0) {
        totalUnread.value = Math.max(0, totalUnread.value - convo.unreadCount)
      }
      convo.unreadCount = 0
    }
  }

  return {
    // state
    totalUnread,
    interactionMessages,
    interactionPage,
    interactionPageSize,
    interactionTotal,
    interactionLoading,
    conversations,
    convoPage,
    convoPageSize,
    convoTotal,
    convoLoading,
    // getters
    interactionUnreadCount,
    privateUnreadCount,
    // actions
    setTotalUnread,
    fetchOverview,
    fetchInteractionMessages,
    fetchConversations,
    markInteractionRead,
    markAllInteractionRead,
    clearConversationUnread,
  }
}
)

