<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { routesApi } from '../api'
import type { PlanResponse } from '../api'
import { useSpotStore } from '../store/spot'
import { loadAmapScript, initAmapMap, addMarker, geocode } from '../utils/amap'
import { formatDateTime } from '../utils/format'

const route = useRoute()
const router = useRouter()
const store = useSpotStore()

// 返回上一页
function goBack() {
  if (window.history.length > 1) {
    router.go(-1)
  } else {
    router.push('/')
  }
}

const spotId = computed(() => {
  const id = route.params.id
  return id && !Array.isArray(id) ? Number(id) : 0
})

const introExpanded = ref(false)
const commentTab = ref<'latest' | 'useful' | 'withPic'>('latest')

// 加入路线：选择已有路线或新建
const addDialogVisible = ref(false)
const myRoutesLoading = ref(false)
const myRoutes = ref<PlanResponse[]>([])
const selectedRouteId = ref<number | null>(null)

// 图片预览
const previewVisible = ref(false)
const previewUrl = ref('')

// 评分分布图（ECharts）
const distRef = ref<HTMLDivElement | null>(null)
let distChart: echarts.ECharts | null = null

// 地图（高德地图）
const mapRef = ref<HTMLDivElement | null>(null)
let amapInstance: any = null
let marker: any = null

const displayIntro = computed(() => {
  const text = store.detail?.intro || ''
  if (introExpanded.value) return text
  return text.length > 80 ? text.slice(0, 80) + '…' : text
})

const ratingText = computed(() => `${store.detail?.rating ?? 0} / 5（${store.detail?.ratingCount ?? 0} 人）`)

const filteredComments = computed(() => {
  const list = store.comments || []
  if (commentTab.value === 'withPic') {
    // 目前后端评论数据无图片字段，先用“包含关键词”做占位筛选
    return list.filter((c) => (c.content || '').includes('图') || (c.content || '').includes('照片'))
  }
  if (commentTab.value === 'useful') {
    // 占位：按分数/时间简单排序
    return [...list].sort((a, b) => (b.score ?? 0) - (a.score ?? 0))
  }
  return [...list].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
})

function openPreview(url: string) {
  previewUrl.value = url
  previewVisible.value = true
}

function shareSpot() {
  navigator.clipboard
    .writeText(window.location.href)
    .then(() => ElMessage.success('已复制分享链接'))
    .catch(() => ElMessage.info('复制失败，请手动复制地址栏链接'))
}

function toggleFavorite() {
  store.toggleFavorite()
  ElMessage.success(store.isFavorited ? '已收藏景点' : '已取消收藏')
}

async function openAddToRoute() {
  addDialogVisible.value = true
  myRoutesLoading.value = true
  try {
    myRoutes.value = await routesApi.myPlans()
  } catch {
    myRoutes.value = []
  } finally {
    myRoutesLoading.value = false
  }
}

function confirmAddToRoute() {
  if (!selectedRouteId.value) {
    ElMessage.warning('请选择要加入的路线')
    return
  }
  // TODO: 接入后端接口，把景点添加到路线（当前仅前端演示）
  ElMessage.success('已加入路线（示例前端逻辑）')
  addDialogVisible.value = false
}

function createNewRoute() {
  const d = store.detail
  router.push({
    name: 'route-create',
    query: {
      addSpotId: String(d?.id ?? ''),
      destination: d?.city ?? '',
      spotName: d?.name ?? '',
    },
  })
  addDialogVisible.value = false
}

function goRecommend(item: { type: 'spot' | 'route'; id: number }) {
  if (item.type === 'route') router.push(`/routes/${item.id}`)
  else router.push(`/spots/${item.id}`)
}

function renderRatingDist() {
  if (!distRef.value) return
  if (!distChart) distChart = echarts.init(distRef.value)

  const [good, mid, bad] = store.ratingDist
  distChart.setOption({
    grid: { left: 20, right: 20, top: 10, bottom: 18 },
    xAxis: {
      type: 'category',
      data: ['好评', '中评', '差评'],
      axisLabel: { color: '#64748b' },
      axisLine: { lineStyle: { color: '#e2e8f0' } },
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#94a3b8' },
      splitLine: { lineStyle: { color: '#f1f5f9' } },
    },
    tooltip: { trigger: 'axis' },
    series: [
      {
        type: 'bar',
        data: [good, mid, bad],
        barWidth: 28,
        itemStyle: {
          borderRadius: [10, 10, 0, 0],
          color: (params: any) => (['#22c55e', '#f59e0b', '#ef4444'][params.dataIndex]),
        },
      },
    ],
  })
}

async function renderMap() {
  const d = store.detail
  if (!mapRef.value || !d) return

  try {
    await loadAmapScript()

    // 等待地图容器真正有尺寸（某些情况下首次进入页面容器短暂为 0x0，会导致地图“空白”）
    const waitForSize = async (el: HTMLElement, maxFrames = 30) => {
      for (let i = 0; i < maxFrames; i++) {
        const rect = el.getBoundingClientRect()
        if (rect.width > 0 && rect.height > 0) return
        await new Promise<void>((r) => requestAnimationFrame(() => r()))
      }
    }
    await waitForSize(mapRef.value)

    // 1. 优先使用景点自身经纬度（来自后端或 mock），保证位置与景点一一对应
    let center: [number, number] | null = null
    if (typeof d.lng === 'number' && typeof d.lat === 'number' && !Number.isNaN(d.lng) && !Number.isNaN(d.lat)) {
      center = [d.lng, d.lat]
    }

    // 2. 如果没有经纬度，再退回到地理编码（根据名称/地址估算位置）
    if (!center) {
      const geoCandidates = [
        d.city && d.name ? `${d.city}${d.name}` : '',
        d.city && d.address ? `${d.city}${d.address}` : '',
        d.address ? d.address.split('·')[0] : '',
        d.city || '',
      ].filter((s) => !!s.trim())

      for (const candidate of geoCandidates) {
        const coords = await geocode(candidate)
        if (coords) {
          center = coords
          break
        }
      }
    }

    // 3. 全部失败时，用默认北京中心，至少保证地图能出图
    if (!center) {
      center = [116.397428, 39.90923]
    }

    // 初始化地图（用 2D + normal，避免部分环境底图黑屏）
    amapInstance = initAmapMap(mapRef.value, center, 15, {
      viewMode: '2D',
      mapStyle: 'amap://styles/normal',
      forceTileLayer: true,
    })

    // 添加标记点
    marker = addMarker(
      amapInstance,
      center,
      d.name,
      `<div style="padding: 8px; min-width: 200px;">
        <b style="font-size: 14px; color: #0f172a;">${d.name}</b><br/>
        <span style="font-size: 12px; color: #64748b;">${d.address}</span>
      </div>`
    )
    // 设置标记样式
    marker.setIcon(
      new window.AMap.Icon({
        size: new window.AMap.Size(40, 40),
        image: `data:image/svg+xml;base64,${btoa(
          `<svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40">
            <circle cx="20" cy="20" r="16" fill="#22c55e" stroke="#fff" stroke-width="3"/>
            <circle cx="20" cy="20" r="8" fill="#fff"/>
          </svg>`
        )}`,
      })
    )
    // 使用默认锚点，让圆形点精确位于实际经纬度位置
    marker.setOffset(new window.AMap.Pixel(0, 0))

    // 调整地图视野
    // 等地图渲染完成再 fitView / resize，避免“黑屏但无报错”
    amapInstance.on('complete', () => {
      try {
        amapInstance.setFitView([marker])
        amapInstance.resize()
      } catch {}
    })
  } catch (error) {
    console.error('初始化地图失败:', error)
    ElMessage.warning('地图加载失败，请检查高德地图API配置')
  }
}

function resizeCharts() {
  distChart?.resize()
  amapInstance?.getSize()
}

async function loadAll() {
  if (!spotId.value) return
  const q = route.query
  const name = typeof q.name === 'string' ? q.name : ''
  const location = typeof q.location === 'string' ? q.location : ''
  const city = typeof q.city === 'string' ? q.city : ''
  await store.fetchDetail(spotId.value, { name, location, city })
  await store.fetchComments(spotId.value)
  await store.fetchRecommend(spotId.value)
  // 等待 DOM 根据 store.detail 完成条件渲染后，再初始化图表/地图
  // 否则 distRef / mapRef 可能还是 null，导致“区域空白但无报错”
  await nextTick()

  renderRatingDist()

  // 加载高德地图API并渲染地图
  try {
    await loadAmapScript()
    await renderMap()
  } catch (error) {
    console.warn('高德地图加载失败:', error)
    ElMessage.warning('地图加载失败，请检查高德地图API配置')
  }
}

watch(spotId, loadAll)

onMounted(async () => {
  await loadAll()
  window.addEventListener('resize', resizeCharts)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeCharts)
  distChart?.dispose()
  distChart = null
  // 清理高德地图实例
  if (amapInstance) {
    amapInstance.destroy()
    amapInstance = null
  }
  marker = null
})
</script>

<template>
  <div class="spot-page">
    <!-- 返回按钮 -->
    <div class="back-button-container">
      <el-button :icon="ArrowLeft" circle @click="goBack" class="back-button" />
    </div>

    <div v-if="store.loading" class="loading text-subtle">加载中…</div>

    <template v-else-if="store.detail">
      <!-- 2. 景点基础信息区 -->
      <section class="hero">
        <div class="hero-grid">
          <div class="media">
            <el-carousel height="280px" indicator-position="outside" class="carousel">
              <el-carousel-item v-for="(img, i) in store.detail.images" :key="i">
                <div class="img" :style="{ backgroundImage: `url(${img})` }" @click="openPreview(img)" />
              </el-carousel-item>
            </el-carousel>
          </div>

          <div class="info">
            <div class="title-row">
              <h1 class="name">{{ store.detail.name }}</h1>
              <div class="tags">
                <el-tag v-for="t in store.detail.tags" :key="t" size="small" effect="plain">
                  {{ t }}
                </el-tag>
              </div>
            </div>

            <div class="rate-row">
              <el-rate :model-value="store.detail.rating" disabled allow-half />
              <span class="rate-text">{{ ratingText }}</span>
            </div>

            <div class="intro">
              <p class="intro-text">
                {{ displayIntro }}
                <button
                  v-if="(store.detail.intro || '').length > 80"
                  type="button"
                  class="intro-toggle"
                  @click="introExpanded = !introExpanded"
                >
                  {{ introExpanded ? '收起' : '展开' }}
                </button>
              </p>
            </div>

            <div class="actions">
              <el-button :type="store.isFavorited ? 'success' : 'primary'" @click="toggleFavorite">
                {{ store.isFavorited ? '已收藏' : '收藏景点' }}
              </el-button>
              <el-button type="primary" plain @click="openAddToRoute">加入路线</el-button>
              <el-button @click="shareSpot">分享景点</el-button>
            </div>

            <div class="address text-subtle">
              {{ store.detail.city }} · {{ store.detail.address }}
            </div>
          </div>
        </div>
      </section>

      <!-- 3. 关键信息速览区 -->
      <section class="quick-grid">
        <el-card shadow="hover" class="quick-card">
          <div class="q-icon">🕒</div>
          <div class="q-main">
            <div class="q-title">开放时间</div>
            <div class="q-value">{{ store.detail.openHours }}</div>
          </div>
        </el-card>
        <el-card shadow="hover" class="quick-card">
          <div class="q-icon">🎫</div>
          <div class="q-main">
            <div class="q-title">门票价格</div>
            <div class="q-value">{{ store.detail.ticketPrice }}</div>
          </div>
        </el-card>
        <el-card shadow="hover" class="quick-card">
          <div class="q-icon">⏳</div>
          <div class="q-main">
            <div class="q-title">建议游玩</div>
            <div class="q-value">{{ store.detail.suggestedDuration }}</div>
          </div>
        </el-card>
        <el-card shadow="hover" class="quick-card">
          <div class="q-icon">🍃</div>
          <div class="q-main">
            <div class="q-title">最佳季节</div>
            <div class="q-value">{{ store.detail.bestSeason }}</div>
          </div>
        </el-card>
      </section>

      <!-- 4/5：PC 分栏，移动单列 -->
      <section class="main-grid">
        <!-- 地理位置与交通 -->
        <div class="map-block">
          <h2 class="block-title">地理位置与交通</h2>
          <div class="map-card">
            <div ref="mapRef" class="map" />
            <div class="traffic">
              <div class="traffic-item">
                <span class="traffic-label">地址</span>
                <span class="traffic-val">{{ store.detail.address }}</span>
              </div>
              <div class="traffic-item">
                <span class="traffic-label">公交/地铁</span>
                <span class="traffic-val">约 30–50 分钟（从市中心）</span>
              </div>
              <div class="traffic-item">
                <span class="traffic-label">自驾</span>
                <span class="traffic-val">约 25–40 分钟 · 停车位充足</span>
              </div>
              <div class="traffic-item">
                <span class="traffic-label">打车</span>
                <span class="traffic-val">约 20–35 分钟 · 视路况而定</span>
              </div>
            </div>
          </div>
        </div>

        <!-- 用户评价与攻略 -->
        <div class="review-block">
          <h2 class="block-title">用户评价与攻略</h2>
          <el-card shadow="hover" class="review-card">
            <div class="review-top">
              <div class="dist">
                <div ref="distRef" class="dist-chart" />
              </div>
              <div class="dist-meta">
                <div class="dist-rate">
                  <div class="dist-num">{{ store.detail.rating }}</div>
                  <div class="dist-sub text-subtle">综合评分</div>
                </div>
                <div class="dist-count text-subtle">{{ store.detail.ratingCount }} 条评价</div>
              </div>
            </div>

            <el-tabs v-model="commentTab" class="comment-tabs">
              <el-tab-pane label="最新" name="latest" />
              <el-tab-pane label="最有用" name="useful" />
              <el-tab-pane label="带图" name="withPic" />
            </el-tabs>

            <div v-if="store.commentsLoading" class="text-subtle">加载评论中...</div>
            <div v-else-if="filteredComments.length === 0" class="text-subtle">
              暂无符合筛选的评论
            </div>
            <div v-else class="comment-list">
              <article v-for="c in filteredComments.slice(0, 6)" :key="c.id" class="comment-item">
                <div class="avatar">{{ (c.userName || 'U').charAt(0).toUpperCase() }}</div>
                <div class="c-main">
                  <div class="c-top">
                    <span class="c-name">{{ c.userName }}</span>
                    <el-rate v-if="c.score != null" :model-value="c.score" disabled allow-half size="small" />
                    <span class="c-time">{{ formatDateTime(c.createdAt) }}</span>
                  </div>
                  <p class="c-content">{{ c.content }}</p>
                  <div class="c-photos">
                    <!-- 评论图片：占位 -->
                    <img
                      v-for="i in 2"
                      :key="i"
                      class="c-photo"
                      :src="`https://picsum.photos/seed/cmt${c.id}-${i}/240/160`"
                      alt="comment"
                      @click="openPreview(`https://picsum.photos/seed/cmt${c.id}-${i}/1200/800`)"
                    />
                  </div>
                </div>
              </article>
              <div class="more">
                <el-button text type="primary" @click="ElMessage.info('查看全部评论：可扩展为弹窗/分页')">
                  查看全部评论
                </el-button>
              </div>
            </div>
          </el-card>
        </div>
      </section>

      <!-- 6. 推荐内容区 -->
      <section class="reco">
        <h2 class="block-title">推荐内容</h2>
        <div class="reco-grid">
          <el-card
            v-for="r in store.recommendSpots"
            :key="`${r.type}-${r.id}`"
            shadow="hover"
            class="reco-card"
            @click="goRecommend(r)"
          >
            <div class="reco-cover" :style="{ backgroundImage: `url(${r.cover})` }" />
            <div class="reco-body">
              <div class="reco-title">{{ r.name }}</div>
              <div class="reco-desc text-subtle">{{ r.desc }}</div>
              <div class="reco-meta text-subtle">{{ r.meta }}</div>
            </div>
          </el-card>
        </div>
      </section>

      <!-- 7. 底部提示区 -->
      <section class="tips-block">
        <h2 class="block-title">游玩小贴士</h2>
        <el-card shadow="hover" class="tips-card">
          <ul class="tips">
            <li>建议错峰出行：上午 9:00 前/下午 16:00 后人流更少。</li>
            <li>提前查看天气与交通管制信息，热门节假日需预留排队时间。</li>
            <li>拍照打卡点建议提前规划路线，避免重复折返。</li>
          </ul>
          <div class="notice text-subtle">官方公告：暂无临时关闭提醒（示例）</div>
        </el-card>
      </section>

      <!-- 移动端底部悬浮操作 -->
      <div class="mobile-bar">
        <el-button class="m-btn" :type="store.isFavorited ? 'success' : 'primary'" @click="toggleFavorite">
          {{ store.isFavorited ? '已收藏' : '收藏' }}
        </el-button>
        <el-button class="m-btn" type="primary" plain @click="openAddToRoute">加入路线</el-button>
        <el-button class="m-btn" @click="shareSpot">分享</el-button>
      </div>
    </template>

    <div v-else class="empty text-subtle">未找到景点信息</div>

    <!-- 加入路线弹窗 -->
    <el-dialog v-model="addDialogVisible" title="加入路线" width="520px">
      <div class="dialog-body">
        <p class="text-subtle">选择一个已有路线，或新建路线后再加入该景点。</p>
        <div class="dialog-row">
          <el-select
            v-model="selectedRouteId"
            placeholder="选择我的路线"
            class="w-full"
            :loading="myRoutesLoading"
            filterable
          >
            <el-option v-for="p in myRoutes" :key="p.id" :label="p.title || p.destination" :value="p.id" />
          </el-select>
        </div>
      </div>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button @click="createNewRoute">新建路线</el-button>
        <el-button type="primary" @click="confirmAddToRoute">加入</el-button>
      </template>
    </el-dialog>

    <!-- 图片预览弹窗 -->
    <el-dialog v-model="previewVisible" width="860px" class="preview-dialog">
      <img v-if="previewUrl" :src="previewUrl" class="preview-img" alt="preview" />
    </el-dialog>
  </div>
</template>

<style scoped>
.spot-page {
  min-height: 100vh;
  background: radial-gradient(circle at top left, #e0f2fe 0, transparent 55%),
    radial-gradient(circle at bottom right, #fef3c7 0, transparent 55%),
    #f8fafc;
  padding: 20px 16px 60px;
  position: relative;
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

@media (max-width: 768px) {
  .back-button-container {
    top: 70px;
    left: 12px;
  }
  
  .back-button {
    width: 36px;
    height: 36px;
  }
}

.loading,
.empty {
  max-width: 1100px;
  margin: 0 auto;
  padding: 56px 16px;
  text-align: center;
}

.hero {
  max-width: 1100px;
  margin: 0 auto 16px;
  background: #fff;
  border-radius: 24px;
  box-shadow: 0 18px 50px rgba(15, 23, 42, 0.12);
  padding: 16px;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(0, 1fr);
  gap: 16px;
}

.carousel :deep(.el-carousel__container) {
  border-radius: 18px;
}

.img {
  width: 100%;
  height: 280px;
  border-radius: 18px;
  background-size: cover;
  background-position: center;
  cursor: zoom-in;
  transition: transform 0.2s ease;
}
.img:hover {
  transform: scale(1.01);
}

.info {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 6px 6px;
}

.name {
  margin: 0 0 6px;
  font-size: 22px;
  font-weight: 750;
  color: #0f172a;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.rate-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.rate-text {
  color: #64748b;
  font-size: 13px;
}

.intro-text {
  margin: 0;
  color: #475569;
  line-height: 1.7;
  font-size: 13px;
}

.intro-toggle {
  margin-left: 8px;
  border: none;
  background: transparent;
  color: #0d9488;
  cursor: pointer;
  font-weight: 600;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.address {
  margin-top: 6px;
}

.quick-grid {
  max-width: 1100px;
  margin: 0 auto 16px;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.quick-card {
  border-radius: 18px;
}

.q-icon {
  font-size: 22px;
  margin-bottom: 8px;
}
.q-title {
  font-size: 12px;
  color: #94a3b8;
}
.q-value {
  margin-top: 2px;
  font-size: 14px;
  font-weight: 650;
  color: #0f172a;
  line-height: 1.4;
}

.main-grid {
  max-width: 1100px;
  margin: 0 auto 16px;
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 16px;
  align-items: start;
}

.block-title {
  margin: 0 0 12px;
  font-size: 18px;
  font-weight: 650;
  color: #0f172a;
}

.map-card {
  background: #020617;
  border-radius: 18px;
  padding: 12px;
  box-shadow: 0 18px 50px rgba(2, 6, 23, 0.35);
}

.map {
  width: 100%;
  height: 320px;
  border-radius: 12px;
}

.traffic {
  margin-top: 10px;
  padding: 10px 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.06);
  color: #e5e7eb;
  font-size: 12px;
  line-height: 1.6;
}

.traffic-item + .traffic-item {
  margin-top: 6px;
}

.traffic-label {
  display: inline-block;
  width: 72px;
  color: #cbd5f5;
}

.review-card {
  border-radius: 18px;
}

.review-top {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.8fr);
  gap: 14px;
  align-items: center;
}

.dist-chart {
  height: 160px;
}

.dist-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.dist-num {
  font-size: 26px;
  font-weight: 800;
  color: #0f172a;
  line-height: 1;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.comment-item {
  display: flex;
  gap: 12px;
}

.avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #5eead4, #0d9488);
  color: #fff;
  font-weight: 800;
  flex-shrink: 0;
}

.c-top {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.c-name {
  font-weight: 650;
  color: #0f172a;
}

.c-time {
  font-size: 12px;
  color: #94a3b8;
}

.c-content {
  margin: 6px 0 0;
  color: #475569;
  font-size: 13px;
  line-height: 1.6;
}

.c-photos {
  margin-top: 8px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.c-photo {
  width: 110px;
  height: 74px;
  object-fit: cover;
  border-radius: 10px;
  cursor: zoom-in;
  border: 1px solid #e2e8f0;
}

.more {
  text-align: right;
}

.reco {
  max-width: 1100px;
  margin: 0 auto 16px;
}

.reco-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.reco-card {
  border-radius: 18px;
  cursor: pointer;
  transition: transform 0.18s ease;
}
.reco-card:hover {
  transform: translateY(-2px);
}
.reco-cover {
  height: 140px;
  border-radius: 14px;
  background-size: cover;
  background-position: center;
}
.reco-body {
  padding-top: 10px;
}
.reco-title {
  font-weight: 700;
  color: #0f172a;
}
.reco-meta {
  margin-top: 6px;
  font-size: 12px;
}

.tips-block {
  max-width: 1100px;
  margin: 0 auto;
}

.tips-card {
  border-radius: 18px;
}

.tips {
  margin: 0;
  padding-left: 18px;
  color: #475569;
  line-height: 1.8;
  font-size: 13px;
}

.notice {
  margin-top: 10px;
}

.text-subtle {
  color: #64748b;
}

.dialog-body .dialog-row {
  margin-top: 10px;
}

.w-full {
  width: 100%;
}

.preview-img {
  width: 100%;
  border-radius: 12px;
}

.mobile-bar {
  display: none;
}

@media (max-width: 980px) {
  .hero-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .quick-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .main-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .reco-grid {
    grid-template-columns: minmax(0, 1fr);
  }

  .mobile-bar {
    position: fixed;
    left: 12px;
    right: 12px;
    bottom: 10px;
    z-index: 60;
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 8px;
    padding: 10px;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.88);
    backdrop-filter: blur(10px);
    border: 1px solid rgba(226, 232, 240, 0.9);
    box-shadow: 0 18px 50px rgba(15, 23, 42, 0.18);
  }

  .m-btn {
    width: 100%;
  }
}
</style>

