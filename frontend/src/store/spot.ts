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

function mockSpot(id: number): SpotDetail {
  const rnd = seededRandom(id)
  const cities = ['杭州', '成都', '西安', '厦门', '北京', '上海', '丽江', '桂林']
  const city = cities[Math.floor(rnd() * cities.length)]
  const names = ['西湖', '宽窄巷子', '兵马俑', '鼓浪屿', '故宫博物院', '外滩', '丽江古城', '漓江']
  const name = names[id % names.length] + (id % 3 === 0 ? '（景区）' : '')
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
  const lng = 100 + rnd() * 20
  const lat = 20 + rnd() * 20

  return {
    id,
    name,
    city,
    address: `${city} 市中心附近 · 示意地址 ${Math.floor(rnd() * 99) + 1} 号`,
    intro:
      '这里是一段用于毕业设计展示的景点简介示例。你可以在此呈现景点的历史背景、最佳游玩方式、拍照机位、避坑建议等内容。支持展开/收起以提升信息密度。',
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

  async function fetchDetail(id: number) {
    loading.value = true
    try {
      // TODO: 后端接入时替换为真实接口，如：api.get(`/spots/${id}`)
      detail.value = mockSpot(id)
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
  }
})

