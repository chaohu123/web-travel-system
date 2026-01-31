<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Calendar, Location, ChatDotRound, Bell, UserFilled, User, Document, Share, ArrowLeft } from '@element-plus/icons-vue'
import { useAuthStore, reputationLevelLabel } from '../store'
import { companionApi, routesApi, userApi } from '../api'
import type { TeamMemberItem, FollowingItem, CompanionPostSummary } from '../api/types'
import { useTeamStore } from '../store/team'
import { formatDate, formatDateTime } from '../utils/format'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const teamStore = useTeamStore()

// 返回上一页（仿照 Route/Spot 等详情页）
function goBack() {
  if (window.history.length > 1) {
    router.go(-1)
  } else {
    router.push('/')
  }
}

const teamId = computed(() => {
  const id = route.params.id
  return id && !Array.isArray(id) ? Number(id) : 0
})

const loading = ref(true)
const errorMsg = ref('')

// 小队数据（与 store 同步，便于模板使用）
const team = computed(() => teamStore.team)
const post = computed(() => teamStore.post)
const plan = computed(() => teamStore.plan)
const announcements = computed(() => teamStore.announcements)
const dynamics = computed(() => teamStore.dynamics)

const isCurrentUserLeader = computed(() => teamStore.isLeader(auth.userId))
const isCurrentUserMember = computed(() => teamStore.isMember(auth.userId))

const statusTagMap: Record<string, { type: 'success' | 'warning' | 'info' | 'danger'; text: string }> = {
  forming: { type: 'success', text: '招募中' },
  FULL: { type: 'warning', text: '已满员' },
  full: { type: 'warning', text: '已满员' },
  formed: { type: 'info', text: '已成团' },
  disbanded: { type: 'danger', text: '已解散' },
}
const statusTag = computed(() => {
  const s = (team.value?.status ?? '').trim() || 'forming'
  return statusTagMap[s] ?? { type: 'info', text: team.value?.status ?? '招募中' }
})

const tripDays = computed(() => {
  const t = team.value
  if (!t?.startDate || !t?.endDate) return 0
  const a = new Date(t.startDate).getTime()
  const b = new Date(t.endDate).getTime()
  return Math.max(1, Math.round((b - a) / (24 * 3600 * 1000)) + 1)
})

const poiCount = computed(() => {
  const days = plan.value?.days ?? []
  return days.reduce((sum, d) => sum + (d.activities?.length ?? 0), 0)
})

const budgetText = computed(() => {
  const t = team.value
  const p = post.value
  const min = t?.budgetMin ?? p?.budgetMin
  const max = t?.budgetMax ?? p?.budgetMax
  if (!min && !max) return '待定'
  if (min != null && max != null) return `¥${min} - ${max}`
  if (min != null) return `¥${min}起`
  if (max != null) return `¥${max}内`
  return '待定'
})

// 成员管理弹窗
const memberDialogVisible = ref(false)
const selectedMember = ref<TeamMemberItem | null>(null)
const removeMemberLoading = ref(false)

// 邀请成员弹窗：已关注列表 + 分享
const inviteDialogVisible = ref(false)
const followingList = ref<FollowingItem[]>([])
const loadingFollowing = ref(false)
const sharedUserIds = ref<Set<number>>(new Set())
const shareLoadingUserId = ref<number | null>(null)

// 推荐相似小队（已登录时展示真实数据）
const loadingRecommended = ref(false)
const recommendedTeams = ref<CompanionPostSummary[]>([])

// 小队群聊未读数（由消息接口提供，暂无则为 0）
const teamChatUnread = ref(0)

async function fetchRecommended(destination?: string | null, excludePostId?: number | null) {
  if (!auth.token) {
    recommendedTeams.value = []
    return
  }
  loadingRecommended.value = true
  try {
    const list = await companionApi.listPosts({ destination: destination || undefined })
    recommendedTeams.value = (list || [])
      .filter((p) => (excludePostId ? p.id !== excludePostId : true))
      .slice(0, 3)
  } catch {
    recommendedTeams.value = []
  } finally {
    loadingRecommended.value = false
  }
}

async function fetchTeam() {
  if (!teamId.value) return
  loading.value = true
  errorMsg.value = ''
  teamStore.reset()
  try {
    const teamData = await companionApi.getTeam(teamId.value)
    teamStore.setTeam(teamData)
    if (teamData.postId) {
      try {
        const postData = await companionApi.getPost(teamData.postId)
        teamStore.setPost(postData)
      } catch {
        teamStore.setPost(null)
      }
    } else {
      teamStore.setPost(null)
    }
    if (teamData.relatedPlanId) {
      try {
        const planData = await routesApi.getOne(teamData.relatedPlanId)
        teamStore.setPlan(planData)
      } catch {
        teamStore.setPlan(null)
      }
    } else {
      teamStore.setPlan(null)
    }
    // 公告与动态：后端暂无接口时为空，有接口后可在此请求并 setAnnouncements/setDynamics
    teamStore.setAnnouncements([])
    teamStore.setDynamics([])

    await fetchRecommended(teamData.destination, teamData.postId)
  } catch (e: any) {
    errorMsg.value = e.response?.data?.message || e.message || '加载小队信息失败'
    teamStore.reset()
  } finally {
    loading.value = false
  }
}

function goPlan() {
  if (!team.value?.relatedPlanId) return
  router.push(`/routes/${team.value.relatedPlanId}`)
}

function goCompanionPost() {
  if (!post.value?.id) return
  router.push(`/companion/${post.value.id}`)
}

function goRecommendCompanion(id: number) {
  router.push(`/companion/${id}`)
}

function goUserProfile(userId: number) {
  router.push(`/users/${userId}`)
}

function openChatWithUser(userId: number, nickname: string) {
  if (!auth.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    ElMessage.warning('请先登录后再发起私信')
    return
  }
  router.push({ path: `/chat/${userId}`, query: { nickname } })
}

function openTeamChat() {
  if (!auth.token) {
    router.push({ name: 'login', query: { redirect: route.fullPath } })
    ElMessage.warning('请先登录后进入小队群聊')
    return
  }
  // 小队群聊：跳转至消息中心（后续可改为小队专属聊天页）
  router.push({ path: '/messages' })
  teamChatUnread.value = 0
}

async function quitTeam() {
  if (!team.value || !auth.token) return
  try {
    await ElMessageBox.confirm('退出后需重新申请才能加入，确定要退出小队吗？', '退出小队', {
      type: 'warning',
      confirmButtonText: '确定退出',
      cancelButtonText: '取消',
    })
    try {
      await companionApi.quitTeam(team.value.id)
      ElMessage.success('已退出小队')
      router.push(`/companion/${team.value.postId || ''}`)
    } catch (e: any) {
      ElMessage.error(e.response?.data?.message ?? e.message ?? '退出失败，请稍后重试')
    }
  } catch {
    // 用户取消
  }
}

async function dissolveTeam() {
  if (!team.value || !isCurrentUserLeader.value) return
  try {
    await ElMessageBox.confirm('解散后小队将不可恢复。确定要解散吗？', '解散小队', {
      type: 'warning',
      confirmButtonText: '确定解散',
      cancelButtonText: '取消',
    })
    await companionApi.dissolveTeam(team.value.id)
    ElMessage.success('小队已解散')
    await fetchTeam()
  } catch (e: any) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message ?? e.message ?? '解散失败，请稍后重试')
    }
  }
}

async function openInviteDialog() {
  if (!team.value || !auth.token) return
  inviteDialogVisible.value = true
  followingList.value = []
  loadingFollowing.value = true
  try {
    followingList.value = await userApi.myFollowing()
  } catch (e: any) {
    ElMessage.error(e.message ?? '获取关注列表失败')
  } finally {
    loadingFollowing.value = false
  }
}

function closeInviteDialog() {
  inviteDialogVisible.value = false
  sharedUserIds.value = new Set()
  shareLoadingUserId.value = null
}

async function shareToUser(userId: number) {
  if (!team.value) return
  shareLoadingUserId.value = userId
  try {
    await companionApi.shareTeam(team.value.id, userId)
    sharedUserIds.value = new Set([...sharedUserIds.value, userId])
    ElMessage.success('已分享，对方可查看该行程')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message ?? e.message ?? '分享失败')
  } finally {
    shareLoadingUserId.value = null
  }
}

function followingDisplayName(item: FollowingItem): string {
  const name = item?.nickname?.trim()
  if (!name) return '旅友'
  if (name.includes('@')) return '旅友'
  return name
}

function openMemberMenu(member: TeamMemberItem) {
  selectedMember.value = member
  memberDialogVisible.value = true
}

function closeMemberDialog() {
  memberDialogVisible.value = false
  selectedMember.value = null
  removeMemberLoading.value = false
}

async function removeMember() {
  if (!selectedMember.value || !team.value || !isCurrentUserLeader.value) return
  removeMemberLoading.value = true
  try {
    await companionApi.removeMember(team.value.id, selectedMember.value.userId)
    ElMessage.success('已移除该成员')
    closeMemberDialog()
    await fetchTeam()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message ?? e.message ?? '移除失败，请稍后重试')
  } finally {
    removeMemberLoading.value = false
  }
}

function reputationLabel(level?: number): string {
  if (level == null) return '普通'
  return reputationLevelLabel(level)
}

/** 成员状态显示中文 */
function stateLabel(state?: string): string {
  if (!state) return '—'
  const map: Record<string, string> = {
    joined: '已加入',
    pending: '待确认',
    left: '已退出',
  }
  return map[state] ?? state
}

/** 成员展示名称：优先昵称，邮箱则隐藏，无则显示「用户{id}」 */
function memberDisplayName(m: TeamMemberItem): string {
  const name = m?.userName?.trim()
  if (name && !name.includes('@')) return name
  if (m?.userId != null) return `用户${m.userId}`
  return '旅友'
}

onMounted(() => {
  fetchTeam()
})

watch(teamId, () => {
  if (teamId.value) fetchTeam()
})
</script>

<template>
  <div class="team-page">
    <!-- 返回按钮（仿照 RouteDetailView 等详情页） -->
    <div class="back-button-container">
      <el-button :icon="ArrowLeft" circle @click="goBack" class="back-button" />
    </div>

    <!-- 加载 / 错误 -->
    <div v-if="loading" class="team-loading">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>加载小队信息中…</p>
    </div>
    <el-empty v-else-if="errorMsg || !team" :description="errorMsg || '未找到小队信息'" />

    <template v-else-if="team">
      <!-- 已解散提示 -->
      <el-alert
        v-if="team.status === 'disbanded'"
        type="warning"
        show-icon
        class="non-member-alert"
        :closable="false"
        title="小队已解散"
      >
        本小队已解散，仅可查看历史信息。
      </el-alert>
      <!-- 已登录但非成员提示 -->
      <el-alert
        v-if="auth.token && !isCurrentUserMember"
        type="info"
        show-icon
        class="non-member-alert"
        :closable="false"
      >
        <template #title>您还不是小队成员</template>
        请先通过关联结伴帖申请加入后再查看完整信息与操作。
        <el-button v-if="post" type="primary" link @click="goCompanionPost">去结伴帖申请</el-button>
      </el-alert>
      <div class="team-layout">
        <!-- 左侧主内容 约 70% -->
        <main class="team-main">
          <!-- 小队头部信息区 -->
          <el-card class="team-header-card" shadow="hover">
            <div class="team-header">
              <div class="header-top">
                <h1 class="team-title">{{ team.name }}</h1>
                <el-tag :type="statusTag.type" size="large">{{ statusTag.text }}</el-tag>
              </div>
              <div class="header-meta">
                <span v-if="team.startDate && team.endDate" class="meta-item">
                  <el-icon><Calendar /></el-icon>
                  {{ formatDate(team.startDate) }} — {{ formatDate(team.endDate) }}
                </span>
                <span v-if="team.destination" class="meta-item">
                  <el-icon><Location /></el-icon>
                  {{ team.destination }}
                </span>
              </div>
              <div class="header-summary">
                <span class="summary-item">
                  <strong>{{ teamStore.currentMemberCount }}</strong> / {{ teamStore.maxMemberCount || '—' }} 人
                </span>
                <span class="summary-item">预算 {{ budgetText }}</span>
                <span class="summary-item">出行方式 自驾/火车/飞机（以行程为准）</span>
              </div>
              <div v-if="team.status !== 'disbanded'" class="header-actions">
                <el-badge v-if="teamChatUnread > 0" :value="teamChatUnread" :max="99">
                  <el-button type="primary" :icon="ChatDotRound" @click="openTeamChat">小队群聊</el-button>
                </el-badge>
                <el-button v-else type="primary" :icon="ChatDotRound" @click="openTeamChat">小队群聊</el-button>
                <el-button v-if="isCurrentUserMember && !isCurrentUserLeader" type="default" @click="quitTeam">
                  退出小队
                </el-button>
                <el-button v-if="isCurrentUserLeader" type="danger" plain @click="dissolveTeam">
                  解散小队
                </el-button>
                <el-button v-if="isCurrentUserLeader" type="primary" plain @click="openInviteDialog">
                  邀请成员
                </el-button>
              </div>
            </div>
          </el-card>

          <!-- 小队成员列表区 -->
          <el-card class="team-card" shadow="hover">
            <template #header>
              <span>小队成员</span>
            </template>
            <div v-if="team.members && team.members.length" class="member-grid">
              <div
                v-for="m in team.members"
                :key="m.userId"
                class="member-card"
                @click="openMemberMenu(m)"
              >
                <el-avatar :size="48" class="member-avatar" :src="m.avatar || undefined">
                  {{ memberDisplayName(m).charAt(0).toUpperCase() }}
                </el-avatar>
                <div class="member-info">
                  <div class="member-name-row">
                    <span class="member-name">{{ memberDisplayName(m) }}</span>
                    <el-tag v-if="m.role === 'leader'" type="warning" size="small">队长</el-tag>
                    <el-tag v-else size="small">成员</el-tag>
                  </div>
                  <div class="member-extra">
                    <span class="member-state">状态：{{ stateLabel(m.state) }}</span>
                    <span class="member-reputation">信誉 {{ reputationLabel(m.reputationLevel ?? undefined) }}</span>
                  </div>
                </div>
                <div class="member-actions">
                  <el-button link type="primary" size="small" :icon="User" @click.stop="goUserProfile(m.userId)">
                    主页
                  </el-button>
                  <el-button link type="primary" size="small" :icon="ChatDotRound" @click.stop="openChatWithUser(m.userId, memberDisplayName(m))">
                    私信
                  </el-button>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无成员" />
          </el-card>

          <!-- 行程概览与协作区 -->
          <el-card v-if="plan || team.relatedPlanId" class="team-card" shadow="hover">
            <template #header>
              <span>行程概览</span>
              <el-tag v-if="plan" type="success" size="small">已确认行程</el-tag>
              <el-tag v-else type="info" size="small">协商中</el-tag>
            </template>
            <template v-if="plan">
              <div class="trip-summary">
                <p class="trip-name">{{ plan.title }}</p>
                <p class="trip-desc">行程 {{ tripDays }} 天，约 {{ poiCount }} 个核心景点</p>
                <p v-if="plan.destination" class="trip-destination">目的地：{{ plan.destination }}</p>
              </div>
              <div class="trip-actions">
                <el-button type="primary" @click="goPlan">查看完整路线</el-button>
                <el-button plain>共同编辑行程</el-button>
              </div>
            </template>
            <template v-else>
              <p class="text-subtle">暂无关联行程，队长可在结伴帖中关联路线。</p>
              <el-button type="primary" plain @click="goCompanionPost">去结伴帖设置</el-button>
            </template>
          </el-card>

          <!-- 出行安全提示（行程概览下方） -->
          <el-card class="team-card safety-card" shadow="hover">
            <template #header>
              <span>出行安全提示</span>
            </template>
            <ul class="safety-list">
              <li>出行前与队友交换联系方式，约定集合时间与地点</li>
              <li>勿向陌生人透露银行卡、验证码等敏感信息</li>
              <li>平台仅提供信息对接，线下见面请选择公共场所</li>
              <li>遇可疑情况可联系平台客服或报警</li>
            </ul>
          </el-card>
        </main>

        <!-- 右侧辅助信息区 约 30%（含小队公告、小队动态） -->
        <aside class="team-aside">
          <el-card class="aside-card compact" shadow="hover">
            <template #header>
              <span>小队公告</span>
            </template>
            <div v-if="announcements.length" class="announce-list">
              <div v-for="a in announcements" :key="a.id" class="announce-item">
                <el-icon class="announce-icon"><Bell /></el-icon>
                <div>
                  <p class="announce-content">{{ a.content }}</p>
                  <span class="announce-time">{{ formatDateTime(a.createdAt) }}{{ a.authorName ? ` · ${a.authorName}` : '' }}</span>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无公告" :image-size="72" />
          </el-card>
          <el-card class="aside-card compact" shadow="hover">
            <template #header>
              <span>小队动态</span>
            </template>
            <div v-if="dynamics.length" class="dynamic-list">
              <div v-for="d in dynamics" :key="d.id" class="dynamic-item">
                <el-icon v-if="d.type === 'join'" class="dynamic-icon join"><UserFilled /></el-icon>
                <el-icon v-else-if="d.type === 'leave'" class="dynamic-icon leave"><User /></el-icon>
                <el-icon v-else class="dynamic-icon update"><Document /></el-icon>
                <span class="dynamic-content">{{ d.content }}</span>
                <span class="dynamic-time">{{ formatDateTime(d.createdAt) }}</span>
              </div>
            </div>
            <el-empty v-else description="暂无动态" :image-size="72" />
          </el-card>
          <el-card v-if="post" class="aside-card" shadow="hover">
            <template #header>
              <span>关联结伴帖子</span>
            </template>
            <div class="post-row">
              <p class="aside-desc">本小队由结伴帖「{{ post.destination }}」发起</p>
              <el-button type="primary" plain @click="goCompanionPost">查看结伴帖</el-button>
            </div>
          </el-card>
          <el-card class="aside-card" shadow="hover">
            <template #header>
              <div class="aside-header-row">
                <span>推荐相似小队</span>
                <el-button link type="primary" size="small" @click="router.push('/companion')">去结伴广场</el-button>
              </div>
            </template>

            <template v-if="!auth.token">
              <p class="aside-desc text-subtle">登录后根据目的地与时间为您推荐相似结伴小队</p>
              <el-button type="primary" plain full-width @click="router.push({ name: 'login', query: { redirect: route.fullPath } })">
                去登录
              </el-button>
            </template>

            <template v-else>
              <div v-if="loadingRecommended" class="text-subtle">正在为你推荐…</div>
              <div v-else-if="recommendedTeams.length" class="recommend-list">
                <div v-for="item in recommendedTeams" :key="item.id" class="recommend-item">
                  <div class="recommend-left">
                    <div class="recommend-title">{{ item.destination }}</div>
                    <div class="recommend-sub text-subtle">{{ item.startDate }} ~ {{ item.endDate }}</div>
                  </div>
                  <el-button size="small" type="primary" plain @click="goRecommendCompanion(item.id)">查看</el-button>
                </div>
              </div>
              <template v-else>
                <p class="aside-desc text-subtle">暂未找到相似小队，去结伴广场看看～</p>
              </template>
            </template>
          </el-card>
        </aside>
      </div>

      <!-- 成员操作弹窗 -->
      <el-dialog
        v-model="memberDialogVisible"
        title="成员操作"
        width="400px"
        @close="closeMemberDialog"
      >
        <template v-if="selectedMember">
          <div class="member-dialog-body">
            <el-avatar :size="56" :src="selectedMember.avatar || undefined">{{ memberDisplayName(selectedMember).charAt(0).toUpperCase() }}</el-avatar>
            <p class="dialog-name">{{ memberDisplayName(selectedMember) }}</p>
            <p class="dialog-role">{{ selectedMember.role === 'leader' ? '队长' : '成员' }} · 状态：{{ stateLabel(selectedMember.state) }}</p>
          </div>
          <div class="member-dialog-actions">
            <el-button type="primary" :icon="User" @click="goUserProfile(selectedMember.userId)">查看主页</el-button>
            <el-button type="primary" plain :icon="ChatDotRound" @click="openChatWithUser(selectedMember.userId, memberDisplayName(selectedMember))">私信</el-button>
            <el-button v-if="isCurrentUserLeader && selectedMember.role !== 'leader'" type="danger" plain :loading="removeMemberLoading" @click="removeMember">
              移除成员
            </el-button>
          </div>
        </template>
      </el-dialog>

      <!-- 邀请成员弹窗：已关注列表，左侧头像+昵称，右侧分享按钮 -->
      <el-dialog
        v-model="inviteDialogVisible"
        title="邀请成员"
        width="480px"
        class="invite-dialog"
        @close="closeInviteDialog"
      >
        <p class="invite-dialog-desc">选择已关注的人分享小队，对方可查看该行程并申请加入。</p>
        <div v-loading="loadingFollowing" class="invite-follow-list">
          <template v-if="followingList.length">
            <div
              v-for="item in followingList"
              :key="item.userId"
              class="invite-follow-item"
            >
              <div class="invite-follow-left">
                <el-avatar :size="40" :src="item.avatar || undefined" class="invite-avatar">
                  {{ followingDisplayName(item).charAt(0).toUpperCase() }}
                </el-avatar>
                <span class="invite-nickname">{{ followingDisplayName(item) }}</span>
              </div>
              <div class="invite-follow-right">
                <el-button
                  v-if="sharedUserIds.has(item.userId)"
                  type="success"
                  size="small"
                  disabled
                >
                  已分享
                </el-button>
                <el-button
                  v-else
                  type="primary"
                  size="small"
                  :icon="Share"
                  :loading="shareLoadingUserId === item.userId"
                  @click="shareToUser(item.userId)"
                >
                  分享
                </el-button>
              </div>
            </div>
          </template>
          <el-empty v-else-if="!loadingFollowing" description="暂无关注的人，去发现旅友并关注后即可邀请" />
        </div>
      </el-dialog>
    </template>
  </div>
</template>

<style scoped>
.team-page {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px 16px;
  min-height: 60vh;
}

.back-button-container {
  position: fixed;
  top: 80px;
  left: 20px;
  z-index: 100;
}

.back-button {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(10px);
  border: 1px solid rgba(226, 232, 240, 0.8);
  box-shadow: 0 4px 12px rgba(15, 23, 42, 0.15);
  transition: all 0.2s ease;
}

.back-button:hover {
  background: rgba(255, 255, 255, 1);
  transform: translateX(-2px);
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.2);
}
.non-member-alert {
  margin-bottom: 16px;
  border-radius: 10px;
}

.team-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px;
  color: var(--el-text-color-secondary);
}
.team-loading .el-icon { margin-bottom: 12px; }

.team-layout {
  display: grid;
  grid-template-columns: minmax(0, 7fr) minmax(0, 3fr);
  gap: 20px;
}

.team-main {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.team-header-card {
  border-radius: 12px;
}
.team-header {
  padding: 4px 0;
}
.header-top {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.team-title {
  margin: 0;
  font-size: 22px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.header-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 10px;
  color: var(--el-text-color-regular);
  font-size: 14px;
}
.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.header-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 16px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
.summary-item strong { color: var(--el-text-color-primary); }
.header-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.chat-badge { margin-left: 4px; }

.team-card {
  border-radius: 12px;
}
.team-card :deep(.el-card__header) {
  font-weight: 600;
  font-size: 16px;
}
.safety-card {
  margin-top: 4px;
}
.safety-card :deep(.el-card__body) {
  padding: 20px 24px;
}
.safety-list {
  margin: 0;
  padding-left: 20px;
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 2;
}
.safety-list li {
  margin-bottom: 8px;
}
.safety-list li:last-child {
  margin-bottom: 0;
}

.member-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}
.member-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  cursor: pointer;
  transition: background-color 0.2s;
}
.member-card:hover {
  background-color: var(--el-fill-color-light);
}
.member-avatar {
  flex-shrink: 0;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
}
.member-info {
  flex: 1;
  min-width: 0;
}
.member-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.member-name {
  font-weight: 500;
  font-size: 15px;
}
.member-extra {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
}
.member-state,
.member-reputation { margin-right: 8px; }
.member-actions {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  flex-shrink: 0;
}
.member-actions .el-button {
  margin: 0;
}

.trip-summary {
  margin-bottom: 12px;
}
.trip-name {
  font-weight: 600;
  font-size: 16px;
  margin: 0 0 6px;
}
.trip-desc,
.trip-destination {
  margin: 0 0 4px;
  font-size: 14px;
  color: var(--el-text-color-regular);
}
.trip-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.announce-list,
.dynamic-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.announce-item {
  display: flex;
  gap: 10px;
  padding: 10px;
  background: var(--el-fill-color-light);
  border-radius: 8px;
}
.announce-icon {
  color: var(--el-color-warning);
  font-size: 18px;
  flex-shrink: 0;
}
.announce-content {
  margin: 0 0 4px;
  font-size: 14px;
}
.announce-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.dynamic-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
}
.dynamic-icon {
  flex-shrink: 0;
  font-size: 16px;
}
.dynamic-icon.join { color: var(--el-color-success); }
.dynamic-icon.leave { color: var(--el-text-color-secondary); }
.dynamic-icon.update { color: var(--el-color-primary); }
.dynamic-content { flex: 1; }
.dynamic-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.team-aside {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.aside-card {
  border-radius: 12px;
}
.aside-card :deep(.el-card__header) {
  font-weight: 600;
  font-size: 15px;
}
.aside-card.compact :deep(.el-card__body) {
  padding: 12px 16px;
}
.aside-card.compact :deep(.el-empty) {
  padding: 8px 0;
}
.aside-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.post-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.post-row .aside-desc {
  margin: 0;
}
.aside-desc {
  margin: 0 0 12px;
  font-size: 14px;
}
.text-subtle { color: var(--el-text-color-secondary); }

.member-dialog-body {
  text-align: center;
  padding: 12px 0;
}
.member-dialog-body .el-avatar {
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
}
.dialog-name {
  margin: 12px 0 4px;
  font-size: 18px;
  font-weight: 600;
}
.dialog-role {
  margin: 0;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
.member-dialog-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  padding-top: 8px;
}

.invite-dialog-desc {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--el-text-color-secondary);
}
.invite-follow-list {
  min-height: 120px;
  max-height: 360px;
  overflow-y: auto;
}
.invite-follow-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px solid var(--el-border-color-lighter);
  gap: 12px;
}
.invite-follow-item:last-child {
  border-bottom: none;
}
.invite-follow-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.invite-avatar {
  flex-shrink: 0;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-weight: 600;
}
.invite-nickname {
  font-size: 15px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.invite-follow-right {
  flex-shrink: 0;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.recommend-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-light);
}
.recommend-left {
  min-width: 0;
}
.recommend-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.recommend-sub {
  font-size: 12px;
}

@media (max-width: 768px) {
  .back-button-container {
    top: 70px;
    left: 12px;
  }

  .back-button {
    width: 36px;
    height: 36px;
  }

  .team-layout {
    grid-template-columns: 1fr;
  }
  .team-aside {
    order: -1;
  }
  .header-actions {
    width: 100%;
  }
  .header-actions .el-button {
    flex: 1;
    min-width: 120px;
  }
}
</style>
