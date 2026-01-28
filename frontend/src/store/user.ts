import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export type CreditLevel = '铜牌' | '银牌' | '金牌' | '钻石'

export const useUserStore = defineStore('user', () => {
  // Mock: 未登录。设为 true 可模拟已登录
  const isLoggedIn = ref(false)
  const nickname = ref('旅行达人')
  const avatar = ref('') // 空则用昵称首字
  const creditLevel = ref<CreditLevel>('金牌')

  const displayName = computed(() => nickname.value || '旅人')
  const initial = computed(() => displayName.value.charAt(0).toUpperCase())

  function setLoggedIn(flag: boolean) {
    isLoggedIn.value = flag
  }

  function setMockUser(options: { nickname?: string; creditLevel?: CreditLevel }) {
    if (options.nickname != null) nickname.value = options.nickname
    if (options.creditLevel != null) creditLevel.value = options.creditLevel
  }

  return {
    isLoggedIn,
    nickname,
    avatar,
    creditLevel,
    displayName,
    initial,
    setLoggedIn,
    setMockUser,
  }
})
