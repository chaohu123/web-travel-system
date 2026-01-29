import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type ChatMessageType = 'text' | 'image' | 'route' | 'companion' | 'system'

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

export interface ChatMessage {
  id: number
  from: 'me' | 'other' | 'system'
  type: ChatMessageType
  content: string
  createdAt: string
  routePayload?: ChatRoutePayload
  companionPayload?: ChatCompanionPayload
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
    appendMessage,
    addTextMessage,
    addRouteCard,
    addCompanionCard,
    addImageMessage,
    addSystemTip,
    markFailed,
  }
})

