import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { TeamDetail, TeamMemberItem, CompanionPostDetail, PlanResponse } from '../api/types'

/** 小队公告（前端展示用，后续可对接后端） */
export interface TeamAnnouncement {
  id: string
  content: string
  createdAt: string
  authorName?: string
}

/** 小队动态（成员加入/退出、行程更新等） */
export interface TeamDynamic {
  id: string
  type: 'join' | 'leave' | 'trip_update' | 'announcement'
  content: string
  createdAt: string
}

export const useTeamStore = defineStore('team', () => {
  const team = ref<TeamDetail | null>(null)
  const post = ref<CompanionPostDetail | null>(null)
  const plan = ref<PlanResponse | null>(null)
  const announcements = ref<TeamAnnouncement[]>([])
  const dynamics = ref<TeamDynamic[]>([])

  const currentMemberCount = computed(() => team.value?.members?.length ?? 0)
  const maxMemberCount = computed(() => team.value?.maxPeople ?? post.value?.maxPeople ?? 0)
  const isFull = computed(() => {
    const max = maxMemberCount.value
    if (!max) return false
    return currentMemberCount.value >= max
  })

  function setTeam(data: TeamDetail | null) {
    team.value = data
  }

  function setPost(data: CompanionPostDetail | null) {
    post.value = data
  }

  function setPlan(data: PlanResponse | null) {
    plan.value = data
  }

  function setAnnouncements(list: TeamAnnouncement[]) {
    announcements.value = list
  }

  function setDynamics(list: TeamDynamic[]) {
    dynamics.value = list
  }

  function reset() {
    team.value = null
    post.value = null
    plan.value = null
    announcements.value = []
    dynamics.value = []
  }

  /** 当前用户是否为队长 */
  function isLeader(userId: number | null): boolean {
    if (!userId || !team.value?.members) return false
    return team.value.members.some((m) => m.userId === userId && m.role === 'leader')
  }

  /** 当前用户是否为小队成员 */
  function isMember(userId: number | null): boolean {
    if (!userId || !team.value?.members) return false
    return team.value.members.some((m) => m.userId === userId)
  }

  return {
    team,
    post,
    plan,
    announcements,
    dynamics,
    currentMemberCount,
    maxMemberCount,
    isFull,
    setTeam,
    setPost,
    setPlan,
    setAnnouncements,
    setDynamics,
    reset,
    isLeader,
    isMember,
  }
})
