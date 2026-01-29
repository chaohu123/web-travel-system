import axios from 'axios'
import type { AxiosInstance } from 'axios'
import { useAuthStore } from '../store'
import type {
  ApiResponse,
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  MeDetail,
  UpdateProfileRequest,
  PlanResponse,
  CreatePlanRequest,
  CompanionPostSummary,
  CompanionPostDetail,
  NoteSummary,
  FeedItem,
  CommentItem,
  UserPublicProfile,
  PageResult,
  InteractionSummary,
  InteractionMessageDTO,
  ConversationSummaryDTO,
  MessageOverview,
} from './types'

export const api: AxiosInstance = axios.create({
  baseURL: '/api',
})

api.interceptors.request.use((config) => {
  const auth = useAuthStore()
  if (auth.token) {
    config.headers = config.headers ?? {}
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

api.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      const auth = useAuthStore()
      auth.clearAuth()
      if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
        const redirect = encodeURIComponent(window.location.pathname + window.location.search)
        window.location.href = `/login?redirect=${redirect}`
      }
    }
    return Promise.reject(err)
  }
)

function unwrap<T>(res: { data: ApiResponse<T> }): T {
  const { code, message, data } = res.data
  if (code !== 0) throw new Error(message || '请求失败')
  return data as T
}

/** 认证 */
export const authApi = {
  login(body: LoginRequest) {
    return api.post<ApiResponse<LoginResponse>>('/auth/login', body).then(unwrap)
  },
  register(body: RegisterRequest) {
    return api.post<ApiResponse<null>>('/auth/register', body).then(unwrap)
  },
}

/** 消息中心（对齐后端接口风格） */
export const messageApi = {
  /** 顶部概览（未读数）- GET /api/messages/overview */
  overview() {
    return api.get<ApiResponse<MessageOverview>>('/messages/overview').then(unwrap)
  },
  /** 互动消息列表 - GET /api/messages/interactions?page=1&pageSize=10&category=all */
  interactionList(params: { page: number; pageSize: number; category?: 'all' | 'like' | 'comment' }) {
    return api
      .get<ApiResponse<PageResult<InteractionMessageDTO>>>('/messages/interactions', { params })
      .then(unwrap)
  },
  /** 私信会话列表 - GET /api/messages/conversations?page=1&pageSize=10 */
  conversationList(params: { page: number; pageSize: number }) {
    return api
      .get<ApiResponse<PageResult<ConversationSummaryDTO>>>('/messages/conversations', { params })
      .then(unwrap)
  },
  /** 标记单条互动消息已读 - POST /api/messages/interactions/{id}/read */
  markInteractionRead(id: number) {
    return api.post<ApiResponse<void>>(`/messages/interactions/${id}/read`, {}).then(unwrap)
  },
  /** 全部互动消息标记已读 - POST /api/messages/interactions/read-all */
  markAllInteractionRead() {
    return api.post<ApiResponse<void>>('/messages/interactions/read-all', {}).then(unwrap)
  },
  /** 清空某个会话未读数（进入聊天页）- POST /api/messages/conversations/{id}/clear-unread */
  clearConversationUnread(conversationId: number) {
    return api.post<ApiResponse<void>>(`/messages/conversations/${conversationId}/clear-unread`, {}).then(unwrap)
  },
}

/** 用户 */
export const userApi = {
  meDetail() {
    return api.get<ApiResponse<MeDetail>>('/users/me/detail').then(unwrap)
  },
  updateProfile(body: UpdateProfileRequest) {
    return api.put<ApiResponse<MeDetail>>('/users/me/profile', body).then(unwrap)
  },
  /** 对外展示的个人主页信息 */
  getPublicProfile(userId: number) {
    return api.get<ApiResponse<UserPublicProfile>>(`/users/${userId}/homepage`).then(unwrap)
  },
  /** 关注某个用户 */
  follow(userId: number) {
    return api.post<ApiResponse<void>>(`/users/${userId}/follow`, {}).then(unwrap)
  },
  /** 取消关注某个用户 */
  unfollow(userId: number) {
    return api.post<ApiResponse<void>>(`/users/${userId}/unfollow`, {}).then(unwrap)
  },
  /** 该用户发布的游记列表 */
  userNotes(userId: number) {
    return api.get<ApiResponse<NoteSummary[]>>(`/users/${userId}/notes`).then(unwrap)
  },
  /** 该用户创建的路线列表 */
  userRoutes(userId: number) {
    return api.get<ApiResponse<PlanResponse[]>>(`/users/${userId}/routes`).then(unwrap)
  },
  /** 该用户发布的结伴活动 */
  userCompanions(userId: number) {
    return api.get<ApiResponse<CompanionPostSummary[]>>(`/users/${userId}/companion`).then(unwrap)
  },
  /** 其他人对该用户的评价（分页） */
  userReviews(userId: number, page: number, pageSize: number) {
    return api
      .get<ApiResponse<PageResult<CommentItem>>>(`/users/${userId}/reviews`, {
        params: { page, pageSize },
      })
      .then(unwrap)
  },
}

/** 路线 */
export const routesApi = {
  myPlans() {
    return api.get<ApiResponse<PlanResponse[]>>('/routes/my').then(unwrap)
  },
  getOne(id: number) {
    return api.get<ApiResponse<PlanResponse>>(`/routes/${id}`).then(unwrap)
  },
  create(body: CreatePlanRequest) {
    return api.post<ApiResponse<number>>('/routes', body).then(unwrap)
  },
}

/** 结伴 */
export const companionApi = {
  listPosts(params?: { destination?: string; startDate?: string; endDate?: string }) {
    return api.get<ApiResponse<CompanionPostSummary[]>>('/companion/posts', { params }).then(unwrap)
  },
  myPosts() {
    return api.get<ApiResponse<CompanionPostSummary[]>>('/companion/posts/my').then(unwrap)
  },
  getPost(id: number) {
    return api.get<ApiResponse<CompanionPostDetail>>(`/companion/posts/${id}`).then(unwrap)
  },
  /** 智能推荐结伴活动（根据登录用户标签或热度） */
  recommend(limit: number = 3) {
    return api.get<ApiResponse<CompanionPostSummary[]>>('/companion/posts/recommend', { params: { limit } }).then(unwrap)
  },
}

/** 游记 */
export const notesApi = {
  list() {
    return api.get<ApiResponse<NoteSummary[]>>('/notes').then(unwrap)
  },
  getOne(id: number) {
    return api.get<ApiResponse<{
      id: number
      title: string
      content: string
      coverImage?: string
      relatedPlanId?: number
      destination?: string
      authorId?: number
      authorName?: string
      createdAt?: string
    }>>(`/notes/${id}`).then(unwrap)
  },
  update(id: number, body: { title: string; content: string; coverImage?: string; relatedPlanId?: number; destination?: string }) {
    return api.put<ApiResponse<void>>(`/notes/${id}`, body).then(unwrap)
  },
}

/** 社区动态 */
export const feedsApi = {
  list() {
    return api.get<ApiResponse<FeedItem[]>>('/feeds').then(unwrap)
  },
  create(body: { content: string; imageUrlsJson?: string }) {
    return api.post<ApiResponse<number>>('/feeds', body).then(unwrap)
  },
}

/** 评论 */
export const commentsApi = {
  list(targetType: string, targetId: number) {
    return api.get<ApiResponse<CommentItem[]>>('/comments', { params: { targetType, targetId } }).then(unwrap)
  },
  create(body: { targetType: string; targetId: number; content: string; score?: number }) {
    return api.post<ApiResponse<void>>('/comments', body).then(unwrap)
  },
}

/** 点赞 & 收藏 */
export const interactionsApi = {
  like(targetType: string, targetId: number) {
    return api.post<ApiResponse<void>>('/interactions/likes', { targetType, targetId }).then(unwrap)
  },
  unlike(targetType: string, targetId: number) {
    return api.delete<ApiResponse<void>>('/interactions/likes', { params: { targetType, targetId } }).then(unwrap)
  },
  favorite(targetType: string, targetId: number) {
    return api.post<ApiResponse<void>>('/interactions/favorites', { targetType, targetId }).then(unwrap)
  },
  unfavorite(targetType: string, targetId: number) {
    return api
      .delete<ApiResponse<void>>('/interactions/favorites', { params: { targetType, targetId } })
      .then(unwrap)
  },
  summary(targetType: string, targetId: number) {
    return api
      .get<ApiResponse<InteractionSummary>>('/interactions/summary', { params: { targetType, targetId } })
      .then(unwrap)
  },
}

export * from './types'
