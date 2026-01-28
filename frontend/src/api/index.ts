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

/** 用户 */
export const userApi = {
  meDetail() {
    return api.get<ApiResponse<MeDetail>>('/users/me/detail').then(unwrap)
  },
  updateProfile(body: UpdateProfileRequest) {
    return api.put<ApiResponse<MeDetail>>('/users/me/profile', body).then(unwrap)
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
}

/** 游记 */
export const notesApi = {
  list() {
    return api.get<ApiResponse<NoteSummary[]>>('/notes').then(unwrap)
  },
  getOne(id: number) {
    return api.get<ApiResponse<{ id: number; title: string; content: string; coverImage?: string; authorName?: string; createdAt?: string }>>(`/notes/${id}`).then(unwrap)
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

export * from './types'
