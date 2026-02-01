import type { UnifiedDynamicItem } from '../api/types'
import { notesApi, feedsApi, companionApi, routesApi } from '../api'
import { useAuthStore } from '../store'

/**
 * 拉取游记、打卡、结伴（及可选路线）并合并为统一动态流。
 * 路线暂无公开列表接口时，登录用户可用「我的路线」占位。
 */
export async function fetchUnifiedDynamicItems(): Promise<UnifiedDynamicItem[]> {
  const auth = useAuthStore()
  const [notes, feeds, companions, myRoutes] = await Promise.all([
    notesApi.list().catch(() => []),
    feedsApi.list().catch(() => []),
    companionApi.listPosts().catch(() => []),
    auth.token ? routesApi.myPlans().catch(() => []) : Promise.resolve([]),
  ])

  const items: UnifiedDynamicItem[] = []

  notes.forEach((n) => {
    items.push({
      type: 'note',
      id: n.id,
      createdAt: n.createdAt || new Date().toISOString(),
      hotScore: (n.likeCount ?? 0) * 2 + (n.commentCount ?? 0),
      authorId: n.authorId,
      authorName: n.authorName,
      note: n,
    })
  })

  feeds.forEach((f) => {
    items.push({
      type: 'feed',
      id: f.id,
      createdAt: f.createdAt,
      authorId: f.authorId,
      authorName: f.authorName,
      feed: f,
    })
  })

  companions.forEach((c) => {
    items.push({
      type: 'companion',
      id: c.id,
      createdAt: c.startDate || new Date().toISOString(),
      authorId: c.creatorId,
      authorName: c.creatorNickname,
      authorAvatar: c.creatorAvatar,
      reputationLevel: c.creatorReputationLevel,
      companion: c,
    })
  })

  myRoutes.forEach((r) => {
    items.push({
      type: 'route',
      id: r.id,
      createdAt: r.startDate || new Date().toISOString(),
      authorId: auth.userId ?? undefined,
      route: r,
    })
  })

  return items.sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
  )
}
