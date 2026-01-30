/** 后端统一包装 */
export interface ApiResponse<T = unknown> {
  code: number
  message: string
  data: T
}

/** 登录 */
export interface LoginRequest {
  username: string
  password: string
}
export interface LoginResponse {
  token: string
  userId: number
  username: string
}

/** 注册 */
export interface RegisterRequest {
  email?: string
  phone?: string
  password: string
}

/** 当前用户详情 */
export interface MeDetail {
  id: number
  email?: string
  phone?: string
  nickname?: string
  avatar?: string
  city?: string
  gender?: string
  age?: number
  intro?: string
  slogan?: string
  reputationScore?: number
  reputationLevel?: number
}

/** 公开个人主页信息（对外展示用） */
export interface UserPublicProfile {
  id: number
  nickname: string
  avatar?: string
  city?: string
  gender?: string
  age?: number
  intro?: string
  slogan?: string
  /** 个人主页封面图 */
  coverImage?: string
  /** 信誉分与等级，用于展示徽章与评分 */
  reputationScore?: number
  reputationLevel?: number
  /** 关注/粉丝相关 */
  followersCount?: number
  followingCount?: number
  isFollowed?: boolean
  /** 旅行偏好（只读展示） */
  preferences?: {
    travelStyles?: string[]
    interests?: string[]
    budgetRange?: string
    transportPreferences?: string[]
  }
  /** 关键统计数据 */
  stats?: {
    completedRoutes: number
    notesCount: number
    companionSuccessCount: number
    likedCount: number
    favoritedCount: number
  }
}

/** 更新资料请求 */
export interface UpdateProfileRequest {
  nickname?: string
  avatar?: string
  gender?: string
  age?: number
  city?: string
  intro?: string
  slogan?: string
}

/** 我关注的人（用于邀请成员等列表） */
export interface FollowingItem {
  userId: number
  nickname: string
  avatar?: string | null
}

/** 路线 */
export interface TripPlanDay {
  dayIndex: number
  date: string
  activities: TripPlanActivity[]
}
export interface TripPlanActivity {
  type?: string
  name?: string
  location?: string
  startTime?: string
  endTime?: string
  transport?: string
  estimatedCost?: number
  /** 景点/活动经度（后端返回真实坐标） */
  lng?: number
  /** 景点/活动纬度（后端返回真实坐标） */
  lat?: number
}
export interface PlanResponse {
  id: number
  title: string
  destination: string
  startDate: string
  endDate: string
  budget?: number
  peopleCount?: number
  pace?: string
  days?: TripPlanDay[]
  /** 该路线被使用/引用次数（用于个人主页统计展示，可选） */
  usedCount?: number
}
export interface CreatePlanRequest {
  destination: string
  startDate: string
  endDate: string
  budget?: number
  peopleCount: number
  pace: string
  preferenceWeightsJson?: string
}

/** 结伴 PostSummary */
export interface CompanionPostSummary {
  id: number
  destination: string
  startDate: string
  endDate: string
  minPeople?: number
  maxPeople?: number
  budgetMin?: number
  budgetMax?: number
  status?: string
  creatorId?: number
  creatorNickname?: string
  relatedPlanId?: number
  /** 结伴说明，后端可选返回 */
  expectedMateDesc?: string
  /** 发起人头像，后端可选返回 */
  creatorAvatar?: string
  /** 信誉等级 1–5，后端可选返回 */
  creatorReputationLevel?: number
  /** 创建者的标签（逗号分隔），后端可选返回 */
  creatorTags?: string
}

/** 结伴帖详情（含说明、关联小队 ID） */
export interface CompanionPostDetail extends CompanionPostSummary {
  expectedMateDesc?: string
  teamId?: number
}

/** 小队成员项（后端返回） */
export interface TeamMemberItem {
  userId: number
  userName: string
  avatar?: string | null
  reputationLevel?: number | null
  role: string
  state: string
}

/** 小队详情（后端返回） */
export interface TeamDetail {
  id: number
  name: string
  status: string
  postId: number | null
  destination: string | null
  startDate: string | null
  endDate: string | null
  relatedPlanId: number | null
  /** 结伴帖最大人数 */
  maxPeople?: number | null
  budgetMin?: number | null
  budgetMax?: number | null
  members: TeamMemberItem[]
}

/** 游记摘要 */
export interface NoteSummary {
  id: number
  title: string
  destination?: string
  coverImage?: string
  /** 作者用户 ID，用于判断是否可删除/编辑 */
  authorId?: number
  authorName?: string
  createdAt?: string
  /** 点赞数与评论数，用于个人主页卡片展示，可选 */
  likeCount?: number
  commentCount?: number
}

/** 游记详情页「相关景点推荐」单项，由后端根据关联路线自动生成 */
export interface RelatedSpotItem {
  id: string
  name: string
  location?: string
  type?: string
  timeRange?: string
  imageUrl?: string | null
  routeId?: number
}

/** 社区动态 */
export interface FeedItem {
  id: number
  content: string
  imageUrlsJson: string | null
  authorName: string
  createdAt: string
}

/** 评论项 */
export interface CommentItem {
  id: number
  userId?: number // 评论用户ID，用于跳转个人主页
  userName: string
  content: string
  score: number | null
  createdAt: string
  /** 评价标签，如「守时」「好沟通」等，可选 */
  tags?: string[]
}

/** 通用分页结果 */
export interface PageResult<T> {
  list: T[]
  total: number
  page: number
  pageSize: number
}

/** 点赞/收藏汇总 */
export interface InteractionSummary {
  likeCount: number
  favoriteCount: number
  likedByCurrentUser: boolean
  favoritedByCurrentUser: boolean
}

/** 消息 - 互动消息（对齐后端字段命名）
 * 注意：后端可能返回小写的 type 和 targetType，前端会在 store 中转换为大写
 */
export interface InteractionMessageDTO {
  id: number
  type: 'LIKE' | 'COMMENT' | 'like' | 'comment' // 兼容大小写
  fromUserId: number
  fromUserName: string
  fromUserAvatar?: string
  targetType: 'NOTE' | 'ROUTE' | 'note' | 'route' // 兼容大小写，后端通常使用小写
  targetId: number
  targetTitle: string
  contentPreview?: string // 评论内容预览
  createdAt: string // ISO 8601 格式字符串（LocalDateTime）
  read: boolean // 或 isRead，根据后端实现
}

/** 消息 - 私信会话摘要（对齐后端字段命名） */
export interface ConversationSummaryDTO {
  id: number
  peerUserId: number
  peerNickname: string
  peerAvatar?: string
  lastMessagePreview: string
  lastMessageTime: string // ISO 8601 格式字符串
  unreadCount: number
  pinned?: boolean
  /** 对方是否为当前用户的粉丝（对方关注了当前用户） */
  peerIsFollower?: boolean
}

/** 消息概览（顶部角标） */
export interface MessageOverview {
  totalUnread: number
}

/** 私信单条消息（聊天页拉取与展示） */
export interface ChatMessageItemDTO {
  id: number
  senderId: number
  content: string
  type: string
  createdAt: string
}

/** 社区动态流统一项：聚合游记、路线、打卡、结伴 */
export type DynamicItemType = 'note' | 'route' | 'companion' | 'feed'
export interface UnifiedDynamicItem {
  type: DynamicItemType
  id: number
  createdAt: string
  /** 用于排序的热度（点赞数等），可选 */
  hotScore?: number
  authorId?: number
  authorName?: string
  authorAvatar?: string
  reputationLevel?: number
  /** 游记摘要 */
  note?: NoteSummary
  /** 路线（后端暂无公开列表时为空） */
  route?: PlanResponse
  /** 结伴摘要 */
  companion?: CompanionPostSummary
  /** 打卡动态 */
  feed?: FeedItem
}