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
  creatorNickname?: string
  relatedPlanId?: number
  /** 结伴说明，后端可选返回 */
  expectedMateDesc?: string
  /** 发起人头像，后端可选返回 */
  creatorAvatar?: string
  /** 信誉等级 1–5，后端可选返回 */
  creatorReputationLevel?: number
}

/** 结伴帖详情（含说明、关联小队 ID） */
export interface CompanionPostDetail extends CompanionPostSummary {
  expectedMateDesc?: string
  teamId?: number
}

/** 游记摘要 */
export interface NoteSummary {
  id: number
  title: string
  destination?: string
  coverImage?: string
  authorName?: string
  createdAt?: string
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
  userName: string
  content: string
  score: number | null
  createdAt: string
}
