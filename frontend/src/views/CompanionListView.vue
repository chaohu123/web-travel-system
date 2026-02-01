<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElPagination } from 'element-plus'
import { useAuthStore } from '../store'
import { api, companionApi } from '../api'
import type { CompanionPostSummary } from '../api'

const auth = useAuthStore()

/** 行程天数选项 */
const DAY_OPTIONS = [
  { label: '3–5天', min: 3, max: 5 },
  { label: '5–7天', min: 5, max: 7 },
  { label: '7+天', min: 7, max: 999 },
]

/** 旅行风格选项 */
const STYLE_OPTIONS = ['自然', '文化', '美食', '休闲', '打卡']

const route = useRoute()
const router = useRouter()

// 是否只显示「我的结伴」
const isMyList = computed(() => route.query.my === '1')

// 筛选表单
const destination = ref('')
const dateRange = ref<[string, string] | null>(null)
const dayRange = ref<number[]>([]) // 选中的天数区间索引 0,1,2
const travelStyles = ref<string[]>([])
const budgetRange = ref<[number, number]>([0, 20000])
const ageRange = ref<[number, number]>([18, 65])
const genderPrefer = ref<'all' | 'male' | 'female'>('all')
const minReputation = ref(0)

const loading = ref(false)
const posts = ref<CompanionPostSummary[]>([])
const appliedPostIds = ref<Set<number>>(new Set())

/** 分页配置 */
const pageSize = ref(6)
const companionPage = ref(1)

/** 热门目的地（占位，可后续从接口拉取） */
const hotDestinations = ['北京', '上海', '成都', '西安', '杭州', '厦门', '云南', '西藏']

// 请求列表：我的结伴 vs 广场列表（未登录可浏览广场，不可看「我的结伴」）
async function fetchList() {
  if (isMyList.value && !auth.token) {
    router.replace({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  loading.value = true
  try {
    if (isMyList.value) {
      posts.value = await companionApi.myPosts()
    } else {
      const [start, end] = dateRange.value || [null, null]
      posts.value = await companionApi.listPosts({
        destination: destination.value.trim() || undefined,
        startDate: start || undefined,
        endDate: end || undefined,
      })
    }
  } catch {
    posts.value = []
  } finally {
    loading.value = false
  }
}

// 客户端二次筛选：目的地、行程天数、旅行风格、预算、年龄、性别、信誉（目的地与后端一致，此处兜底）
const filteredPosts = computed(() => {
  let list = [...posts.value]
  if (isMyList.value) return list

  const destTrim = destination.value.trim()
  if (destTrim) {
    list = list.filter((p) => (p.destination || '').includes(destTrim))
  }

  const [budgetMin, budgetMax] = budgetRange.value
  const [ageMin, ageMax] = ageRange.value

  return list.filter((p) => {
    const days = getTripDays(p)
    if (dayRange.value.length > 0) {
      const match = dayRange.value.some((idx) => {
        const opt = DAY_OPTIONS[idx]
        return opt && days >= opt.min && days <= opt.max
      })
      if (!match) return false
    }
    const pBudgetMin = p.budgetMin ?? 0
    const pBudgetMax = p.budgetMax ?? 999999
    if (pBudgetMax < budgetMin || pBudgetMin > budgetMax) return false
    return true
  })
})

/** 当前页要展示的列表（分页切片） */
const paginatedPosts = computed(() => {
  const list = filteredPosts.value
  const start = (companionPage.value - 1) * pageSize.value
  return list.slice(start, start + pageSize.value)
})

const companionTotal = computed(() => filteredPosts.value.length)

function onCompanionPageChange(p: number) {
  companionPage.value = p
}

function getTripDays(p: CompanionPostSummary): number {
  if (!p.startDate || !p.endDate) return 0
  const a = new Date(p.startDate)
  const b = new Date(p.endDate)
  return Math.max(1, Math.ceil((b.getTime() - a.getTime()) / (24 * 60 * 60 * 1000)) + 1)
}

function applyFilters() {
  fetchList()
}

function resetFilters() {
  destination.value = ''
  dateRange.value = null
  dayRange.value = []
  travelStyles.value = []
  budgetRange.value = [0, 20000]
  ageRange.value = [18, 65]
  genderPrefer.value = 'all'
  minReputation.value = 0
  fetchList()
}

function toggleDay(idx: number) {
  const i = dayRange.value.indexOf(idx)
  if (i === -1) dayRange.value = [...dayRange.value, idx]
  else dayRange.value = dayRange.value.filter((x) => x !== idx)
}

function toggleStyle(s: string) {
  const i = travelStyles.value.indexOf(s)
  if (i === -1) travelStyles.value = [...travelStyles.value, s]
  else travelStyles.value = travelStyles.value.filter((x) => x !== s)
}

function selectHotDest(dest: string) {
  destination.value = dest
  if (!isMyList.value) fetchList()
}

// 申请结伴：需登录；创建小队并加入，并标记已申请
async function applyCompanion(post: CompanionPostSummary) {
  if (appliedPostIds.value.has(post.id)) return
  if (!auth.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    ElMessage.warning('请先登录后再申请结伴')
    return
  }
  try {
    const res = await api.post<{ data: { code: number; data: number } }>('/companion/teams', null, { params: { postId: post.id } })
    const teamId = res.data.data
    await api.post(`/companion/teams/${teamId}/join`)
    appliedPostIds.value = new Set([...appliedPostIds.value, post.id])
    router.push(`/teams/${teamId}`)
  } catch (e: any) {
    const msg = e.response?.data?.message || '申请失败'
    ElMessage.error(msg)
  }
}

function viewDetail(post: CompanionPostSummary) {
  router.push(`/companion/${post.id}`)
}

// 展示用：信誉等级文案
function reputationLabel(level?: number) {
  if (level == null) return '普通'
  if (level >= 4) return '优质'
  if (level >= 3) return '良好'
  if (level >= 2) return '一般'
  return '普通'
}

// 展示用：每条帖子的标签（后端暂无，用预算/人数等生成简单标签）
function postTags(p: CompanionPostSummary): string[] {
  const tags: string[] = []
  const days = getTripDays(p)
  if (days >= 7) tags.push('长线')
  else if (days <= 3) tags.push('短途')
  if (p.budgetMax != null && p.budgetMax < 3000) tags.push('穷游')
  if (p.budgetMin != null && p.budgetMin >= 5000) tags.push('品质')
  if (!tags.length) tags.push('自由行')
  return tags
}

watch(isMyList, () => { companionPage.value = 1; fetchList() }, { immediate: false })
watch([destination, dateRange, dayRange, travelStyles, budgetRange, posts], () => { companionPage.value = 1 })

/** 目的地或日期变化时自动请求列表（目的地防抖，避免输入时频繁请求） */
let destinationFetchTimer: ReturnType<typeof setTimeout> | null = null
watch(destination, () => {
  if (isMyList.value) return
  if (destinationFetchTimer) clearTimeout(destinationFetchTimer)
  destinationFetchTimer = setTimeout(() => {
    destinationFetchTimer = null
    fetchList()
  }, 400)
})
watch(dateRange, () => {
  if (isMyList.value) return
  fetchList()
})

watch(pageSize, () => { companionPage.value = 1 })
onMounted(fetchList)
</script>

<template>
  <div class="companion-page" :class="{ 'companion-page--with-sidebar': !isMyList }">
    <!-- 1. 顶部导航与引导 -->
    <header class="page-header">
      <div class="header-inner">
        <div class="header-text">
          <h1 class="page-title">结伴广场</h1>
          <p class="page-subtitle">找到和你同路的人，一起出发</p>
        </div>
        <div class="header-actions">
          <ElButton type="primary" size="large" class="btn-publish" @click="router.push('/companion/create')">
            发布结伴
          </ElButton>
          <ElButton size="large" :type="isMyList ? 'primary' : 'default'" @click="router.replace(isMyList ? '/companion' : '/companion?my=1')">
            我的结伴
          </ElButton>
        </div>
      </div>
    </header>

    <div class="page-body" :class="{ 'page-body--full': isMyList, 'page-body--with-sidebar': !isMyList }">
      <!-- 2. 左侧筛选栏：固定定位，不随右侧内容变化而移动 -->
      <aside v-if="!isMyList" class="filter-sidebar">
        <div class="filter-card">
          <h3 class="filter-title">筛选条件</h3>

          <div class="filter-block">
            <label class="filter-label">目的地</label>
            <ElInput
              v-model="destination"
              placeholder="输入城市或国家"
              clearable
              class="filter-input"
            />
            <div class="hot-tags">
              <span
                v-for="d in hotDestinations"
                :key="d"
                class="hot-tag"
                :class="{ active: destination === d }"
                @click="selectHotDest(d)"
              >
                {{ d }}
              </span>
            </div>
          </div>

          <div class="filter-block">
            <label class="filter-label">出发时间</label>
            <ElDatePicker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              class="filter-date-range"
            />
          </div>

          <div class="filter-block">
            <label class="filter-label">行程天数</label>
            <div class="tag-group">
              <span
                v-for="(opt, idx) in DAY_OPTIONS"
                :key="opt.label"
                class="filter-tag"
                :class="{ active: dayRange.includes(idx) }"
                @click="toggleDay(idx)"
              >
                {{ opt.label }}
              </span>
            </div>
          </div>

          <div class="filter-block">
            <label class="filter-label">旅行风格</label>
            <div class="tag-group">
              <span
                v-for="s in STYLE_OPTIONS"
                :key="s"
                class="filter-tag"
                :class="{ active: travelStyles.includes(s) }"
                @click="toggleStyle(s)"
              >
                {{ s }}
              </span>
            </div>
          </div>

          <div class="filter-block">
            <label class="filter-label">预算区间（元）</label>
            <ElSlider
              v-model="budgetRange"
              range
              :min="0"
              :max="20000"
              :step="500"
              show-input
              class="filter-slider"
            />
          </div>

          <div class="filter-block">
            <label class="filter-label">旅友条件</label>
            <div class="sub-label">年龄范围</div>
            <ElSlider
              v-model="ageRange"
              range
              :min="18"
              :max="65"
              :step="1"
              show-input
              class="filter-slider"
            />
            <div class="sub-label">性别偏好</div>
            <div class="tag-group">
              <span class="filter-tag" :class="{ active: genderPrefer === 'all' }" @click="genderPrefer = 'all'">不限</span>
              <span class="filter-tag" :class="{ active: genderPrefer === 'male' }" @click="genderPrefer = 'male'">男</span>
              <span class="filter-tag" :class="{ active: genderPrefer === 'female' }" @click="genderPrefer = 'female'">女</span>
            </div>
            <div class="sub-label">信誉等级不低于</div>
            <ElSlider v-model="minReputation" :min="0" :max="5" :step="1" show-input class="filter-slider" />
          </div>

          <div class="filter-actions">
            <ElButton type="primary" class="apply-btn" @click="applyFilters" :loading="loading">
              应用筛选
            </ElButton>
            <ElButton @click="resetFilters">重置</ElButton>
          </div>
        </div>
      </aside>

      <!-- 3. 右侧卡片流 -->
      <main class="card-stream">
        <!-- 加载：骨架屏（同三列网格） -->
        <template v-if="loading">
          <div class="card-list-grid">
            <div v-for="i in 6" :key="i" class="companion-card skeleton-card">
              <div class="skeleton-avatar" />
              <div class="skeleton-line long" />
              <div class="skeleton-line short" />
              <div class="skeleton-chips" />
              <div class="skeleton-desc" />
              <div class="skeleton-btns" />
            </div>
          </div>
        </template>

        <!-- 空状态 -->
        <div v-else-if="filteredPosts.length === 0" class="empty-wrap">
          <ElEmpty description="暂无符合条件的结伴" class="empty-box">
            <template #image>
              <div class="empty-illustration">🧳</div>
            </template>
            <template #description>
              <p class="empty-desc">暂无符合条件的结伴，建议调整筛选条件或发布一条结伴信息～</p>
            </template>
          </ElEmpty>
        </div>

        <!-- 卡片列表：一行 3 个均匀分布 -->
        <template v-else>
          <div class="card-list-grid">
            <article
              v-for="post in paginatedPosts"
              :key="post.id"
              class="companion-card"
            >
              <div class="card-top">
                <div class="user-cell">
                  <div class="avatar-wrap">
                    <img
                      v-if="post.creatorAvatar"
                      :src="post.creatorAvatar"
                      :alt="post.creatorNickname"
                      class="avatar"
                    />
                    <span v-else class="avatar-placeholder">{{ (post.creatorNickname || '旅').charAt(0) }}</span>
                  </div>
                  <div class="user-meta">
                    <span class="nickname">{{ post.creatorNickname || '旅友' }}</span>
                    <ElTag size="small" type="warning" effect="plain" class="reputation-badge">
                      {{ reputationLabel(post.creatorReputationLevel) }}
                    </ElTag>
                  </div>
                </div>
              </div>

              <div class="card-trip">
                <h3 class="trip-dest">{{ post.destination }}</h3>
                <p class="trip-date">
                  {{ post.startDate }} – {{ post.endDate }}
                  <span class="trip-days">{{ getTripDays(post) }} 天</span>
                </p>
              </div>

              <div class="card-tags">
                <span v-for="tag in postTags(post)" :key="tag" class="trip-tag">{{ tag }}</span>
              </div>

              <p class="card-desc">{{ (post.expectedMateDesc || '一起结伴，开心出行～').slice(0, 60) }}{{ (post.expectedMateDesc && post.expectedMateDesc.length > 60) ? '…' : '' }}</p>

              <div class="card-actions">
                <ElButton size="default" @click="viewDetail(post)">
                  查看详情
                </ElButton>
                <ElButton
                  type="primary"
                  size="default"
                  :disabled="appliedPostIds.has(post.id)"
                  @click="applyCompanion(post)"
                >
                  {{ appliedPostIds.has(post.id) ? '已申请' : '申请结伴' }}
                </ElButton>
              </div>
            </article>
          </div>
          <!-- 分页组件 -->
          <div v-if="companionTotal > 0" class="pagination-wrap">
            <el-pagination
              v-model:current-page="companionPage"
              v-model:page-size="pageSize"
              :total="companionTotal"
              :page-sizes="[6, 12, 18, 24]"
              layout="total, sizes, prev, pager, next, jumper"
              background
            />
          </div>
        </template>
      </main>
    </div>
  </div>
</template>

<style scoped>
.companion-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  background: linear-gradient(180deg, #f0f9ff 0%, #f8fafc 24%);
  padding-bottom: 48px;
}

/* 有筛选栏时：整页左内边距，为固定侧栏留出空间 */
.companion-page--with-sidebar {
  padding-left: 304px; /* 280 侧栏 + 24 间距 */
}

/* ----- 顶部 ----- */
.page-header {
  flex-shrink: 0;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  margin: 0 24px 24px;
  padding: 28px 32px;
}

.header-inner {
  max-width: 1200px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: -0.02em;
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 15px;
  color: #64748b;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.btn-publish {
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}
.btn-publish:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 14px rgba(13, 148, 136, 0.35);
}

/* ----- 主体两栏 ----- */
.page-body {
  flex: 1;
  min-height: 0;
  max-width: 1280px;
  margin: 0 auto;
  padding: 0 24px;
  display: grid;
  grid-template-columns: 280px 1fr;
  grid-template-rows: 1fr;
  gap: 24px;
  align-items: stretch;
}

/* 有筛选栏时：预留左侧空间，主体只占一列 */
.page-body--with-sidebar {
  grid-template-columns: 1fr;
}

/* 我的结伴模式：无左侧筛选栏，全宽显示 */
.page-body--full {
  grid-template-columns: 1fr;
}

@media (max-width: 1024px) {
  .page-body {
    grid-template-columns: 1fr;
  }
  .companion-page--with-sidebar {
    padding-left: 0;
  }
  .filter-sidebar {
    position: relative;
    left: auto;
    top: auto;
    width: 100%;
    max-height: none;
  }
}

/* ----- 左侧筛选：固定定位，始终在视口左侧同一位置 ----- */
.filter-sidebar {
  position: fixed;
  left: 24px;
  top: 24px;
  width: 280px;
  max-height: calc(100vh - 48px);
  overflow-y: auto;
  z-index: 10;
}

.filter-card {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 20px;
}

.filter-title {
  margin: 0 0 20px;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.filter-block {
  margin-bottom: 20px;
}

.filter-block:last-of-type {
  margin-bottom: 24px;
}

.filter-label {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: #475569;
  margin-bottom: 8px;
}

.sub-label {
  font-size: 12px;
  color: #94a3b8;
  margin: 12px 0 6px;
}

.filter-input,
.filter-date-range {
  width: 100%;
}

.filter-date-range {
  width: 100%;
}

:deep(.el-date-editor) {
  width: 100%;
}

.hot-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.hot-tag {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 20px;
  background: #f1f5f9;
  color: #64748b;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.hot-tag:hover,
.hot-tag.active {
  background: #ccfbf1;
  color: #0d9488;
}

.tag-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.filter-tag {
  font-size: 12px;
  padding: 6px 12px;
  border-radius: 8px;
  background: #f1f5f9;
  color: #64748b;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.filter-tag:hover,
.filter-tag.active {
  background: #e0f2fe;
  color: #0369a1;
}

.filter-slider {
  margin-top: 4px;
}

.filter-actions {
  display: flex;
  gap: 10px;
  padding-top: 8px;
}

.apply-btn {
  flex: 1;
}

/* ----- 右侧卡片流：上方网格一行 3 个，分页固定到底部 ----- */
.card-stream {
  display: flex;
  flex-direction: column;
  gap: 0;
  min-width: 0;
  min-height: 0;
  height: 100%;
}

.card-list-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  align-items: stretch;
}

@media (max-width: 1024px) {
  .card-list-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .card-list-grid {
    grid-template-columns: 1fr;
  }
}

.pagination-wrap {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 28px 0 16px;
  margin-top: auto;
  border-top: 1px solid #e2e8f0;
  flex-shrink: 0;
  background: linear-gradient(180deg, transparent 0%, #f8fafc 8%);
}

.pagination-total {
  font-size: 14px;
  color: #64748b;
}

.companion-card {
  min-width: 0;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 20px;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  overflow: hidden;
}

.companion-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.1);
}

.companion-card:hover .card-actions .el-button--primary:not(.is-disabled) {
  background: #0f766e;
  border-color: #0f766e;
}

.card-top {
  margin-bottom: 14px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-wrap {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, #5eead4, #0d9488);
}

.avatar {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 18px;
  font-weight: 600;
}

.user-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.nickname {
  font-weight: 600;
  font-size: 15px;
  color: #1e293b;
}

.reputation-badge {
  font-size: 11px;
}

.card-trip {
  margin-bottom: 12px;
}

.trip-dest {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
}

.trip-date {
  margin: 6px 0 0;
  font-size: 14px;
  color: #64748b;
}

.trip-days {
  margin-left: 8px;
  color: #0d9488;
  font-weight: 500;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.trip-tag {
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 6px;
  background: #f0fdfa;
  color: #0d9488;
}

.card-desc {
  flex: 1;
  margin: 0 0 16px;
  font-size: 14px;
  color: #64748b;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.8em;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: auto;
  min-width: 0;
}

.card-actions .el-button {
  flex: 1;
  min-width: 80px;
}

/* ----- 骨架屏 ----- */
.skeleton-card {
  pointer-events: none;
}

.skeleton-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: linear-gradient(90deg, #e2e8f0 25%, #f1f5f9 50%, #e2e8f0 75%);
  background-size: 200% 100%;
  animation: skeleton 1.2s ease-in-out infinite;
}

.skeleton-line {
  height: 14px;
  border-radius: 6px;
  background: linear-gradient(90deg, #e2e8f0 25%, #f1f5f9 50%, #e2e8f0 75%);
  background-size: 200% 100%;
  animation: skeleton 1.2s ease-in-out infinite;
  margin-top: 12px;
}

.skeleton-line.long {
  width: 70%;
}

.skeleton-line.short {
  width: 45%;
}

.skeleton-chips {
  margin-top: 12px;
  height: 24px;
  border-radius: 6px;
  width: 60%;
  background: linear-gradient(90deg, #e2e8f0 25%, #f1f5f9 50%, #e2e8f0 75%);
  background-size: 200% 100%;
  animation: skeleton 1.2s ease-in-out infinite;
}

.skeleton-desc {
  margin-top: 12px;
  height: 40px;
  border-radius: 6px;
  background: linear-gradient(90deg, #e2e8f0 25%, #f1f5f9 50%, #e2e8f0 75%);
  background-size: 200% 100%;
  animation: skeleton 1.2s ease-in-out infinite;
}

.skeleton-btns {
  margin-top: 16px;
  height: 36px;
  border-radius: 8px;
  background: linear-gradient(90deg, #e2e8f0 25%, #f1f5f9 50%, #e2e8f0 75%);
  background-size: 200% 100%;
  animation: skeleton 1.2s ease-in-out infinite;
}

@keyframes skeleton {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

/* ----- 空状态：占满主内容区并居中，避免筛选无结果时样式塌陷 ----- */
.empty-wrap {
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
}

.empty-box {
  padding: 0;
}

.empty-illustration {
  font-size: 72px;
  margin-bottom: 16px;
  opacity: 0.9;
}

.empty-desc {
  margin: 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.6;
}
</style>
