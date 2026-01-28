<script setup lang="ts">
import { ref, computed } from 'vue'
import RouteHeader from './RouteHeader.vue'
import InterestSlider from './InterestSlider.vue'
import { useRoutePlanningStore } from '../../store/routePlanning'
import type { TransportType, IntensityType } from '../../store/routePlanning'

const store = useRoutePlanningStore()
const generating = ref(false)
const departCity = ref('')
const newDest = ref('')
const expandedDays = ref<Set<number>>(new Set([1]))

const transportOptions: { value: TransportType; label: string }[] = [
  { value: 'public', label: '公共交通' },
  { value: 'drive', label: '自驾' },
  { value: 'mixed', label: '混合' },
]
const intensityOptions: { value: IntensityType; label: string }[] = [
  { value: 'relaxed', label: '轻松' },
  { value: 'moderate', label: '适中' },
  { value: 'high', label: '高强度' },
]

const activeDays = computed(() => store.activeDays)
const variants = computed(() => store.variants)

function addDestination() {
  const t = newDest.value.trim()
  if (t) {
    store.addDestination(t)
    newDest.value = ''
  }
}

async function generateRoute() {
  generating.value = true
  await new Promise((r) => setTimeout(r, 800))
  store.generateItinerary()
  expandedDays.value = new Set(activeDays.value.map((d) => d.dayIndex))
  generating.value = false
}

function toggleDay(dayIndex: number) {
  const next = new Set(expandedDays.value)
  if (next.has(dayIndex)) next.delete(dayIndex)
  else next.add(dayIndex)
  expandedDays.value = next
}

function formatDuration(m: number) {
  const h = Math.floor(m / 60)
  const min = m % 60
  return min ? `${h} 小时 ${min} 分钟` : `${h} 小时`
}

function onDragStart(e: DragEvent, dayIndex: number, index: number) {
  if (!e.dataTransfer) return
  e.dataTransfer.effectAllowed = 'move'
  e.dataTransfer.setData('text/plain', JSON.stringify({ dayIndex, index }))
  e.dataTransfer.dropEffect = 'move'
  ;(e.target as HTMLElement).classList.add('opacity-50')
}

function onDragEnd(e: DragEvent) {
  ;(e.target as HTMLElement).classList.remove('opacity-50')
}

function onDragOver(e: DragEvent) {
  e.preventDefault()
  if (e.dataTransfer) e.dataTransfer.dropEffect = 'move'
}

function onDrop(e: DragEvent, dayIndex: number, toIndex: number) {
  e.preventDefault()
  try {
    const raw = e.dataTransfer?.getData('text/plain')
    if (!raw) return
    const { dayIndex: fromDay, index: fromIndex } = JSON.parse(raw)
    if (fromDay !== dayIndex) return
    store.reorderDayItems(dayIndex, fromIndex, toIndex)
  } catch {
    // ignore
  }
}

function removeItem(dayIndex: number, itemId: string) {
  store.removeDayItem(dayIndex, itemId)
}

function saveRoute() {
  console.log('保存路线')
}
function inviteBuddies() {
  console.log('邀请旅友')
}
function publishCompanion() {
  console.log('发布结伴')
}
function exportItinerary() {
  console.log('导出行程')
}
function saveAndGoCompanion() {
  console.log('保存并进入结伴')
}
</script>

<template>
  <div class="route-planning min-h-screen flex flex-col bg-gradient-to-br from-slate-50 via-teal-50/30 to-indigo-50/30">
    <RouteHeader class="mx-4 mt-4 mb-2" />

    <div class="flex-1 flex flex-col lg:flex-row gap-4 px-4 pb-4 min-h-0">
      <!-- 左侧配置区 -->
      <aside class="w-full lg:w-[30%] lg:min-w-[280px] lg:max-w-[380px] flex-shrink-0 space-y-4 overflow-y-auto">
        <el-card shadow="never" class="rounded-2xl bg-white/95 border border-slate-200/80">
          <h3 class="text-sm font-semibold text-slate-800 mb-3">行程基础信息</h3>
          <div class="mb-3 space-y-2">
            <div class="text-xs text-slate-500">出发城市</div>
            <el-input
              v-model="departCity"
              placeholder="例如：上海"
              size="small"
            />
          </div>
          <h4 class="text-sm font-semibold text-slate-800 mb-2">目的地</h4>
          <div class="flex flex-wrap gap-2 mb-2">
            <span
              v-for="d in store.destinations"
              :key="d"
              class="inline-flex items-center gap-1 px-3 py-1.5 rounded-full bg-teal-100 text-teal-800 text-sm font-medium"
            >
              {{ d }}
              <button
                type="button"
                class="ml-0.5 rounded-full p-0.5 hover:bg-teal-200/80 text-teal-700"
                aria-label="删除"
                @click="store.removeDestination(d)"
              >
                <span class="sr-only">删除</span>×
              </button>
            </span>
          </div>
          <div class="flex gap-2">
            <input
              v-model="newDest"
              type="text"
              placeholder="添加目的地"
              class="flex-1 rounded-lg border border-slate-200 px-3 py-2 text-sm focus:ring-2 focus:ring-teal-500/50 focus:border-teal-500"
              @keydown.enter.prevent="addDestination"
            />
            <button
              type="button"
              class="px-3 py-2 rounded-lg bg-slate-100 text-slate-700 text-sm font-medium hover:bg-slate-200"
              @click="addDestination"
            >
              添加
            </button>
          </div>
        </el-card>

        <el-card shadow="never" class="rounded-2xl bg-white/95 border border-slate-200/80">
          <h3 class="text-sm font-semibold text-slate-800 mb-2">总预算（元）</h3>
          <el-slider
            v-model="store.totalBudget"
            :min="1000"
            :max="50000"
            :step="500"
            show-input
          />
        </el-card>

        <el-card shadow="never" class="rounded-2xl bg-white/95 border border-slate-200/80">
          <h3 class="text-sm font-semibold text-slate-800 mb-2">交通偏好</h3>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="opt in transportOptions"
              :key="opt.value"
              type="button"
              class="px-3 py-2 rounded-xl text-sm font-medium transition-all"
              :class="store.transport === opt.value
                ? 'bg-teal-500 text-white shadow-md'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'"
              @click="store.transport = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
        </el-card>

        <el-card shadow="never" class="rounded-2xl bg-white/95 border border-slate-200/80">
          <h3 class="text-sm font-semibold text-slate-800 mb-2">每日游玩强度</h3>
          <div class="flex flex-wrap gap-2">
            <button
              v-for="opt in intensityOptions"
              :key="opt.value"
              type="button"
              class="px-3 py-2 rounded-xl text-sm font-medium transition-all"
              :class="store.intensity === opt.value
                ? 'bg-indigo-500 text-white shadow-md'
                : 'bg-slate-100 text-slate-600 hover:bg-slate-200'"
              @click="store.intensity = opt.value"
            >
              {{ opt.label }}
            </button>
          </div>
        </el-card>

        <el-card shadow="never" class="rounded-2xl bg-white/95 border border-slate-200/80">
          <h3 class="text-sm font-semibold text-slate-800 mb-3">兴趣权重</h3>
          <div class="space-y-4">
            <InterestSlider v-model="store.interestWeights.nature" label="自然风光" />
            <InterestSlider v-model="store.interestWeights.culture" label="历史文化" />
            <InterestSlider v-model="store.interestWeights.food" label="美食体验" />
            <InterestSlider v-model="store.interestWeights.shopping" label="购物娱乐" />
            <InterestSlider v-model="store.interestWeights.relax" label="休闲放松" />
          </div>
        </el-card>

        <button
          type="button"
          class="w-full py-3.5 rounded-2xl font-semibold text-white shadow-lg transition-all bg-gradient-to-r from-teal-500 to-indigo-500 hover:from-teal-600 hover:to-indigo-600 hover:shadow-xl disabled:opacity-70"
          :disabled="generating"
          @click="generateRoute"
        >
          {{ generating ? 'AI 生成中…' : 'AI 生成我的路线' }}
        </button>
      </aside>

      <!-- 右侧：方案 Tabs + 地图 + 行程 -->
      <div class="flex-1 flex flex-col min-h-0 rounded-2xl bg-white/95 border border-slate-200/80 shadow-sm overflow-hidden">
        <div class="flex border-b border-slate-200 bg-slate-50/80">
          <button
            v-for="v in variants"
            :key="v.id"
            type="button"
            class="flex-1 py-3 px-4 text-sm font-medium transition-colors"
            :class="store.activeVariantId === v.id
              ? 'text-teal-600 border-b-2 border-teal-500 bg-white'
              : 'text-slate-600 hover:text-slate-800 hover:bg-white/50'"
            @click="store.setActiveVariant(v.id)"
          >
            {{ v.name }}
          </button>
        </div>

        <div class="p-4 border-b border-slate-100">
          <div class="rounded-xl border-2 border-dashed border-slate-300 bg-slate-50/80 flex items-center justify-center min-h-[200px] text-slate-500">
            地图路线预览区域
          </div>
        </div>

        <div class="flex-1 overflow-y-auto p-4">
          <template v-if="activeDays.length">
            <div class="space-y-2">
              <div
                v-for="day in activeDays"
                :key="day.dayIndex"
                class="rounded-2xl border border-slate-200/80 bg-white shadow-sm overflow-hidden"
              >
                <button
                  type="button"
                  class="w-full flex items-center justify-between p-4 text-left hover:bg-slate-50/80 transition-colors"
                  @click="toggleDay(day.dayIndex)"
                >
                  <div class="flex items-center gap-3">
                    <span class="text-lg font-bold text-teal-600">Day {{ day.dayIndex }}</span>
                    <span class="text-slate-600">{{ day.date }}</span>
                  </div>
                  <div class="text-sm text-slate-500">
                    约 {{ formatDuration(day.durationMinutes) }} · {{ day.distanceKm }} km · 通勤约 {{ day.commuteMinutes }} 分钟
                  </div>
                  <span class="text-slate-400 transform transition-transform" :class="expandedDays.has(day.dayIndex) ? 'rotate-180' : ''">▼</span>
                </button>
                <div v-show="expandedDays.has(day.dayIndex)" class="border-t border-slate-100">
                  <div class="p-3 space-y-3">
                    <div
                      v-for="(item, index) in day.items"
                      :key="item.id"
                      draggable="true"
                      class="flex gap-3 p-3 rounded-xl bg-slate-50/80 border border-slate-100 hover:border-teal-200/80 transition-colors"
                      @dragstart="onDragStart($event, day.dayIndex, index)"
                      @dragend="onDragEnd"
                      @dragover="onDragOver"
                      @drop="onDrop($event, day.dayIndex, index)"
                    >
                      <img
                        :src="item.image"
                        :alt="item.name"
                        class="w-20 h-20 object-cover rounded-lg flex-shrink-0"
                      />
                      <div class="flex-1 min-w-0">
                        <div class="font-medium text-slate-800">{{ item.name }}</div>
                        <div class="text-sm text-slate-500 mt-0.5">建议停留 {{ item.stayMinutes }} 分钟</div>
                        <div class="flex flex-wrap gap-1.5 mt-1.5">
                          <span
                            v-for="tag in item.tags"
                            :key="tag"
                            class="px-2 py-0.5 rounded-full bg-teal-100/80 text-teal-700 text-xs font-medium"
                          >
                            {{ tag }}
                          </span>
                        </div>
                      </div>
                      <div class="flex flex-col gap-2 flex-shrink-0">
                        <span class="text-slate-400 cursor-grab text-sm" title="拖拽排序">⋮⋮</span>
                        <button
                          type="button"
                          class="text-slate-400 hover:text-red-500 text-sm"
                          aria-label="删除"
                          @click="removeItem(day.dayIndex, item.id)"
                        >
                          删除
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="flex flex-col items-center justify-center py-16 text-slate-500">
            <p class="mb-2">尚未生成行程</p>
            <p class="text-sm">在左侧完成配置后点击「AI 生成我的路线」</p>
          </div>
        </div>
      </div>
    </div>

    <!-- 底部固定操作栏 -->
    <div class="sticky bottom-0 left-0 right-0 z-40 flex items-center justify-between gap-4 px-4 py-3 bg-white/95 backdrop-blur-md border-t border-slate-200 shadow-[0_-4px_12px_rgba(0,0,0,0.06)]">
      <div class="flex flex-wrap items-center gap-2">
        <button
          type="button"
          class="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 transition-colors"
          @click="saveRoute"
        >
          保存路线
        </button>
        <button
          type="button"
          class="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 transition-colors"
          @click="inviteBuddies"
        >
          邀请旅友
        </button>
        <button
          type="button"
          class="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 transition-colors"
          @click="publishCompanion"
        >
          发布结伴
        </button>
        <button
          type="button"
          class="px-4 py-2.5 rounded-xl text-sm font-medium text-slate-700 bg-slate-100 hover:bg-slate-200 transition-colors"
          @click="exportItinerary"
        >
          导出行程
        </button>
      </div>
      <button
        type="button"
        class="px-6 py-2.5 rounded-xl text-sm font-semibold text-white shadow-md bg-gradient-to-r from-teal-500 to-indigo-500 hover:from-teal-600 hover:to-indigo-600 hover:shadow-lg transition-all"
        @click="saveAndGoCompanion"
      >
        保存并进入结伴
      </button>
    </div>
  </div>
</template>
