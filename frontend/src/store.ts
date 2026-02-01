import { createPinia, defineStore } from 'pinia'
import { ref } from 'vue'

export const pinia = createPinia()

/** 信誉等级显示：后端为数字，前端展示用 */
export function reputationLevelLabel(level: number | null | undefined): string {
  if (level == null) return '铜牌'
  const map: Record<number, string> = { 1: '铜牌', 2: '银牌', 3: '金牌', 4: '钻石' }
  return map[level] ?? '铜牌'
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem('token'))
  const userId = ref<number | null>(localStorage.getItem('userId') ? Number(localStorage.getItem('userId')) : null)
  const nickname = ref<string | null>(localStorage.getItem('nickname'))
  const reputationLevel = ref<number | null>(localStorage.getItem('reputationLevel') ? Number(localStorage.getItem('reputationLevel')) : null)
  /** 本会话内已关注的用户 ID（用于动态页关注按钮状态，不持久化） */
  const followedSessionIds = ref<Set<number>>(new Set())

  function setAuth(t: string, uid: number) {
    token.value = t
    userId.value = uid
    localStorage.setItem('token', t)
    localStorage.setItem('userId', String(uid))
  }

  function setProfile(nick: string | null, level: number | null) {
    nickname.value = nick
    reputationLevel.value = level
    if (nick != null) localStorage.setItem('nickname', nick)
    else localStorage.removeItem('nickname')
    if (level != null) localStorage.setItem('reputationLevel', String(level))
    else localStorage.removeItem('reputationLevel')
  }

  function addFollowedSession(uid: number) {
    followedSessionIds.value = new Set([...followedSessionIds.value, uid])
  }

  function removeFollowedSession(uid: number) {
    const next = new Set(followedSessionIds.value)
    next.delete(uid)
    followedSessionIds.value = next
  }

  function clearAuth() {
    token.value = null
    userId.value = null
    nickname.value = null
    reputationLevel.value = null
    followedSessionIds.value = new Set()
    localStorage.removeItem('token')
    localStorage.removeItem('userId')
    localStorage.removeItem('nickname')
    localStorage.removeItem('reputationLevel')
  }

  return {
    token,
    userId,
    nickname,
    reputationLevel,
    followedSessionIds,
    setAuth,
    setProfile,
    addFollowedSession,
    removeFollowedSession,
    clearAuth,
  }
})

