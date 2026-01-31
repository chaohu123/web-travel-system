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
  AiGenerateRouteRequest,
  AiGenerateRouteResponse,
  RouteGenerateForm,
  CompanionPostSummary,
  CompanionPostDetail,
  PostChatMessageItem,
  MyTeamMessageItem,
  NoteSummary,
  FeedItem,
  CommentItem,
  UserPublicProfile,
  FollowingItem,
  FollowerItem,
  PageResult,
  InteractionSummary,
  InteractionMessageDTO,
  ConversationSummaryDTO,
  MessageOverview,
  ChatMessageItemDTO,
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
    // 未认证(401)或 Spring Security 默认返回的禁止(403)均视为需重新登录，统一跳转登录页
    const status = err.response?.status
    if (status === 401 || status === 403) {
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
  /** 发送私信 - POST /api/messages/chat/{peerUserId}，type=image 时 content 为图片 base64；type=spot 时需传 spotJson */
  sendChatMessage(
    peerUserId: number,
    content: string,
    type: 'text' | 'image' | 'spot' = 'text',
    spotJson?: string
  ) {
    return api
      .post<ApiResponse<ChatMessageItemDTO>>(`/messages/chat/${peerUserId}`, {
        content,
        type,
        spotJson: type === 'spot' ? spotJson : undefined,
      })
      .then(unwrap)
  },
  /** 获取与指定用户的私信消息列表 - GET /api/messages/chat/{peerUserId}/messages */
  getChatMessages(peerUserId: number) {
    return api
      .get<ApiResponse<ChatMessageItemDTO[]>>(`/messages/chat/${peerUserId}/messages`)
      .then(unwrap)
  },
  /** 进入与指定用户的聊天页时清空该会话未读数 - POST /api/messages/chat/{peerUserId}/clear-unread */
  clearChatUnread(peerUserId: number) {
    return api.post<ApiResponse<void>>(`/messages/chat/${peerUserId}/clear-unread`, {}).then(unwrap)
  },
  /** 删除会话（仅对自己隐藏，对方仍可见） */
  deleteConversation(conversationId: number) {
    return api.delete<ApiResponse<void>>(`/messages/conversations/${conversationId}`).then(unwrap)
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
  /** 我关注的人列表（用于邀请成员等） */
  myFollowing() {
    return api.get<ApiResponse<FollowingItem[]>>('/users/me/following').then(unwrap)
  },
  /** 我的粉丝列表（关注我的人） */
  myFollowers() {
    return api.get<ApiResponse<FollowerItem[]>>('/users/me/followers').then(unwrap)
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
  /** 热门路线（未登录可访问） */
  hot(limit: number = 4) {
    return api.get<ApiResponse<PlanResponse[]>>('/routes/hot', { params: { limit } }).then(unwrap)
  },
  getOne(id: number) {
    return api.get<ApiResponse<PlanResponse>>(`/routes/${id}`).then(unwrap)
  },
  delete(id: number) {
    return api.delete<ApiResponse<void>>(`/routes/${id}`).then(unwrap)
  },
  create(body: CreatePlanRequest) {
    return api.post<ApiResponse<number>>('/routes', body).then(unwrap)
  },
  /** AI 生成路线方案（不落库，返回多方案供前端展示） */
  aiGenerate(body: AiGenerateRouteRequest) {
    return api.post<ApiResponse<AiGenerateRouteResponse>>('/routes/ai-generate', body).then(unwrap)
  },
}

/** 从 RouteGenerateForm + 日期人数 构建 AI 生成请求体 */
export function buildAiGenerateRequest(
  form: RouteGenerateForm,
  startDate: string,
  endDate: string,
  peopleCount: number = 2
): AiGenerateRouteRequest {
  return {
    departureCity: form.startCity || undefined,
    destinations: form.destinations,
    startDate,
    endDate,
    totalBudget: form.budget,
    peopleCount,
    transport: form.transportType === 'car' ? 'drive' : form.transportType,
    intensity: form.pace === 'easy' ? 'relaxed' : form.pace === 'hard' ? 'high' : 'moderate',
    interestWeightsJson: JSON.stringify(form.interests),
  }
}

/** 结伴 */
export const companionApi = {
  /** 发布结伴帖，返回新帖子 id */
  publish(body: {
    destination: string
    startDate: string
    endDate: string
    minPeople?: number
    maxPeople?: number
    budgetMin?: number | null
    budgetMax?: number | null
    expectedMateDesc?: string | null
    visibility?: string
    relatedPlanId?: number | null
  }) {
    return api.post<ApiResponse<number>>('/companion/posts', body).then(unwrap)
  },
  /** 为结伴帖创建小队（发起人自动成为队长并加入），返回小队 id */
  createTeam(postId: number) {
    return api.post<ApiResponse<number>>('/companion/teams', undefined, { params: { postId } }).then(unwrap)
  },
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
  /** 删除结伴帖（仅创建者可删） */
  deletePost(id: number) {
    return api.delete<ApiResponse<void>>(`/companion/posts/${id}`).then(unwrap)
  },
  /** 小队详情 */
  getTeam(teamId: number) {
    return api.get<ApiResponse<import('./types').TeamDetail>>(`/companion/teams/${teamId}`).then(unwrap)
  },
  /** 退出小队 */
  quitTeam(teamId: number) {
    return api.post<ApiResponse<void>>(`/companion/teams/${teamId}/quit`, {}).then(unwrap)
  },
  /** 解散小队（仅队长） */
  dissolveTeam(teamId: number) {
    return api.post<ApiResponse<void>>(`/companion/teams/${teamId}/dissolve`, {}).then(unwrap)
  },
  /** 队长移除指定成员 */
  removeMember(teamId: number, userId: number) {
    return api.delete<ApiResponse<void>>(`/companion/teams/${teamId}/members/${userId}`).then(unwrap)
  },
  /** 队长将小队分享给指定用户，被分享人可查看该行程 */
  shareTeam(teamId: number, userId: number) {
    return api.post<ApiResponse<void>>(`/companion/teams/${teamId}/share`, {}, { params: { userId } }).then(unwrap)
  },
  /** 获取结伴帖内置沟通消息列表（任何人可读） */
  getPostChatMessages(postId: number) {
    return api.get<ApiResponse<PostChatMessageItem[]>>(`/companion/posts/${postId}/chat/messages`).then(unwrap)
  },
  /** 发送结伴帖内置沟通消息（需登录且为发起人或已加入小队成员）；可选 type=spot/image/route/companion 及对应 JSON */
  sendPostChatMessage(
    postId: number,
    content: string,
    options?: {
      type?: 'text' | 'spot' | 'image' | 'route' | 'companion'
      spotJson?: string
      routeJson?: string
      companionJson?: string
    }
  ) {
    return api
      .post<ApiResponse<PostChatMessageItem>>(`/companion/posts/${postId}/chat`, {
        content,
        type: options?.type,
        spotJson: options?.spotJson,
        routeJson: options?.routeJson,
        companionJson: options?.companionJson,
      })
      .then(unwrap)
  },
  /** 当前用户加入的小队及最近聊天预览（消息中心「小队消息」） */
  getMyTeamMessages() {
    return api.get<ApiResponse<MyTeamMessageItem[]>>('/companion/me/teams').then(unwrap)
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
  /** 游记详情页「相关景点推荐」，由后端根据关联路线自动生成 */
  getRelatedSpots(noteId: number) {
    return api.get<ApiResponse<import('./types').RelatedSpotItem[]>>(`/notes/${noteId}/related-spots`).then(unwrap)
  },
  update(id: number, body: { title: string; content: string; coverImage?: string; relatedPlanId?: number; destination?: string }) {
    return api.put<ApiResponse<void>>(`/notes/${id}`, body).then(unwrap)
  },
  delete(id: number) {
    return api.delete<ApiResponse<void>>(`/notes/${id}`).then(unwrap)
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
  delete(id: number) {
    return api.delete<ApiResponse<void>>(`/comments/${id}`).then(unwrap)
  },
}

/** 我的收藏项（与后端 /interactions/favorites 一致） */
export interface MyFavoriteItem {
  targetType: string
  targetId: number
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
  /** 当前用户收藏列表 */
  myFavorites() {
    return api.get<ApiResponse<MyFavoriteItem[]>>('/interactions/favorites').then(unwrap)
  },
  summary(targetType: string, targetId: number) {
    return api
      .get<ApiResponse<InteractionSummary>>('/interactions/summary', { params: { targetType, targetId } })
      .then(unwrap)
  },
}

export * from './types'
