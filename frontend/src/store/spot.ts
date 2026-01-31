import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { commentsApi } from '../api'
import type { CommentItem } from '../api'

export type SpotTag = '自然景观' | '历史文化' | '亲子' | '网红打卡' | '美食体验' | '夜景'

export interface SpotDetail {
  id: number
  name: string
  city: string
  address: string
  intro: string
  tags: SpotTag[]
  rating: number
  ratingCount: number
  openHours: string
  ticketPrice: string
  suggestedDuration: string
  bestSeason: string
  // 用于地图（示例坐标）
  lng: number
  lat: number
  images: string[]
}

export interface SpotRecommend {
  id: number
  name: string
  desc: string
  cover: string
  type: 'spot' | 'route'
  meta: string
}

function seededRandom(seed: number) {
  // 简单可复现随机数
  let x = Math.sin(seed) * 10000
  return () => {
    x = Math.sin(x) * 10000
    return x - Math.floor(x)
  }
}

function hashString(s: string) {
  let hash = 0
  for (let i = 0; i < s.length; i++) {
    hash = (hash * 31 + s.charCodeAt(i)) >>> 0
  }
  return hash >>> 0
}

function guessCityFromLocation(location?: string) {
  const s = (location || '').trim()
  if (!s) return ''
  // 简单提取：优先取 “xx市”，其次取前 2-3 个字
  const m = s.match(/(.{2,8}?市)/)
  if (m?.[1]) return m[1].replace('市', '')
  return s.slice(0, 2)
}

function buildIntroByName(name: string) {
  const n = name.trim() || '该景点'
  return [
    `【历史背景】`,
    `${n}在当地通常被视为代表性的打卡点之一，既有观景动线也有适合慢游的区域。建议先在入口处拿一份导览图（或提前在地图 App 收藏关键点），按动线游览更省体力。`,
    ``,
    `【最佳玩法】`,
    `- 上午：光线更柔、游客相对少，适合“主景位”拍照与走完整体动线`,
    `- 下午：可把周边街区/公园/商圈串联安排，体验当地生活与餐饮`,
    `- 晚上：如果有夜景/灯光，建议预留 30–60 分钟等待天色变暗（蓝调时刻最好看）`,
    ``,
    `【拍照机位】`,
    `- 主入口视角：适合拍“到此一游”与全景`,
    `- 高处观景点：建议 0.5x/超广角，画面更震撼`,
    `- 小路/转角：利用框景（门/窗/树影）更出片`,
    ``,
    `【避坑建议】`,
    `- 节假日务必错峰：9:00 前或 16:00 后体验更好`,
    `- 如需预约/实名，提前 1–3 天查看官方渠道`,
    `- 餐饮/纪念品建议多对比，景区入口附近价格通常更高`,
  ].join('\n')
}

function mockSpot(
  id: number,
  overrides?: {
    name?: string
    location?: string
    city?: string
  }
): SpotDetail {
  const overrideName = (overrides?.name || '').trim()
  const overrideLocation = (overrides?.location || '').trim()
  const seed = overrideName ? hashString(overrideName) : id
  const rnd = seededRandom(seed)
  const cities = ['杭州', '成都', '西安', '厦门', '北京', '上海', '丽江', '桂林']
  const city =
    (overrides?.city || '').trim() ||
    guessCityFromLocation(overrideLocation) ||
    cities[Math.floor(rnd() * cities.length)]
  const names = ['西湖', '宽窄巷子', '兵马俑', '鼓浪屿', '故宫博物院', '外滩', '丽江古城', '漓江']
  const name = overrideName || names[id % names.length] + (id % 3 === 0 ? '（景区）' : '')
  const tags: SpotTag[] = ([
    '自然景观',
    '历史文化',
    '亲子',
    '网红打卡',
    '美食体验',
    '夜景',
  ] as SpotTag[]).filter(() => rnd() > 0.45).slice(0, 4)
  const rating = Math.round((4.2 + rnd() * 0.7) * 10) / 10
  const ratingCount = 200 + Math.floor(rnd() * 6000)

  // 根据城市给一个“接近真实”的示例经纬度，避免完全随机到其它国家/城市
  const cityCoords: Record<string, [number, number]> = {
    // 中国主要城市
    杭州: [120.1552, 30.2741],
    成都: [104.0665, 30.5723],
    西安: [108.9398, 34.3416],
    厦门: [118.0894, 24.4798],
    北京: [116.4074, 39.9042],
    上海: [121.4737, 31.2304],
    丽江: [100.2278, 26.8550],
    桂林: [110.2900, 25.2736],
    // 日本常见旅游城市
    京都: [135.7681, 35.0116],
    奈良: [135.8048, 34.6851],
    大阪: [135.5023, 34.6937],
    东京: [139.6917, 35.6895],
    札幌: [141.3545, 43.0621],
    福冈: [130.4017, 33.5904],
    冲绳: [127.6811, 26.2124],
    名古屋: [136.9066, 35.1814],
    // 其它热门海外目的地（示例）
    首尔: [126.9780, 37.5665],
    釜山: [129.0756, 35.1796],
    曼谷: [100.5018, 13.7563],
    新加坡: [103.8198, 1.3521],
    巴厘岛: [115.1889, -8.4095],
    伦敦: [-0.1276, 51.5074],
    巴黎: [2.3522, 48.8566],
    纽约: [-74.0060, 40.7128],
  }
  const [baseLng, baseLat] = cityCoords[city] || [105.0, 35.0]
  // 在城市附近做一个很小的随机偏移，保证同一景点每次打开位置一致
  const lng = baseLng + (rnd() - 0.5) * 0.4
  const lat = baseLat + (rnd() - 0.5) * 0.4

  return {
    id,
    name,
    city,
    address: overrideLocation || `${city} 市中心附近 · 示意地址 ${Math.floor(rnd() * 99) + 1} 号`,
    intro: buildIntroByName(name),
    tags: tags.length ? tags : ['网红打卡', '历史文化'],
    rating,
    ratingCount,
    openHours: '09:00 - 17:30（旺季延长至 18:30）',
    ticketPrice: rnd() > 0.5 ? '免费（部分展馆收费）' : `¥${Math.floor(40 + rnd() * 120)} / 人`,
    suggestedDuration: `${Math.floor(2 + rnd() * 4)} - ${Math.floor(4 + rnd() * 6)} 小时`,
    bestSeason: ['春季', '夏季', '秋季', '冬季'][Math.floor(rnd() * 4)],
    lng,
    lat,
    images: [
      `https://picsum.photos/seed/spot${id}-1/1200/680`,
      `https://picsum.photos/seed/spot${id}-2/1200/680`,
      `https://picsum.photos/seed/spot${id}-3/1200/680`,
    ],
  }
}

export const useSpotStore = defineStore('spot', () => {
  const loading = ref(false)
  const commentsLoading = ref(false)
  const recommendLoading = ref(false)

  const detail = ref<SpotDetail | null>(null)
  const comments = ref<CommentItem[]>([])
  const isFavorited = ref(false)

  const recommendSpots = ref<SpotRecommend[]>([])

  const ratingDist = computed(() => {
    const total = detail.value?.ratingCount ?? 0
    if (!total) return [0, 0, 0]
    // 简单生成：好评/中评/差评
    const good = Math.floor(total * 0.72)
    const mid = Math.floor(total * 0.2)
    const bad = Math.max(0, total - good - mid)
    return [good, mid, bad]
  })

  async function fetchDetail(
    id: number,
    overrides?: {
      name?: string
      location?: string
      city?: string
    }
  ) {
    loading.value = true
    try {
      // TODO: 后端接入时替换为真实接口，如：api.get(`/spots/${id}`)
      detail.value = mockSpot(id, overrides)
    } finally {
      loading.value = false
    }
  }

  async function fetchComments(id: number) {
    commentsLoading.value = true
    try {
      comments.value = await commentsApi.list('spot', id)
    } catch {
      comments.value = []
    } finally {
      commentsLoading.value = false
    }
  }

  async function fetchRecommend(id: number) {
    recommendLoading.value = true
    try {
      const base = mockSpot(id)
      recommendSpots.value = [
        {
          id: id + 11,
          name: `${base.city} · 相似景点 A`,
          desc: '景观相似 / 距离较近 / 适合同日安排',
          cover: `https://picsum.photos/seed/reco-spot-${id}-a/640/420`,
          type: 'spot',
          meta: '自然景观 · 2-3 小时',
        },
        {
          id: id + 12,
          name: `${base.city} · 相似景点 B`,
          desc: '同主题玩法 / 适合拍照打卡',
          cover: `https://picsum.photos/seed/reco-spot-${id}-b/640/420`,
          type: 'spot',
          meta: '网红打卡 · 1-2 小时',
        },
        {
          id: 1000 + id,
          name: `${base.city} · 关联路线推荐`,
          desc: '包含该景点的经典路线，可一键复用',
          cover: `https://picsum.photos/seed/reco-route-${id}/640/420`,
          type: 'route',
          meta: '3-5 天 · 预算约 5000-8000',
        },
      ]
    } finally {
      recommendLoading.value = false
    }
  }

  function toggleFavorite() {
    isFavorited.value = !isFavorited.value
  }

  /** 根据景点 ID 获取简要信息（用于私信发送景点，含封面图与坐标） */
  function getSpotBrief(
    id: number,
    overrides?: { name?: string; city?: string; location?: string }
  ): { name: string; location: string; imageUrl: string; lng: number; lat: number } {
    const spot = mockSpot(id, overrides)
    const cover = spot.images?.[0] || `https://picsum.photos/seed/spot${id}/400/250`
    return {
      name: spot.name,
      location: spot.address || spot.city,
      imageUrl: cover,
      lng: spot.lng,
      lat: spot.lat,
    }
  }

  return {
    loading,
    commentsLoading,
    recommendLoading,
    detail,
    comments,
    isFavorited,
    recommendSpots,
    ratingDist,
    fetchDetail,
    fetchComments,
    fetchRecommend,
    toggleFavorite,
    getSpotBrief,
  }
})

