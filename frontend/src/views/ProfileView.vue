<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import EditProfileDialog from '../components/EditProfileDialog.vue'
import { useProfileStore, type ProfileTab } from '../store/profile'
import { useAuthStore, reputationLevelLabel } from '../store'
import { userApi, routesApi, companionApi, feedsApi, interactionsApi } from '../api'
import type { FavoriteItem } from '../store/profile'
import type { MeDetail } from '../api'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const auth = useAuthStore()
const store = useProfileStore()

const activeTab = ref<ProfileTab>('info')
const editDialogVisible = ref(false)
const sidebarCollapsed = ref(false)

const menuItems: { key: ProfileTab; label: string }[] = [
  { key: 'info', label: '个人信息' },
  { key: 'routes', label: '我的路线' },
  { key: 'companion', label: '我的结伴' },
  { key: 'favorites', label: '我的收藏' },
  { key: 'feeds', label: '我的动态' },
  { key: 'settings', label: '账户设置' },
  { key: 'security', label: '安全与隐私' },
]

const levelLabel = computed(() => reputationLevelLabel(store.me?.reputationLevel))

async function loadMe() {
  try {
    const me = await userApi.meDetail()
    store.setMe(me)
    auth.setProfile(me.nickname ?? null, me.reputationLevel ?? null)
  } catch {
    store.setMe(null)
  }
}

async function loadRoutes() {
  try {
    const list = await routesApi.myPlans()
    store.setMyRoutes(list || [])
  } catch {
    store.setMyRoutes([])
  }
}

async function loadCompanion() {
  try {
    const list = await companionApi.myPosts()
    store.setMyCompanion(list || [])
  } catch {
    store.setMyCompanion([])
  }
}

async function loadFeeds() {
  try {
    const list = await feedsApi.list()
    const email = store.me?.email
    const nickname = auth.nickname
    const mine = (list || []).filter(
      (f) => f.authorName === email || f.authorName === nickname
    )
    store.setMyFeeds(mine)
  } catch {
    store.setMyFeeds([])
  }
}

async function loadFavorites() {
  try {
    const list = await interactionsApi.myFavorites()
    const items: FavoriteItem[] = (list || []).map((item) => ({
      type: item.targetType as FavoriteItem['type'],
      id: item.targetId,
      title: undefined,
      destination: undefined,
    }))
    store.setFavorites(items)
  } catch {
    store.setFavorites([])
  }
}

onMounted(async () => {
  await loadMe()
  loadRoutes()
  loadCompanion()
  loadFeeds()
  loadFavorites()
})

watch(activeTab, (tab) => {
  if (tab === 'routes') loadRoutes()
  if (tab === 'companion') loadCompanion()
  if (tab === 'feeds') loadFeeds()
  if (tab === 'favorites') loadFavorites()
})

function onProfileUpdated(profile: MeDetail) {
  store.setMe(profile)
  auth.setProfile(profile.nickname ?? null, profile.reputationLevel ?? null)
}

function daysBetween(start: string, end: string) {
  if (!start || !end) return 0
  return Math.max(1, Math.round((new Date(end).getTime() - new Date(start).getTime()) / (24 * 3600 * 1000)) + 1)
}

function routeStatus(plan: { startDate: string; endDate: string }) {
  const end = new Date(plan.endDate)
  const now = new Date()
  if (now > end) return '已完成'
  if (now >= new Date(plan.startDate) && now <= end) return '进行中'
  return '未出行'
}

function goRoute(id: number) {
  router.push(`/routes/${id}`)
}

function goCompanion(id: number) {
  router.push(`/companion/${id}`)
}

function handleDeleteFeed(id: number) {
  ElMessageBox.confirm('确定删除这条动态吗？', '提示', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    store.removeFeed(id)
    ElMessage.success('已删除')
  }).catch(() => {})
}

function handleDeactivate() {
  ElMessageBox.confirm('账号注销后数据无法恢复，确定要继续吗？', '注销账号', {
    confirmButtonText: '确认注销',
    cancelButtonText: '取消',
    type: 'warning',
  }).then(() => {
    ElMessage.info('账号注销功能需后端支持，请联系管理员')
  }).catch(() => {})
}

const removingFavoriteId = ref<string | null>(null)

async function handleRemoveFavorite(f: FavoriteItem) {
  const key = `${f.type}-${f.id}`
  if (removingFavoriteId.value === key) return
  removingFavoriteId.value = key
  try {
    await interactionsApi.unfavorite(f.type, f.id)
    store.removeFavorite(f.type, f.id)
    ElMessage.success('已取消收藏')
  } catch (e: any) {
    ElMessage.error(e?.message || '取消收藏失败，请稍后重试')
  } finally {
    removingFavoriteId.value = null
  }
}

function favoriteTypeLabel(type: string) {
  const map: Record<string, string> = {
    note: '游记',
    route: '路线',
    companion: '结伴',
    feed: '动态',
  }
  return map[type] || type
}

function goToFavorite(f: FavoriteItem) {
  if (f.type === 'note') router.push({ name: 'note-detail', params: { id: f.id } })
  else if (f.type === 'route') router.push({ name: 'route-detail', params: { id: f.id } })
  else if (f.type === 'companion') router.push({ name: 'companion-detail', params: { id: f.id } })
  else if (f.type === 'feed') router.push({ name: 'feed', query: { highlight: f.id } })
}
</script>

<template>
  <div class="profile-page">
    <div class="profile-body">
      <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
        <nav class="sidebar-nav">
          <button
            v-for="item in menuItems"
            :key="item.key"
            type="button"
            class="nav-item"
            :class="{ active: activeTab === item.key }"
            @click="activeTab = item.key"
          >
            {{ item.label }}
          </button>
        </nav>
      </aside>

      <main class="content">
        <!-- 个人信息 -->
        <section v-show="activeTab === 'info'" class="panel">
          <div class="panel-head">
            <h2>个人信息</h2>
            <el-button type="primary" @click="editDialogVisible = true">编辑资料</el-button>
          </div>
          <div class="info-card">
            <div class="info-avatar-wrap">
              <el-avatar :size="80" class="info-avatar">
                <img v-if="store.me?.avatar" :src="store.me.avatar" alt="avatar" />
                <span v-else>{{ (store.me?.nickname || auth.nickname || '我').charAt(0) }}</span>
              </el-avatar>
            </div>
            <div class="info-meta">
              <h3 class="info-name">{{ store.me?.nickname || auth.nickname || '旅友' }}</h3>
              <p class="info-row">
                <span v-if="store.me?.gender">{{ store.me.gender === 'male' ? '男' : store.me.gender === 'female' ? '女' : '其他' }}</span>
                <span v-if="store.me?.age"> · {{ store.me.age }} 岁</span>
                <span v-if="store.me?.city"> · {{ store.me.city }}</span>
              </p>
              <div v-if="store.me?.intro" class="info-intro">{{ store.me.intro }}</div>
              <div v-if="store.me?.slogan" class="info-slogan">「{{ store.me.slogan }}」</div>
              <div class="info-reputation">
                <el-tag type="warning" effect="plain">{{ levelLabel }}</el-tag>
                <span class="score">积分 {{ store.me?.reputationScore ?? 0 }}</span>
                <div class="progress-wrap">
                  <el-progress :percentage="store.reputationProgress" :show-text="false" stroke-width="8" />
                </div>
              </div>
            </div>
          </div>
          <div class="quick-links">
            <el-button @click="router.push('/routes/create')">规划路线</el-button>
            <el-button type="primary" @click="router.push('/companion/create')">发布结伴</el-button>
            <el-button @click="router.push('/community')">去社区</el-button>
          </div>
        </section>

        <!-- 我的路线 -->
        <section v-show="activeTab === 'routes'" class="panel">
          <h2>我的路线</h2>
          <div v-if="store.myRoutes.length === 0" class="empty">
            <el-empty description="暂无路线" />
            <el-button type="primary" @click="router.push('/routes/create')">去规划</el-button>
          </div>
          <div v-else class="card-grid">
            <article
              v-for="r in store.myRoutes"
              :key="r.id"
              class="route-card"
              @click="goRoute(r.id)"
            >
              <div class="route-cover">
                <img :src="`https://picsum.photos/seed/r${r.id}/400/200`" :alt="r.title" />
              </div>
              <div class="route-body">
                <h4>{{ r.title || r.destination }}</h4>
                <p class="route-meta">{{ r.startDate }} – {{ r.endDate }} · {{ daysBetween(r.startDate, r.endDate) }} 天</p>
                <el-tag size="small" :type="routeStatus(r) === '已完成' ? 'success' : routeStatus(r) === '进行中' ? 'warning' : 'info'">
                  {{ routeStatus(r) }}
                </el-tag>
              </div>
            </article>
          </div>
        </section>

        <!-- 我的结伴 -->
        <section v-show="activeTab === 'companion'" class="panel">
          <h2>我的结伴</h2>
          <div v-if="store.myCompanion.length === 0" class="empty">
            <el-empty description="暂无结伴" />
            <el-button type="primary" @click="router.push('/companion/create')">发布结伴</el-button>
          </div>
          <div v-else class="card-grid">
            <article
              v-for="p in store.myCompanion"
              :key="p.id"
              class="companion-card"
              @click="goCompanion(p.id)"
            >
              <h4>{{ p.destination }}</h4>
              <p class="companion-meta">{{ p.startDate }} – {{ p.endDate }}</p>
              <p class="companion-meta">人数 {{ p.minPeople ?? 0 }}–{{ p.maxPeople ?? 0 }} 人</p>
              <el-tag size="small">{{ p.status || 'open' }}</el-tag>
            </article>
          </div>
        </section>

        <!-- 我的收藏 -->
        <section v-show="activeTab === 'favorites'" class="panel">
          <h2>我的收藏</h2>
          <div v-if="store.favorites.length === 0" class="empty">
            <el-empty description="暂无收藏" />
          </div>
          <div v-else class="card-grid">
            <div v-for="f in store.favorites" :key="`${f.type}-${f.id}`" class="fav-card">
              <span class="fav-card-title" @click="goToFavorite(f)">{{ f.title || f.destination || f.nickname || `${favoriteTypeLabel(f.type)} #${f.id}` }}</span>
              <el-button
                text
                type="danger"
                size="small"
                :loading="removingFavoriteId === (f.type + '-' + f.id)"
                @click.stop="handleRemoveFavorite(f)"
              >
                取消收藏
              </el-button>
            </div>
          </div>
        </section>

        <!-- 我的动态 -->
        <section v-show="activeTab === 'feeds'" class="panel">
          <h2>我的动态</h2>
          <div v-if="store.myFeeds.length === 0" class="empty">
            <el-empty description="暂无动态" />
            <el-button type="primary" @click="router.push('/community')">去社区发布</el-button>
          </div>
          <ul v-else class="feed-list">
            <li v-for="f in store.myFeeds" :key="f.id" class="feed-row">
              <p class="feed-content">{{ f.content }}</p>
              <span class="feed-time">{{ f.createdAt }}</span>
              <el-button text type="danger" size="small" @click="handleDeleteFeed(f.id)">删除</el-button>
            </li>
          </ul>
        </section>

        <!-- 账户设置 -->
        <section v-show="activeTab === 'settings'" class="panel">
          <h2>账户设置</h2>
          <el-card shadow="never" class="settings-card">
            <p class="text-subtle">修改密码、绑定/解绑第三方账号需后端支持，敬请期待。</p>
          </el-card>
        </section>

        <!-- 安全与隐私 -->
        <section v-show="activeTab === 'security'" class="panel">
          <h2>安全与隐私</h2>
          <el-card shadow="never" class="settings-card">
            <el-form label-width="140px">
              <el-form-item label="谁可以看到我的动态">
                <el-select model-value="public" class="w-200">
                  <el-option value="public" label="所有人" />
                  <el-option value="friends" label="好友" />
                  <el-option value="private" label="仅自己" />
                </el-select>
              </el-form-item>
              <el-form-item label="谁可以看到结伴信息">
                <el-select model-value="public" class="w-200">
                  <el-option value="public" label="所有人" />
                  <el-option value="friends" label="好友" />
                </el-select>
              </el-form-item>
            </el-form>
          </el-card>
          <el-card shadow="never" class="settings-card danger-zone">
            <h4>注销账号</h4>
            <p class="text-subtle">注销后数据无法恢复。</p>
            <el-button type="danger" plain @click="handleDeactivate">申请注销</el-button>
          </el-card>
        </section>
      </main>
    </div>

    <EditProfileDialog
      :visible="editDialogVisible"
      :profile="store.me"
      @update:visible="editDialogVisible = $event"
      @updated="onProfileUpdated"
    />
  </div>
</template>

<style scoped>
.profile-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f0f9ff 0%, #f8fafc 16%);
  padding-bottom: 48px;
}

.profile-body {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 20px;
  display: grid;
  grid-template-columns: 200px 1fr;
  gap: 32px;
  align-items: start;
}

@media (max-width: 768px) {
  .profile-body {
    grid-template-columns: 1fr;
  }
}

.sidebar {
  position: sticky;
  top: 88px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 12px 0;
}

.sidebar-nav {
  display: flex;
  flex-direction: column;
}

.nav-item {
  padding: 12px 20px;
  text-align: left;
  font-size: 15px;
  color: #475569;
  background: none;
  border: none;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.nav-item:hover {
  background: #f1f5f9;
  color: #0d9488;
}

.nav-item.active {
  background: #ccfbf1;
  color: #0d9488;
  font-weight: 500;
}

.content {
  min-width: 0;
}

.panel {
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  padding: 24px;
}

.panel-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.panel h2 {
  margin: 0 0 20px;
  font-size: 18px;
  font-weight: 600;
  color: #1e293b;
}

.panel-head h2 {
  margin: 0;
}

.info-card {
  display: flex;
  gap: 24px;
  margin-bottom: 24px;
}

.info-avatar-wrap {
  flex-shrink: 0;
}

.info-avatar {
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-size: 32px;
  font-weight: 600;
}

.info-meta {
  min-width: 0;
}

.info-name {
  margin: 0 0 8px;
  font-size: 22px;
  font-weight: 600;
  color: #1e293b;
}

.info-row {
  margin: 0 0 8px;
  font-size: 14px;
  color: #64748b;
}

.info-intro {
  margin: 0 0 6px;
  font-size: 14px;
  color: #475569;
  line-height: 1.5;
}

.info-slogan {
  margin: 0 0 12px;
  font-size: 14px;
  color: #0d9488;
  font-style: italic;
}

.info-reputation {
  margin-top: 12px;
}

.info-reputation .score {
  margin-left: 8px;
  font-size: 14px;
  color: #64748b;
}

.progress-wrap {
  margin-top: 8px;
  max-width: 200px;
}

.quick-links {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.empty {
  padding: 40px 20px;
  text-align: center;
}

.empty .el-button {
  margin-top: 16px;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 20px;
}

.route-card,
.companion-card {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s;
}

.route-card:hover,
.companion-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
}

.route-cover {
  height: 120px;
  background: #f1f5f9;
}

.route-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.route-body {
  padding: 14px;
}

.route-body h4,
.companion-card h4 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #1e293b;
}

.route-meta,
.companion-meta {
  margin: 0 0 4px;
  font-size: 13px;
  color: #64748b;
}

.companion-card {
  padding: 16px;
}

.fav-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-radius: 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.fav-card-title {
  cursor: pointer;
  flex: 1;
  min-width: 0;
}

.fav-card-title:hover {
  color: var(--el-color-primary);
}

.feed-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.feed-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 0;
  border-bottom: 1px solid #f1f5f9;
}

.feed-row:last-child {
  border-bottom: none;
}

.feed-content {
  flex: 1;
  margin: 0;
  font-size: 14px;
  color: #334155;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.feed-time {
  font-size: 12px;
  color: #94a3b8;
}

.settings-card {
  margin-bottom: 20px;
}

.w-200 {
  width: 200px;
}

.danger-zone {
  border-color: #fecaca;
}

.danger-zone h4 {
  margin: 0 0 8px;
  font-size: 15px;
  color: #1e293b;
}

.text-subtle {
  color: #64748b;
  font-size: 14px;
}
</style>
