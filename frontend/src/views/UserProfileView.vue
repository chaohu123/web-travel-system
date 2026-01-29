
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserProfileStore } from '../store/userProfile'
import { useAuthStore } from '../store'
import type { UserProfileTab } from '../store/userProfile'
import type { NoteSummary, PlanResponse, CompanionPostSummary } from '../api'
import { ChatRound, Guide, CollectionTag, StarFilled, Tickets } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const store = useUserProfileStore()

const userId = computed(() => {
  const raw = route.params.id
  return raw && !Array.isArray(raw) ? Number(raw) : 0
})

const activeTab = computed<UserProfileTab>({
  get: () => store.currentTab,
  set: (val) => {
    if (!userId.value) return
    store.fetchTabData(userId.value, val)
  },
})

const reputationDialogVisible = ref(false)

const isLoggedIn = computed(() => !!auth.token)

const isSelf = computed(() => store.isSelf)

function handleMessageClick() {
  if (!userId.value) return
  router.push({ name: 'chat', params: { id: userId.value } })
}

function handleCompanionClick() {
  if (!isLoggedIn.value) {
    ElMessage.info('请先登录后再发起结伴')
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  router.push({
    name: 'companion-create',
    query: { targetUserId: userId.value },
  })
}

async function handleFollowClick() {
  if (!isLoggedIn.value) {
    ElMessage.info('登录后才能关注旅友哦')
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    return
  }
  if (!userId.value) return
  try {
    await store.toggleFollow(userId.value)
    ElMessage.success(store.profile?.isFollowed ? '已关注' : '已取消关注')
  } catch (e) {
    console.error(e)
    ElMessage.error('操作失败，请稍后重试')
  }
}

function goBack() {
  router.back()
}

function goToNoteDetail(note: NoteSummary) {
  router.push({ name: 'note-detail', params: { id: note.id } })
}

function goToRouteDetail(routeItem: PlanResponse) {
  router.push({ name: 'route-detail', params: { id: routeItem.id } })
}

function goToCompanionDetail(item: CompanionPostSummary) {
  router.push({ name: 'companion-detail', params: { id: item.id } })
}

function handleReviewPageChange(page: number) {
  if (!userId.value) return
  store.changeReviewsPage(userId.value, page)
}

const coverStyle = computed(() => {
  const url = store.profile?.coverImage
  if (!url) {
    return {
      background:
        'linear-gradient(135deg, rgba(56,189,248,0.8), rgba(59,130,246,0.85)), url("https://images.pexels.com/photos/346885/pexels-photo-346885.jpeg?auto=compress&cs=tinysrgb&w=1200") center/cover no-repeat',
    }
  }
  return {
    background: `linear-gradient(120deg, rgba(15,23,42,0.3), rgba(15,23,42,0.55)), url("${url}") center/cover no-repeat`,
  }
})

const computedGenderAgeCity = computed(() => {
  const p = store.profile
  if (!p) return ''
  const parts: string[] = []
  if (p.gender) parts.push(p.gender === 'F' || p.gender === '女' ? '女' : '男')
  if (typeof p.age === 'number') parts.push(`${p.age}岁`)
  if (p.city) parts.push(p.city)
  return parts.join(' · ')
})

const travelStyles = computed(() => store.profile?.preferences?.travelStyles || [])
const interests = computed(() => store.profile?.preferences?.interests || [])
const budgetRange = computed(() => store.profile?.preferences?.budgetRange || '未设置')
const transportPreferences = computed(
  () => store.profile?.preferences?.transportPreferences || [],
)

const stats = computed(() => ({
  completedRoutes: store.profile?.stats?.completedRoutes ?? 0,
  notesCount: store.profile?.stats?.notesCount ?? 0,
  companionSuccessCount: store.profile?.stats?.companionSuccessCount ?? 0,
  likedAndFavorited:
    (store.profile?.stats?.likedCount ?? 0) + (store.profile?.stats?.favoritedCount ?? 0),
}))

const headerLoading = computed(() => store.loadingProfile)
const tabLoading = computed(() => store.loadingTab)

const reputationRateValue = computed(() => {
  const score = store.reputationScoreDisplay
  const level = store.profile?.reputationLevel ?? 1
  if (score <= 0) return level
  const maxScore = 1000
  const normalized = Math.min(1, Math.max(0, score / maxScore))
  return 3 + normalized * 2
})

onMounted(async () => {
  if (!userId.value) return
  try {
    await store.fetchProfile(userId.value)
    await store.fetchTabData(userId.value, 'notes')
  } catch (e) {
    console.error(e)
    ElMessage.error('个人主页加载失败，请稍后重试')
  }
})
</script>

<template>
  <div class="user-profile-page">
    <div class="top-cover" :style="coverStyle">
      <div class="cover-overlay">
        <div class="cover-content" :class="{ 'is-loading': headerLoading }">
          <div class="avatar-area">
            <el-avatar
              :size="96"
              :src="store.profile?.avatar"
              class="avatar-shadow"
            >
              <span v-if="!store.profile?.avatar">
                {{ store.profile?.nickname?.charAt(0).toUpperCase() || '旅' }}
              </span>
            </el-avatar>
          </div>
          <div class="info-area">
            <div class="title-row">
              <div class="name-block">
                <h1 class="nickname">
                  {{ store.profile?.nickname || '旅友' }}
                </h1>
                <p v-if="computedGenderAgeCity" class="sub-line">
                  {{ computedGenderAgeCity }}
                </p>
              </div>
              <div class="reputation-block" @click="reputationDialogVisible = true">
                <div class="reputation-label">
                  信誉等级
                  <span class="reputation-text">{{ store.reputationLevelText }}</span>
                </div>
                <el-rate
                  :model-value="reputationRateValue"
                  disabled
                  allow-half
                  :max="5"
                  class="reputation-rate"
                />
                <div class="reputation-score">
                  积分 {{ store.reputationScoreDisplay }}
                </div>
              </div>
            </div>
            <p v-if="store.profile?.slogan" class="slogan">
              “{{ store.profile?.slogan }}”
            </p>
            <p v-if="store.profile?.intro" class="intro">
              {{ store.profile?.intro }}
            </p>
            <p v-else class="intro muted">
              这位旅友还没有写简介，但旅途中一定有很多故事等你一起发现。
            </p>
            <div class="header-actions desktop-only">
              <template v-if="isSelf">
                <el-button type="primary" plain @click="router.push({ name: 'profile' })">
                  编辑个人资料
                </el-button>
              </template>
              <template v-else>
                <el-button
                  type="primary"
                  :loading="store.followLoading"
                  @click="handleFollowClick"
                >
                  {{ store.profile?.isFollowed ? '已关注' : '关注' }}
                </el-button>
                <el-button type="success" :icon="ChatRound" @click="handleMessageClick">
                  私信
                </el-button>
                <el-button type="warning" plain @click="handleCompanionClick">
                  发起结伴
                </el-button>
              </template>
            </div>
          </div>
        </div>
      </div>
    </div>

    <main class="page-main">
      <section class="layout-grid">
        <aside class="left-column">
          <el-card class="card preferences-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="card-title">旅行偏好</span>
                <span class="card-sub">来自用户档案，仅对外展示</span>
              </div>
            </template>
            <div class="pref-group">
              <div class="pref-label">旅行风格</div>
              <div class="tag-list">
                <el-tag
                  v-for="item in travelStyles"
                  :key="item"
                  type="success"
                  effect="light"
                  round
                >
                  {{ item }}
                </el-tag>
                <span v-if="!travelStyles.length" class="placeholder-text">暂未设置</span>
              </div>
            </div>
            <div class="pref-group">
              <div class="pref-label">兴趣偏好</div>
              <div class="tag-list">
                <el-tag
                  v-for="item in interests"
                  :key="item"
                  type="info"
                  effect="light"
                  round
                >
                  {{ item }}
                </el-tag>
                <span v-if="!interests.length" class="placeholder-text">暂未设置</span>
              </div>
            </div>
            <div class="pref-group">
              <div class="pref-label">预算区间</div>
              <div class="tag-list">
                <el-tag v-if="budgetRange" type="warning" effect="plain" round>
                  {{ budgetRange }}
                </el-tag>
              </div>
            </div>
            <div class="pref-group">
              <div class="pref-label">交通偏好</div>
              <div class="tag-list">
                <el-tag
                  v-for="item in transportPreferences"
                  :key="item"
                  type="primary"
                  effect="light"
                  round
                >
                  {{ item }}
                </el-tag>
                <span v-if="!transportPreferences.length" class="placeholder-text">暂未设置</span>
              </div>
            </div>
          </el-card>

          <el-card class="card stats-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="card-title">旅行数据概览</span>
              </div>
            </template>
            <div class="stats-grid">
              <div class="stat-item">
                <div class="stat-icon stat-icon-blue">
                  <Guide />
                </div>
                <div class="stat-meta">
                  <div class="stat-number">{{ stats.completedRoutes }}</div>
                  <div class="stat-label">已完成路线</div>
                </div>
              </div>
              <div class="stat-item">
                <div class="stat-icon stat-icon-green">
                  <CollectionTag />
                </div>
                <div class="stat-meta">
                  <div class="stat-number">{{ stats.notesCount }}</div>
                  <div class="stat-label">发布游记</div>
                </div>
              </div>
              <div class="stat-item">
                <div class="stat-icon stat-icon-orange">
                  <Tickets />
                </div>
                <div class="stat-meta">
                  <div class="stat-number">{{ stats.companionSuccessCount }}</div>
                  <div class="stat-label">结伴成功</div>
                </div>
              </div>
              <div class="stat-item">
                <div class="stat-icon stat-icon-purple">
                  <StarFilled />
                </div>
                <div class="stat-meta">
                  <div class="stat-number">{{ stats.likedAndFavorited }}</div>
                  <div class="stat-label">收到点赞/收藏</div>
                </div>
              </div>
            </div>
          </el-card>
        </aside>

        <section class="right-column">
          <el-card class="card content-card" shadow="hover">
            <el-tabs
              v-model="activeTab"
              class="profile-tabs"
              stretch
            >
              <el-tab-pane label="游记" name="notes">
                <transition name="fade-slide" mode="out-in">
                  <div v-if="tabLoading" key="loading-notes" class="tab-loading">
                    <el-skeleton :rows="4" animated />
                  </div>
                  <div v-else key="notes-content">
                    <div v-if="store.notes.length" class="notes-grid">
                      <el-card
                        v-for="item in store.notes"
                        :key="item.id"
                        class="note-card"
                        shadow="hover"
                        @click="goToNoteDetail(item)"
                      >
                        <div class="note-cover" v-if="item.coverImage">
                          <img
                            :src="item.coverImage"
                            alt="游记封面"
                            loading="lazy"
                          />
                        </div>
                        <div class="note-body">
                          <h3 class="note-title">
                            {{ item.title }}
                          </h3>
                          <p class="note-meta">
                            <span>{{ item.destination || '未标记目的地' }}</span>
                            <span v-if="item.createdAt">· {{ item.createdAt }}</span>
                          </p>
                          <div class="note-stats">
                            <span>赞 {{ item.likeCount ?? 0 }}</span>
                            <span>评论 {{ item.commentCount ?? 0 }}</span>
                          </div>
                        </div>
                      </el-card>
                    </div>
                    <el-empty
                      v-else
                      description="还没有发布游记"
                      :image-size="120"
                    />
                  </div>
                </transition>
              </el-tab-pane>

              <el-tab-pane label="路线" name="routes">
                <transition name="fade-slide" mode="out-in">
                  <div v-if="tabLoading" key="loading-routes" class="tab-loading">
                    <el-skeleton :rows="4" animated />
                  </div>
                  <div v-else key="routes-content">
                    <div v-if="store.routes.length" class="routes-grid">
                      <el-card
                        v-for="item in store.routes"
                        :key="item.id"
                        class="route-card"
                        shadow="hover"
                        @click="goToRouteDetail(item)"
                      >
                        <div class="route-cover-placeholder">
                          <span class="route-destination">{{ item.destination }}</span>
                        </div>
                        <div class="route-body">
                          <h3 class="route-title">{{ item.title }}</h3>
                          <p class="route-meta">
                            <span>预计 {{ item.days?.length || 0 }} 天</span>
                            <span v-if="item.budget">· 预算 ¥{{ item.budget }}</span>
                            <span v-if="item.peopleCount">· 人数 {{ item.peopleCount }}</span>
                          </p>
                          <p class="route-meta-sub">
                            <span>被使用 {{ item.usedCount ?? 0 }} 次</span>
                            <span v-if="item.pace">· {{ item.pace }}</span>
                          </p>
                        </div>
                      </el-card>
                    </div>
                    <el-empty
                      v-else
                      description="还没有公开路线"
                      :image-size="120"
                    />
                  </div>
                </transition>
              </el-tab-pane>

              <el-tab-pane label="结伴" name="companion">
                <transition name="fade-slide" mode="out-in">
                  <div v-if="tabLoading" key="loading-companion" class="tab-loading">
                    <el-skeleton :rows="4" animated />
                  </div>
                  <div v-else key="companion-content">
                    <div v-if="store.companions.length" class="companion-list">
                      <el-card
                        v-for="item in store.companions"
                        :key="item.id"
                        class="companion-card"
                        shadow="hover"
                        @click="goToCompanionDetail(item)"
                      >
                        <div class="companion-main">
                          <h3 class="companion-destination">{{ item.destination }}</h3>
                          <p class="companion-meta">
                            <span>{{ item.startDate }} - {{ item.endDate }}</span>
                            <span v-if="item.minPeople && item.maxPeople">
                              · {{ item.minPeople }}-{{ item.maxPeople }} 人
                            </span>
                            <span v-if="item.status">· {{ item.status }}</span>
                          </p>
                          <p v-if="item.expectedMateDesc" class="companion-desc">
                            {{ item.expectedMateDesc }}
                          </p>
                        </div>
                      </el-card>
                    </div>
                    <el-empty
                      v-else
                      description="还没有发布结伴活动"
                      :image-size="120"
                    />
                  </div>
                </transition>
              </el-tab-pane>

              <el-tab-pane label="评价" name="reviews">
                <transition name="fade-slide" mode="out-in">
                  <div v-if="tabLoading" key="loading-reviews" class="tab-loading">
                    <el-skeleton :rows="4" animated />
                  </div>
                  <div v-else key="reviews-content" class="reviews-section">
                    <div v-if="store.reviews.length" class="review-list">
                      <div
                        v-for="item in store.reviews"
                        :key="item.id"
                        class="review-item"
                      >
                        <div class="review-header">
                          <span class="review-user">{{ item.userName }}</span>
                          <el-rate
                            :model-value="item.score || 0"
                            disabled
                            :max="5"
                            class="review-rate"
                          />
                        </div>
                        <p class="review-content">
                          {{ item.content }}
                        </p>
                        <div class="review-footer">
                          <div class="review-tags">
                            <el-tag
                              v-for="tag in item.tags || []"
                              :key="tag"
                              size="small"
                              effect="plain"
                              round
                            >
                              {{ tag }}
                            </el-tag>
                          </div>
                          <span class="review-time">{{ item.createdAt }}</span>
                        </div>
                      </div>
                    </div>
                    <el-empty
                      v-else
                      description="暂时还没有结伴评价"
                      :image-size="120"
                    />
                    <div v-if="store.reviewsTotal > store.reviewsPageSize" class="pagination-wrap">
                      <el-pagination
                        background
                        layout="prev, pager, next"
                        :page-size="store.reviewsPageSize"
                        :total="store.reviewsTotal"
                        :current-page="store.reviewsPage"
                        @current-change="handleReviewPageChange"
                      />
                    </div>
                  </div>
                </transition>
              </el-tab-pane>
            </el-tabs>
          </el-card>

          <div class="bottom-hint">
            <span class="hint-text">所有评价均来自真实结伴行程</span>
            <el-button link type="danger" class="report-btn">
              举报该用户
            </el-button>
          </div>
        </section>
      </section>
    </main>

    <div class="mobile-actions mobile-only">
      <template v-if="isSelf">
        <el-button type="primary" plain @click="router.push({ name: 'profile' })">
          编辑资料
        </el-button>
      </template>
      <template v-else>
        <el-button
          type="primary"
          :loading="store.followLoading"
          @click="handleFollowClick"
        >
          {{ store.profile?.isFollowed ? '已关注' : '关注' }}
        </el-button>
        <el-button type="success" :icon="ChatRound" @click="handleMessageClick">
          私信
        </el-button>
        <el-button type="warning" plain @click="handleCompanionClick">
          结伴
        </el-button>
      </template>
    </div>

    <el-dialog
      v-model="reputationDialogVisible"
      title="信誉等级说明"
      width="420px"
    >
      <div class="reputation-dialog">
        <p>平台会根据结伴完成率、评价分数、历史投诉等维度，为每位旅友计算综合信誉分，并划分为不同等级：</p>
        <ul>
          <li>铜牌：新旅友或样本较少的用户，建议先从短途结伴开始；</li>
          <li>银牌：完成多次结伴且整体评价良好，违约记录较少；</li>
          <li>金牌：评价稳定优秀，沟通及时、守时靠谱，适合作为主发起人；</li>
          <li>钻石：长期高分且零严重投诉，为平台重点推荐旅友。</li>
        </ul>
        <p class="dialog-tip">提示：信誉分会随每一次真实行程动态调整，请文明出行、彼此尊重。</p>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="reputationDialogVisible = false">知道了</el-button>
        </span>
      </template>
    </el-dialog>

    <div class="back-btn-wrap desktop-only">
      <el-button text @click="goBack">返回上一页</el-button>
    </div>
  </div>
</template>

<style scoped>
.user-profile-page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, #1e293b 0, transparent 55%),
    radial-gradient(circle at bottom right, #0f766e 0, transparent 55%),
    #020617;
  color: #0f172a;
}

.top-cover {
  position: relative;
  width: 100%;
  height: 260px;
  overflow: hidden;
  animation: cover-fade-in 0.9s ease-out forwards;
}

.cover-overlay {
  width: 100%;
  height: 100%;
  backdrop-filter: blur(1px);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 24px 16px;
}

.cover-content {
  max-width: 1120px;
  width: 100%;
  display: flex;
  gap: 24px;
  color: #e5e7eb;
  opacity: 1;
  transform: translateY(0);
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.cover-content.is-loading {
  opacity: 0.6;
  transform: translateY(4px);
}

.avatar-area {
  flex-shrink: 0;
}

.avatar-shadow :deep(.el-avatar) {
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.7);
  border: 3px solid rgba(248, 250, 252, 0.8);
  background: linear-gradient(135deg, #22c55e, #0ea5e9);
  font-weight: 700;
}

.info-area {
  flex: 1;
  min-width: 0;
}

.title-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
}

.name-block {
  min-width: 0;
}

.nickname {
  margin: 0 0 6px;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.sub-line {
  margin: 0;
  font-size: 14px;
  color: #cbd5f5;
}

.reputation-block {
  min-width: 180px;
  padding: 10px 14px;
  border-radius: 14px;
  background: rgba(15, 23, 42, 0.65);
  border: 1px solid rgba(148, 163, 184, 0.4);
  cursor: pointer;
  transition: background 0.2s ease, transform 0.2s ease, box-shadow 0.2s ease;
}

.reputation-block:hover {
  background: rgba(30, 64, 175, 0.8);
  transform: translateY(-1px);
  box-shadow: 0 10px 30px rgba(15, 23, 42, 0.6);
}

.reputation-label {
  font-size: 12px;
  color: #c7d2fe;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.reputation-text {
  font-weight: 600;
}

.reputation-rate {
  margin: 4px 0;
}

.reputation-score {
  font-size: 12px;
  color: #e5e7eb;
}

.slogan {
  margin: 10px 0 4px;
  font-size: 15px;
  font-weight: 500;
  color: #fef9c3;
}

.intro {
  margin: 0;
  font-size: 13px;
  color: #e5e7eb;
}

.intro.muted {
  opacity: 0.8;
}

.header-actions {
  margin-top: 12px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.page-main {
  max-width: 1120px;
  margin: 24px auto 40px;
  padding: 0 16px 40px;
  position: relative;
  z-index: 1;
}

.layout-grid {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: 24px;
  align-items: flex-start;
}

.card {
  border-radius: 18px;
  overflow: hidden;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.card:hover {
  transform: translateY(-3px);
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}

.card-sub {
  font-size: 12px;
  color: #9ca3af;
}

.pref-group + .pref-group {
  margin-top: 12px;
}

.pref-label {
  font-size: 13px;
  color: #6b7280;
  margin-bottom: 6px;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.placeholder-text {
  font-size: 12px;
  color: #9ca3af;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 14px;
  background: #f9fafb;
}

.stat-icon {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-icon-blue {
  background: linear-gradient(135deg, #38bdf8, #6366f1);
}

.stat-icon-green {
  background: linear-gradient(135deg, #22c55e, #16a34a);
}

.stat-icon-orange {
  background: linear-gradient(135deg, #f97316, #f59e0b);
}

.stat-icon-purple {
  background: linear-gradient(135deg, #a855f7, #6366f1);
}

.stat-meta {
  flex: 1;
}

.stat-number {
  font-weight: 600;
  font-size: 18px;
}

.stat-label {
  font-size: 12px;
  color: #6b7280;
}

.right-column {
  min-width: 0;
}

.profile-tabs {
  --el-color-primary: #0ea5e9;
}

.tab-loading {
  padding: 16px 4px;
}

.notes-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 14px;
}

.note-card {
  cursor: pointer;
  display: flex;
  flex-direction: column;
}

.note-card:hover {
  transform: translateY(-4px);
}

.note-cover {
  width: 100%;
  height: 140px;
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 10px;
  background: #e5e7eb;
}

.note-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.4s ease;
}

.note-card:hover .note-cover img {
  transform: scale(1.04);
}

.note-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.note-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.note-meta {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.note-stats {
  font-size: 12px;
  color: #9ca3af;
  display: flex;
  gap: 10px;
}

.routes-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 14px;
}

.route-card {
  cursor: pointer;
}

.route-cover-placeholder {
  height: 90px;
  border-radius: 10px;
  background: linear-gradient(135deg, #38bdf8, #22c55e);
  display: flex;
  align-items: flex-end;
  padding: 10px 12px;
  color: #f9fafb;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.route-title {
  margin: 10px 0 4px;
  font-size: 15px;
  font-weight: 600;
}

.route-meta,
.route-meta-sub {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.companion-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.companion-card {
  cursor: pointer;
}

.companion-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.companion-destination {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
}

.companion-meta {
  margin: 0;
  font-size: 12px;
  color: #6b7280;
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.companion-desc {
  margin: 0;
  font-size: 13px;
  color: #4b5563;
}

.reviews-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.review-item {
  padding: 10px 0;
  border-bottom: 1px dashed #e5e7eb;
}

.review-item:last-child {
  border-bottom: none;
}

.review-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.review-user {
  font-size: 13px;
  font-weight: 600;
  color: #374151;
}

.review-content {
  margin: 0;
  font-size: 13px;
  color: #4b5563;
}

.review-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
}

.review-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.review-time {
  font-size: 12px;
  color: #9ca3af;
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  padding-top: 8px;
}

.bottom-hint {
  margin-top: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #9ca3af;
}

.report-btn {
  font-size: 12px;
}

.back-btn-wrap {
  position: fixed;
  left: 24px;
  bottom: 24px;
}

.mobile-actions {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 8px 12px calc(env(safe-area-inset-bottom, 0px) + 8px);
  display: flex;
  gap: 8px;
  justify-content: center;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0), rgba(15, 23, 42, 0.6));
  backdrop-filter: blur(10px);
  z-index: 20;
}

.mobile-actions :deep(.el-button) {
  flex: 1;
}

.desktop-only {
  display: block;
}

.mobile-only {
  display: none;
}

.reputation-dialog {
  font-size: 13px;
  color: #4b5563;
}

.reputation-dialog ul {
  padding-left: 18px;
  margin: 6px 0 8px;
}

.reputation-dialog li {
  margin-bottom: 4px;
}

.dialog-tip {
  font-size: 12px;
  color: #6b7280;
}

@keyframes cover-fade-in {
  from {
    opacity: 0;
    transform: scale(1.02);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: opacity 0.25s ease, transform 0.25s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(4px);
}

@media (max-width: 960px) {
  .top-cover {
    height: 210px;
  }

  .cover-content {
    flex-direction: row;
  }

  .page-main {
    margin-top: -28px;
  }

  .layout-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .left-column {
    order: 2;
  }

  .right-column {
    order: 1;
  }

  .back-btn-wrap {
    display: none;
  }
}

@media (max-width: 640px) {
  .top-cover {
    height: 190px;
  }

  .title-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .reputation-block {
    margin-top: 8px;
  }

  .desktop-only {
    display: none;
  }

  .mobile-only {
    display: flex;
  }

  .page-main {
    padding-bottom: 72px;
  }
}
</style>
