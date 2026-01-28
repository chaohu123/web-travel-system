<script setup lang="ts">
import { ref, watch } from 'vue'
import HeroSearch from '../components/HeroSearch.vue'
import PreferenceSelector from '../components/PreferenceSelector.vue'
import RoutePlanning from '../components/RoutePlanning/RoutePlanning.vue'
import { useRoutePlanningStore } from '../store/routePlanning'

const store = useRoutePlanningStore()

// 与首页相同的偏好选择，用于映射到路线规划 store
const pace = ref('medium')
const interests = ref<string[]>([])
const submitLoading = ref(false)

function applyPreferencesToStore() {
  // 行程节奏 -> 游玩强度
  if (pace.value === 'fast') store.intensity = 'high'
  else if (pace.value === 'slow') store.intensity = 'relaxed'
  else store.intensity = 'moderate'

  // 兴趣偏好 -> 权重（简化映射）
  const selected = new Set(interests.value)
  store.interestWeights.nature = selected.has('自然风光') ? 80 : 40
  store.interestWeights.culture = selected.has('历史文化') ? 80 : 40
  store.interestWeights.food = selected.has('美食体验') ? 80 : 40
  store.interestWeights.shopping = selected.has('购物娱乐') ? 80 : 30
  store.interestWeights.relax = selected.has('休闲放松') ? 80 : 40
}

async function onSearchSubmit(payload: {
  destination: string
  startDate: string
  endDate: string
  travelers: number
}) {
  submitLoading.value = true
  try {
    // 将搜索条件写入路线规划 store
    if (payload.destination.trim()) {
      store.destinations = [payload.destination.trim()]
      store.routeName = `${payload.destination.trim()} 我的路线`
    }
    if (payload.startDate) store.startDate = payload.startDate
    if (payload.endDate) store.endDate = payload.endDate
    store.peopleCount = payload.travelers || store.peopleCount

    applyPreferencesToStore()
    // 直接触发一次 AI 行程生成
    store.generateItinerary()
  } finally {
    submitLoading.value = false
  }
}

watch([pace, interests], applyPreferencesToStore)
</script>

<template>
  <div class="min-h-screen bg-slate-50/80">
    <!-- 顶部截图区域：搜索 + 偏好 -->
    <section class="max-w-5xl mx-auto px-4 sm:px-6 pt-10 pb-4">
      <HeroSearch :loading="submitLoading" @submit="onSearchSubmit" />
    </section>
    <PreferenceSelector v-model:pace="pace" v-model:interests="interests" />

    <!-- 下方为原有路线规划工作台 -->
    <section class="mt-4">
      <RoutePlanning />
    </section>
  </div>
</template>
