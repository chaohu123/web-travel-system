<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../store'
import { routesApi } from '../api'

const auth = useAuthStore()

interface PlanSummary {
  id: number
  title: string
  destination: string
  startDate: string
  endDate: string
  budget: number
  peopleCount: number
  pace: string
}

const loading = ref(false)
const plans = ref<PlanSummary[]>([])
const errorMsg = ref('')
const router = useRouter()
const route = useRoute()

const HOT_FALLBACK: PlanSummary[] = [
  {
    id: 1,
    title: '京都奈良文化深度 5 日游',
    destination: '京都 / 奈良',
    startDate: '待定',
    endDate: '待定',
    budget: 8000,
    peopleCount: 2,
    pace: 'normal',
  },
  {
    id: 2,
    title: '云南大理丽江休闲 6 日',
    destination: '云南',
    startDate: '待定',
    endDate: '待定',
    budget: 5000,
    peopleCount: 2,
    pace: 'relax',
  },
]

const fetchMyPlans = async () => {
  loading.value = true
  errorMsg.value = ''
  try {
    if (auth.token) {
      const list = await routesApi.myPlans()
      plans.value = list || []
    } else {
      const list = await routesApi.hot(8)
      plans.value = (list && list.length ? list : HOT_FALLBACK) || []
    }
  } catch {
    if (auth.token) {
      plans.value = []
      errorMsg.value = '加载行程列表失败，请稍后重试'
    } else {
      // 未登录时，接口失败退回到本地热门示例
      plans.value = HOT_FALLBACK
      errorMsg.value = ''
    }
  } finally {
    loading.value = false
  }
}

const goDetail = (id: number) => {
  router.push(`/routes/${id}`)
}

/** 节奏英文转中文：fast→高，medium/normal→中，slow/relax→低 */
function paceLabel(pace?: string): string {
  if (!pace) return '中'
  const p = pace.toLowerCase()
  if (p === 'fast') return '高'
  if (p === 'slow' || p === 'relax') return '低'
  return '中' // medium, normal 等
}

onMounted(fetchMyPlans)
</script>

<template>
  <div class="min-h-screen bg-gradient-to-br from-slate-50 via-teal-50/30 to-indigo-50/30">
    <div class="max-w-4xl mx-auto px-4 sm:px-6 py-8">
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 mb-6">
        <h1 class="text-2xl font-bold text-slate-800">{{ auth.token ? '我的行程' : '热门线路' }}</h1>
        <router-link
          :to="{ name: 'route-create' }"
          class="px-4 py-2.5 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-teal-500 to-indigo-500 shadow-md hover:from-teal-600 hover:to-indigo-600 hover:shadow-lg transition-all w-fit inline-block text-center"
        >
          创建新行程
        </router-link>
      </div>

      <div
        v-if="errorMsg"
        class="rounded-2xl border border-red-200 bg-red-50 px-4 py-4 text-center text-red-700 text-sm"
      >
        {{ errorMsg }}
      </div>
      <template v-else>
        <div
          v-if="!auth.token"
          class="rounded-2xl border border-slate-200 bg-white/80 px-4 py-6 text-slate-600 mb-4"
        >
          <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
            <div>
              <div class="font-semibold text-slate-700">未登录状态</div>
              <div class="text-sm text-slate-500 mt-0.5">可先浏览热门线路；登录后可查看与管理「我的行程」。</div>
            </div>
            <router-link
              :to="{ name: 'login', query: { redirect: route.fullPath } }"
              class="inline-block px-4 py-2.5 rounded-xl text-sm font-semibold text-white bg-gradient-to-r from-teal-500 to-indigo-500"
            >
              去登录
            </router-link>
          </div>
        </div>

        <div v-if="loading" class="rounded-2xl border border-slate-200 bg-white/80 px-4 py-12 text-center text-slate-500">
          加载中...
        </div>
        <div
          v-else-if="plans.length === 0"
          class="rounded-2xl border border-slate-200 bg-white/80 px-4 py-12 text-center text-slate-500"
        >
          {{ auth.token ? '你还没有创建任何行程，先去创建一个吧～' : '暂无热门线路' }}
        </div>

        <div v-else class="space-y-3">
          <article
            v-for="plan in plans"
            :key="plan.id"
            class="rounded-2xl border border-slate-200/80 bg-white shadow-sm overflow-hidden flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 p-4 sm:p-5"
          >
            <div class="min-w-0 flex-1">
              <div class="flex flex-wrap items-baseline gap-2">
                <span class="text-lg font-semibold text-slate-800">{{ plan.destination }}</span>
                <span class="text-sm text-slate-500">
                  {{ plan.startDate }} ~ {{ plan.endDate }}
                </span>
              </div>
              <div class="mt-2 flex flex-wrap gap-3 text-sm text-slate-500">
                <span>预算：{{ plan.budget || 0 }} 元</span>
                <span>人数：{{ plan.peopleCount }} 人</span>
                <span>节奏：{{ paceLabel(plan.pace) }}</span>
              </div>
            </div>
            <div class="flex-shrink-0">
              <button
                type="button"
                class="px-4 py-2 rounded-xl text-sm font-medium text-slate-600 bg-slate-100 hover:bg-slate-200 hover:text-slate-800 transition-colors"
                @click="goDetail(plan.id)"
              >
                查看详情
              </button>
            </div>
          </article>
        </div>
      </template>
    </div>
  </div>
</template>

