<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import RouteCard from '../components/RouteCard.vue'
import BuddyCard from '../components/BuddyCard.vue'
import CommunityCard from '../components/CommunityCard.vue'
import { useAuthStore, reputationLevelLabel } from '../store'
import { userApi, routesApi, companionApi, notesApi, feedsApi, interactionsApi } from '../api'
import type { PlanResponse, CompanionPostSummary, NoteSummary } from '../api'

const router = useRouter()
const auth = useAuthStore()

const pace = ref('medium')
const interests = ref<string[]>([])

const routeList = ref<Array<{
  id: number
  cover: string
  title: string
  tags: string[]
  days: number
  budgetMin: number
  budgetMax: number
}>>([])
const buddyList = ref<Array<{
  postId: number
  userId?: number
  avatar: string
  nickname: string
  creditLevel: string
  destination: string
  time: string
  styles: string[]
}>>([])
const communityList = ref<
  Array<{
    id: number
    cover: string
    title: string
    authorAvatar: string
    authorName: string
    likes: number
    comments: number
    /** 内容类型：游记 / 路线 / 结伴 / 动态 */
    type: 'note' | 'route' | 'companion' | 'feed'
    /** 对应内容 ID，用于跳转 */
    targetId: number
  }>
>([])

const bannerList = ref<
  Array<{
    id: number
    title: string
    subtitle: string
    image: string
    link: string
  }>
>([
  {
    id: 1,
    title: '樱花季 · 京都奈良人文深度游',
    subtitle: '漫步古都与花海，感受和风文化',
    image: 'https://picsum.photos/seed/banner1/1200/420',
    link: '/routes',
  },
  {
    id: 2,
    title: '海岛躺平 · 普吉/苏梅 6 日轻奢度假',
    subtitle: '碧海蓝天与泳池别墅，零压力放空之旅',
    image: 'https://picsum.photos/seed/banner2/1200/420',
    link: '/routes',
  },
  {
    id: 3,
    title: '周末城市微旅行 · 热门目的地低成本玩法',
    subtitle: '短途也能很精彩，为你推荐 2～3 日路线',
    image: 'https://picsum.photos/seed/banner3/1200/420',
    link: '/community',
  },
])

const hotSpots = ref<
  Array<{
    id: number
    name: string
    city: string
    image: string
    score: number
  }>
>([
  { id: 1, name: '西湖', city: '杭州', image: 'https://picsum.photos/seed/spot1/320/200', score: 4.9 },
  { id: 2, name: '鼓浪屿', city: '厦门', image: 'https://picsum.photos/seed/spot2/320/200', score: 4.8 },
  { id: 3, name: '丽江古城', city: '丽江', image: 'https://picsum.photos/seed/spot3/320/200', score: 4.7 },
  { id: 4, name: '九份老街', city: '新北', image: 'https://picsum.photos/seed/spot4/320/200', score: 4.6 },
])

const routesLoading = ref(false)
const buddyLoading = ref(false)
const communityLoading = ref(false)
const submitLoading = ref(false)

const MOCK_ROUTES = [
  { id: 1, cover: 'https://picsum.photos/seed/route1/400/250', title: '京都奈良文化深度 5 日游', tags: ['文化', '历史'], days: 5, budgetMin: 8000, budgetMax: 15000 },
  { id: 2, cover: 'https://picsum.photos/seed/route2/400/250', title: '云南大理丽江休闲 6 日', tags: ['自然', '休闲'], days: 6, budgetMin: 4000, budgetMax: 8000 },
  { id: 3, cover: 'https://picsum.photos/seed/route3/400/250', title: '厦门鼓浪屿美食 3 日', tags: ['美食', '海岛'], days: 3, budgetMin: 2000, budgetMax: 4000 },
]
const MOCK_BUDDIES = [
  // mock 数据不保证与数据库用户ID一致，因此不提供 userId，避免跳转到错误个人主页
  { postId: 1, userId: undefined, avatar: '', nickname: '小鹿', creditLevel: '金牌', destination: '北海道', time: '2 月初 · 约 7 天', styles: ['摄影', '美食'] },
  { postId: 2, userId: undefined, avatar: '', nickname: '行者老张', creditLevel: '钻石', destination: '新疆', time: '7 月 · 约 10 天', styles: ['自驾', '风光'] },
  { postId: 3, userId: undefined, avatar: '', nickname: '桃桃', creditLevel: '银牌', destination: '泰国清迈', time: '3 月 ·约 5 天', styles: ['休闲', '夜市'] },
]
const MOCK_COMMUNITY: Array<{
  id: number
  cover: string
  title: string
  authorAvatar: string
  authorName: string
  likes: number
  comments: number
  type: 'note' | 'route' | 'companion' | 'feed'
  targetId: number
}> = [
  {
    id: 1,
    cover: 'https://picsum.photos/seed/comm1/400/250',
    title: '一个人去冰岛：环岛自驾与极光攻略',
    authorAvatar: '',
    authorName: '北极星',
    likes: 1203,
    comments: 89,
    type: 'note',
    targetId: 1,
  },
  {
    id: 2,
    cover: 'https://picsum.photos/seed/comm2/400/250',
    title: '东京 5 日暴走打卡清单（含机酒预算）',
    authorAvatar: '',
    authorName: '关东煮',
    likes: 856,
    comments: 42,
    type: 'route',
    targetId: 2,
  },
  {
    id: 3,
    cover: 'https://picsum.photos/seed/comm3/400/250',
    title: '大理洱海边的慢生活：住宿与拍照机位',
    authorAvatar: '',
    authorName: '苍山雪',
    likes: 634,
    comments: 31,
    type: 'companion',
    targetId: 3,
  },
]

function daysBetween(start: string, end: string): number {
  if (!start || !end) return 0
  const a = new Date(start).getTime()
  const b = new Date(end).getTime()
  return Math.max(1, Math.round((b - a) / (24 * 3600 * 1000)) + 1)
}

function mapPlanToCard(p: PlanResponse) {
  const days = daysBetween(p.startDate, p.endDate)
  const budget = p.budget ?? 0
  return {
    id: p.id,
    cover: 'https://picsum.photos/seed/route' + p.id + '/400/250',
    title: p.title || `${p.destination} ${days} 日`,
    tags: p.pace ? [p.pace === 'fast' ? '暴走' : p.pace === 'slow' ? '悠闲' : '适中'] : [],
    days,
    budgetMin: budget,
    budgetMax: budget || 5000,
  }
}

function mapPostToBuddy(p: CompanionPostSummary) {
  const start = p.startDate ? new Date(p.startDate).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }) : ''
  const end = p.endDate ? new Date(p.endDate).toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' }) : ''
  const time = start && end ? `${start} - ${end}` : '待定'
  
  // 解析创建者标签
  const styles: string[] = []
  if (p.creatorTags) {
    const tags = p.creatorTags.split(',').map(t => t.trim()).filter(Boolean)
    styles.push(...tags)
  }
  // 如果没有标签，至少显示目的地
  if (styles.length === 0 && p.destination) {
    styles.push(p.destination)
  }
  
  return {
    postId: p.id,
    userId: p.creatorId, // 创建者ID，用于跳转个人主页和私信
    avatar: p.creatorAvatar || '',
    nickname: p.creatorNickname || '旅友',
    creditLevel: reputationLevelLabel(p.creatorReputationLevel ?? null),
    destination: p.destination,
    time,
    styles,
  }
}

function mapNoteToCommunity(n: NoteSummary) {
  return {
    id: n.id,
    cover: n.coverImage || 'https://picsum.photos/seed/note' + n.id + '/400/250',
    title: n.title,
    authorAvatar: '',
    authorName: n.authorName || '用户',
    likes: n.likeCount ?? 0,
    comments: n.commentCount ?? 0,
    type: 'note' as const,
    targetId: n.id,
  }
}

async function loadUserDetail() {
  if (!auth.token) return
  try {
    const me = await userApi.meDetail()
    auth.setProfile(me.nickname ?? null, me.reputationLevel ?? null)
  } catch {
    // ignore
  }
}

async function loadRoutes() {
  // 路线推荐需要登录，未登录时不显示
  if (!auth.token) {
    routeList.value = []
    return
  }
  routesLoading.value = true
  try {
    const list = await routesApi.myPlans()
    routeList.value = list.map(mapPlanToCard)
  } catch {
    routeList.value = []
  } finally {
    routesLoading.value = false
  }
}

async function loadBuddies() {
  buddyLoading.value = true
  try {
    // 优先使用智能推荐接口；失败或返回空时回退为公开列表前 3 条
    let list: CompanionPostSummary[] = []
    try {
      const raw = await companionApi.recommend(3)
      list = Array.isArray(raw) ? raw : []
    } catch {
      // recommend 失败（如未登录被拒、网络错误）时回退为公开结伴列表
      try {
        const raw = await companionApi.listPosts({})
        list = Array.isArray(raw) ? raw.slice(0, 3) : []
      } catch {
        list = []
      }
    }
    buddyList.value = list.map(mapPostToBuddy)
  } catch {
    buddyList.value = []
  } finally {
    buddyLoading.value = false
  }
}

async function loadCommunity() {
  communityLoading.value = true
  try {
    // 同时加载游记和动态（都支持未登录访问）
    const [notes, feeds] = await Promise.all([
      notesApi.list().catch(() => []),
      feedsApi.list().catch(() => []),
    ])
    
    // 处理游记：获取点赞数和评论数
    const enrichedNotes = await Promise.all(
      notes.map(async (n) => {
        try {
          const summary = await interactionsApi.summary('note', n.id)
          return { ...n, likeCount: summary.likeCount, commentCount: n.commentCount ?? 0 }
        } catch {
          return { ...n, likeCount: n.likeCount ?? 0, commentCount: n.commentCount ?? 0 }
        }
      }),
    )
    
    // 处理动态：获取点赞数和评论数
    const enrichedFeeds = await Promise.all(
      feeds.map(async (f) => {
        try {
          const summary = await interactionsApi.summary('feed', f.id)
          return {
            id: f.id,
            cover: f.imageUrlsJson ? JSON.parse(f.imageUrlsJson)[0] : undefined,
            title: f.content.length > 50 ? f.content.substring(0, 50) + '...' : f.content,
            authorAvatar: '',
            authorName: f.authorName || '用户',
            likes: summary.likeCount,
            comments: 0, // 动态暂时没有评论数
            type: 'feed' as const,
            targetId: f.id,
          }
        } catch {
          return {
            id: f.id,
            cover: f.imageUrlsJson ? JSON.parse(f.imageUrlsJson)[0] : undefined,
            title: f.content.length > 50 ? f.content.substring(0, 50) + '...' : f.content,
            authorAvatar: '',
            authorName: f.authorName || '用户',
            likes: 0,
            comments: 0,
            type: 'feed' as const,
            targetId: f.id,
          }
        }
      }),
    )
    
    // 合并游记和动态，按时间排序（最新的在前）
    const allItems = [
      ...enrichedNotes.map(mapNoteToCommunity),
      ...enrichedFeeds,
    ].sort((a, b) => {
      // 简单排序：游记优先，然后按ID倒序（假设ID越大越新）
      if (a.type !== b.type) {
        return a.type === 'note' ? -1 : 1
      }
      return b.id - a.id
    })
    
    // 取前6条作为首页展示
    communityList.value = allItems.slice(0, 6)
  } catch {
    communityList.value = []
  } finally {
    communityLoading.value = false
  }
}

async function onSearchSubmit(payload: { destination: string; startDate: string; endDate: string; travelers: number }) {
  if (!auth.token) {
    router.push('/login?redirect=' + encodeURIComponent('/'))
    return
  }
  if (!payload.destination?.trim()) {
    alert('请填写目的地')
    return
  }
  if (!payload.startDate || !payload.endDate) {
    alert('请选择出发与结束日期')
    return
  }
  submitLoading.value = true
  try {
    await routesApi.create({
      destination: payload.destination.trim(),
      startDate: payload.startDate,
      endDate: payload.endDate,
      peopleCount: payload.travelers,
      pace: pace.value,
      preferenceWeightsJson: interests.value.length ? JSON.stringify(interests.value) : undefined,
    })
    await loadRoutes()
    alert('路线已创建')
    // 可跳转到详情: router.push('/routes/' + planId)
  } catch (e: any) {
    alert(e.response?.data?.message || e.message || '创建失败')
  } finally {
    submitLoading.value = false
  }
}

function onCommunityClick(_payload: { title: string; likes: number; comments: number }) {
  // 具体跳转逻辑已经在 CommunityCard 内部根据 type + targetId 统一处理
}

onMounted(() => {
  loadUserDetail()
  loadRoutes()
  loadBuddies()
  loadCommunity()
})
</script>

<template>
  <div class="min-h-screen bg-slate-50/80">
    <main>
      <!-- 轮播 Banner -->
      <section class="banner-section">
        <el-carousel height="380px" indicator-position="outside" :interval="6000" trigger="click">
          <el-carousel-item
            v-for="b in bannerList"
            :key="b.id"
            @click="$router.push(b.link)"
          >
            <div class="banner-slide" :style="{ backgroundImage: `url(${b.image})` }">
              <div class="banner-mask">
                <div class="banner-content">
                  <h1 class="banner-title">{{ b.title }}</h1>
                  <p class="banner-subtitle">{{ b.subtitle }}</p>
                  <div class="banner-actions">
                    <el-button type="primary" size="large">查看推荐路线</el-button>
                    <el-button size="large" @click.stop="$router.push('/routes/create')">立即规划行程</el-button>
                  </div>
                  <div class="banner-stats">
                    <div class="stat-item">
                      <div class="stat-number">1,200+</div>
                      <div class="stat-label">智能生成路线</div>
                    </div>
                    <div class="stat-item">
                      <div class="stat-number">800+</div>
                      <div class="stat-label">真实结伴活动</div>
                    </div>
                    <div class="stat-item">
                      <div class="stat-number">4.9</div>
                      <div class="stat-label">出行满意度评分</div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </el-carousel-item>
        </el-carousel>
      </section>

      <!-- 为你推荐：路线 / 游记 -->
      <section v-if="auth.token" class="py-14 max-w-6xl mx-auto px-4 sm:px-6">
        <h2 class="home-section-title">为你推荐</h2>
        <p class="home-section-subtitle">根据你的偏好，推荐适合的路线与游记</p>
        <div v-if="routesLoading" class="text-slate-500 py-8">加载中...</div>
        <div v-else-if="routeList.length === 0" class="text-slate-500 py-8 text-center">
          <p>还没有创建过路线，<router-link to="/routes/create" class="text-indigo-600 hover:underline">立即规划你的第一条路线</router-link></p>
        </div>
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
          <RouteCard
            v-for="r in routeList"
            :key="r.id"
            :cover="r.cover"
            :title="r.title"
            :tags="r.tags"
            :days="r.days"
            :budget-min="r.budgetMin"
            :budget-max="r.budgetMax"
            :route-id="r.id"
          />
        </div>
      </section>

      <!-- 热门景点与目的地 -->
      <section class="py-14 bg-white border-y border-slate-100">
        <div class="max-w-6xl mx-auto px-4 sm:px-6">
          <h2 class="home-section-title">热门景点与目的地</h2>
          <p class="home-section-subtitle">当前最受欢迎的旅行地，一键获取灵感</p>
          <div class="hotspot-scroll">
            <el-card
              v-for="s in hotSpots"
              :key="s.id"
              shadow="hover"
              class="hotspot-card"
              @click="
                $router.push({
                  name: 'spot-detail',
                  params: { id: s.id },
                  query: { name: s.name, city: s.city },
                })
              "
            >
              <div class="hotspot-cover">
                <img :src="s.image" :alt="s.name" loading="lazy" />
              </div>
              <div class="hotspot-body">
                <h4>{{ s.name }}</h4>
                <p class="text-xs text-slate-500 mt-0.5">{{ s.city }}</p>
                <div class="flex items-center justify-between mt-2">
                  <el-tag size="small" type="warning" effect="plain">热度 {{ s.score.toFixed(1) }}</el-tag>
                  <el-button text size="small" type="primary">查看景点</el-button>
                </div>
              </div>
            </el-card>
          </div>
        </div>
      </section>

      <!-- 结伴活动推荐 -->
      <section class="py-14 bg-white">
      <div class="max-w-6xl mx-auto px-4 sm:px-6">
        <h2 class="home-section-title">结伴活动推荐</h2>
        <p class="home-section-subtitle">找到和你同路的旅友，一起出发</p>
        <div v-if="buddyLoading" class="text-slate-500 py-8">加载中...</div>
        <div v-else-if="buddyList.length === 0" class="text-slate-500 py-8 text-center">
          <p>暂无推荐的结伴活动</p>
        </div>
        <div v-else class="grid grid-cols-1 md:grid-cols-3 gap-6">
          <BuddyCard
            v-for="b in buddyList"
            :key="b.postId"
            :avatar="b.avatar"
            :nickname="b.nickname"
            :credit-level="b.creditLevel"
            :destination="b.destination"
            :time="b.time"
            :styles="b.styles"
            :user-id="b.userId"
            :post-id="b.postId"
          />
        </div>
      </div>
    </section>

      <!-- 社区动态模块 -->
      <section class="py-14 max-w-6xl mx-auto px-4 sm:px-6">
        <h2 class="home-section-title">社区动态</h2>
        <p class="home-section-subtitle">看看旅友们最近在分享什么</p>
        <div v-if="communityLoading" class="text-slate-500 py-8">加载中...</div>
        <div v-else-if="communityList.length === 0" class="text-slate-500 py-8 text-center">
          <p>暂无社区动态</p>
        </div>
        <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          <CommunityCard
            v-for="c in communityList"
            :key="c.id"
            :cover="c.cover"
            :title="c.title"
            :author-avatar="c.authorAvatar"
            :author-name="c.authorName"
            :likes="c.likes"
            :comments="c.comments"
            :type="c.type"
            :target-id="c.targetId"
            @click="onCommunityClick"
          />
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
@reference "tailwindcss/utilities";

.banner-section {
  max-width: 1200px;
  margin: 16px auto 0;
  padding: 0 16px;
}

.banner-slide {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  border-radius: 24px;
  overflow: hidden;
  position: relative;
}

.banner-mask {
  position: absolute;
  inset: 0;
  background: linear-gradient(120deg, rgba(15, 23, 42, 0.88), rgba(15, 23, 42, 0.3) 50%, transparent 100%);
  display: flex;
  align-items: center;
  padding-left: 40px;
}

.banner-content {
  max-width: 520px;
  color: #e5e7eb;
}

.banner-title {
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 8px;
}

.banner-subtitle {
  font-size: 14px;
  color: #cbd5f5;
  margin-bottom: 18px;
}

.banner-actions {
  display: flex;
  gap: 12px;
}

.banner-stats {
  margin-top: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  font-size: 12px;
  color: #cbd5f5;
}

.stat-item {
  min-width: 90px;
}

.stat-number {
  font-size: 18px;
  font-weight: 700;
  color: #e5e7eb;
}

.stat-label {
  opacity: 0.9;
}

.home-section-title {
  font-size: 1.5rem; /* text-2xl */
  font-weight: 700;  /* font-bold */
  color: #1f2937;    /* text-slate-800 */
  margin-bottom: 0.5rem; /* mb-2 */
}

.home-section-subtitle {
  color: #475569;       /* text-slate-600 */
  margin-bottom: 2rem;  /* mb-8 */
  font-size: 0.875rem;  /* text-sm */
}

.hotspot-scroll {
  display: flex;
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 8px;
}

.hotspot-card {
  min-width: 220px;
  border-radius: 16px;
  cursor: pointer;
}

.hotspot-cover img {
  width: 100%;
  height: 140px;
  object-fit: cover;
  border-radius: 12px;
}

.hotspot-body {
  margin-top: 8px;
}

.hotspot-body h4 {
  font-size: 14px;
  font-weight: 600;
  color: #1e293b;
}
</style>
