import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { userApi } from '../api'
import type {
  UserPublicProfile,
  NoteSummary,
  PlanResponse,
  CompanionPostSummary,
  CommentItem,
  PageResult,
} from '../api'
import { useAuthStore, reputationLevelLabel } from '../store'

export type UserProfileTab = 'notes' | 'routes' | 'companion' | 'reviews'

export const useUserProfileStore = defineStore('user-profile', () => {
  const profile = ref<UserPublicProfile | null>(null)
  const notes = ref<NoteSummary[]>([])
  const routes = ref<PlanResponse[]>([])
  const companions = ref<CompanionPostSummary[]>([])
  const reviews = ref<CommentItem[]>([])

  const reviewsTotal = ref(0)
  const reviewsPage = ref(1)
  const reviewsPageSize = ref(5)

  const loadingProfile = ref(false)
  const loadingTab = ref(false)
  const followLoading = ref(false)

  const currentTab = ref<UserProfileTab>('notes')

  const auth = useAuthStore()

  const isSelf = computed(() => {
    if (!auth.userId || !profile.value) return false
    return auth.userId === profile.value.id
  })

  const reputationScoreDisplay = computed(() => profile.value?.reputationScore ?? 0)

  const reputationLevelText = computed(() =>
    reputationLevelLabel(profile.value?.reputationLevel ?? null),
  )

  async function fetchProfile(userId: number) {
    if (!userId) return
    loadingProfile.value = true
    try {
      profile.value = await userApi.getPublicProfile(userId)
    } finally {
      loadingProfile.value = false
    }
  }

  async function fetchTabData(userId: number, tab: UserProfileTab) {
    if (!userId) return
    currentTab.value = tab
    loadingTab.value = true
    try {
      if (tab === 'notes') {
        notes.value = await userApi.userNotes(userId)
      } else if (tab === 'routes') {
        routes.value = await userApi.userRoutes(userId)
      } else if (tab === 'companion') {
        companions.value = await userApi.userCompanions(userId)
      } else if (tab === 'reviews') {
        await fetchReviews(userId, reviewsPage.value, reviewsPageSize.value)
      }
    } finally {
      loadingTab.value = false
    }
  }

  async function fetchReviews(userId: number, page: number, pageSize: number) {
    const res: PageResult<CommentItem> = await userApi.userReviews(userId, page, pageSize)
    reviews.value = res.list
    reviewsTotal.value = res.total
    reviewsPage.value = res.page
    reviewsPageSize.value = res.pageSize
  }

  async function changeReviewsPage(userId: number, page: number) {
    if (!userId) return
    await fetchReviews(userId, page, reviewsPageSize.value)
  }

  async function toggleFollow(userId: number) {
    if (!profile.value || !userId) return
    if (!auth.token) {
      // 未登录时交由路由守卫/页面逻辑处理
      return
    }
    followLoading.value = true
    try {
      if (profile.value.isFollowed) {
        await userApi.unfollow(userId)
        profile.value.isFollowed = false
        if (profile.value.followersCount != null) {
          profile.value.followersCount = Math.max(0, profile.value.followersCount - 1)
        }
      } else {
        await userApi.follow(userId)
        profile.value.isFollowed = true
        if (profile.value.followersCount != null) {
          profile.value.followersCount += 1
        }
      }
    } finally {
      followLoading.value = false
    }
  }

  return {
    profile,
    notes,
    routes,
    companions,
    reviews,
    reviewsTotal,
    reviewsPage,
    reviewsPageSize,
    loadingProfile,
    loadingTab,
    followLoading,
    currentTab,
    isSelf,
    reputationScoreDisplay,
    reputationLevelText,
    fetchProfile,
    fetchTabData,
    changeReviewsPage,
    toggleFollow,
  }
})

