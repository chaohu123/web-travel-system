import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type ChatMessageType = 'text' | 'image' | 'route' | 'companion' | 'spot' | 'system'

export interface ChatRoutePayload {
  id: number
  title: string
  days: number
  destination: string
}

export interface ChatCompanionPayload {
  id: number
  destination: string
  startDate: string
  endDate: string
}

/** 景点卡片 payload（与 spotJson 一致） */
export interface ChatSpotPayload {
  routeId: number
  dayIndex: number
  activityIndex: number
  name: string
  location?: string
  /** 独立收藏的景点 ID，有值时点击跳转景点详情 */
  spotId?: number
  /** 景点封面图 URL */
  imageUrl?: string
  /** 经度（查看位置用） */
  lng?: number
  /** 纬度（查看位置用） */
  lat?: number
}

export interface ChatMessage {
  id: number
  from: 'me' | 'other' | 'system'
  type: ChatMessageType
  content: string
  createdAt: string
  routePayload?: ChatRoutePayload
  companionPayload?: ChatCompanionPayload
  spotPayload?: ChatSpotPayload
  imageUrl?: string
  failed?: boolean
}

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<Record<string, ChatMessage[]>>({})

  function ensureSession(sessionId: string): ChatMessage[] {
    const existing = sessions.value[sessionId]
    if (existing) return existing
    const created: ChatMessage[] = []
    sessions.value[sessionId] = created
    return created
  }

  function nowISO() {
    return new Date().toISOString()
  }

  function getMessages(sessionId: string) {
    return ensureSession(sessionId)
  }

  /**
   * 从后端拉取的消息列表覆盖当前会话（用于进入聊天页 / 发送后刷新）
   * items 为 API 返回的 ChatMessageItemDTO[]，currentUserId 用于区分 me/other
   */
  function setMessagesFromApi(
    sessionId: string,
    items: { id: number; senderId: number; content: string; type: string; spotJson?: string | null; createdAt: string | number[] }[],
    currentUserId: number
  ) {
    const toISO = (v: string | number[]): string => {
      if (typeof v === 'string') return v
      if (Array.isArray(v) && v.length >= 6) {
        const [y, mo, d, h = 0, min = 0, s = 0] = v
        return new Date(y, (mo as number) - 1, d as number, h as number, min as number, s as number).toISOString()
      }
      return new Date().toISOString()
    }
    const list: ChatMessage[] = items.map((m) => {
      const type = (m.type === 'text' || m.type === 'image' || m.type === 'route' || m.type === 'companion' || m.type === 'spot' ? m.type : 'text') as ChatMessageType
      let spotPayload: ChatSpotPayload | undefined
      if (type === 'spot' && m.spotJson) {
        try {
          spotPayload = JSON.parse(m.spotJson) as ChatSpotPayload
        } catch {
          // ignore
        }
      }
      return {
        id: m.id,
        from: m.senderId === currentUserId ? ('me' as const) : ('other' as const),
        type,
        content: m.content,
        createdAt: toISO(m.createdAt),
        imageUrl: type === 'image' && m.content ? m.content : undefined,
        spotPayload,
      }
    })
    sessions.value[sessionId] = list
  }

  function appendMessage(sessionId: string, msg: Omit<ChatMessage, 'id' | 'createdAt'> & { id?: number; createdAt?: string }) {
    const list = ensureSession(sessionId)
    const full: ChatMessage = {
      id: msg.id ?? Date.now(),
      createdAt: msg.createdAt ?? nowISO(),
      ...msg,
    }
    list.push(full)
    return full
  }

  function addTextMessage(sessionId: string, from: 'me' | 'other', content: string) {
    return appendMessage(sessionId, { from, type: 'text', content })
  }

  function addRouteCard(sessionId: string, from: 'me' | 'other', payload: ChatRoutePayload) {
    return appendMessage(sessionId, {
      from,
      type: 'route',
      content: payload.title,
      routePayload: payload,
    })
  }

  function addCompanionCard(sessionId: string, from: 'me' | 'other', payload: ChatCompanionPayload) {
    return appendMessage(sessionId, {
      from,
      type: 'companion',
      content: payload.destination,
      companionPayload: payload,
    })
  }

  function addSpotCard(sessionId: string, from: 'me' | 'other', payload: ChatSpotPayload) {
    return appendMessage(sessionId, {
      from,
      type: 'spot',
      content: `分享了一个景点：${payload.name}`,
      spotPayload: payload,
    })
  }

  function addImageMessage(sessionId: string, from: 'me' | 'other', imageUrl: string) {
    return appendMessage(sessionId, {
      from,
      type: 'image',
      content: '[图片]',
      imageUrl,
    })
  }

  function addSystemTip(sessionId: string, content: string) {
    return appendMessage(sessionId, { from: 'system', type: 'system', content })
  }

  function markFailed(sessionId: string, id: number) {
    const list = getMessages(sessionId)
    const target = list.find((m) => m.id === id)
    if (target) target.failed = true
  }

  const lastMessageMap = computed<Record<string, ChatMessage | null>>(() => {
    const result: Record<string, ChatMessage | null> = {}
    for (const [k, list] of Object.entries(sessions.value)) {
      result[k] = list.length ? list[list.length - 1] : null
    }
    return result
  })

  return {
    sessions,
    lastMessageMap,
    getMessages,
    setMessagesFromApi,
    appendMessage,
    addTextMessage,
    addRouteCard,
    addCompanionCard,
    addSpotCard,
    addImageMessage,
    addSystemTip,
    markFailed,
  }
})

