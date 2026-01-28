<script setup lang="ts">
import { ref } from 'vue'

const destination = ref('')
const startDate = ref('')
const endDate = ref('')
const travelers = ref(1)

const props = withDefaults(defineProps<{ loading?: boolean }>(), { loading: false })
const emit = defineEmits<{
  submit: [payload: { destination: string; startDate: string; endDate: string; travelers: number }]
}>()

const submit = () => {
  console.log('HeroSearch 提交参数:', {
    destination: destination.value,
    startDate: startDate.value,
    endDate: endDate.value,
    travelers: travelers.value,
  })
  emit('submit', {
    destination: destination.value,
    startDate: startDate.value,
    endDate: endDate.value,
    travelers: travelers.value,
  })
}
</script>

<template>
  <section id="hero-search" class="relative py-16 sm:py-24 overflow-hidden">
    <div class="absolute inset-0 bg-gradient-to-br from-teal-50/80 via-white to-indigo-50/60 -z-10" />
    <div class="max-w-4xl mx-auto px-4 sm:px-6 text-center">
      <h1 class="text-3xl sm:text-4xl font-bold text-slate-800 tracking-tight mb-3">
        你的专属旅行路线，从这里开始
      </h1>
      <p class="text-slate-600 text-lg mb-10">
        AI 个性化路线规划 · 安全可靠的旅友结伴
      </p>

      <div class="bg-white rounded-2xl shadow-xl shadow-slate-200/50 border border-slate-100 p-6 sm:p-8">
        <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4 items-end mb-6">
          <div class="lg:col-span-2">
            <label class="block text-left text-sm font-medium text-slate-700 mb-1">目的地</label>
            <input
              v-model="destination"
              type="text"
              placeholder="城市 / 国家 / 景点"
              class="w-full rounded-xl border border-slate-200 px-4 py-3 text-slate-800 placeholder-slate-400 focus:ring-2 focus:ring-teal-500 focus:border-teal-500 outline-none transition"
            />
          </div>
          <div>
            <label class="block text-left text-sm font-medium text-slate-700 mb-1">出发日期</label>
            <input
              v-model="startDate"
              type="date"
              class="w-full rounded-xl border border-slate-200 px-4 py-3 text-slate-800 focus:ring-2 focus:ring-teal-500 focus:border-teal-500 outline-none transition"
            />
          </div>
          <div>
            <label class="block text-left text-sm font-medium text-slate-700 mb-1">结束日期</label>
            <input
              v-model="endDate"
              type="date"
              class="w-full rounded-xl border border-slate-200 px-4 py-3 text-slate-800 focus:ring-2 focus:ring-teal-500 focus:border-teal-500 outline-none transition"
            />
          </div>
          <div>
            <label class="block text-left text-sm font-medium text-slate-700 mb-1">同行人数</label>
            <input
              v-model.number="travelers"
              type="number"
              min="1"
              max="20"
              class="w-full rounded-xl border border-slate-200 px-4 py-3 text-slate-800 focus:ring-2 focus:ring-teal-500 focus:border-teal-500 outline-none transition"
            />
          </div>
        </div>
        <button
          type="button"
          class="w-full sm:w-auto px-8 py-4 rounded-xl font-semibold text-white bg-gradient-to-r from-teal-500 to-indigo-500 shadow-lg shadow-teal-500/30 hover:shadow-xl hover:shadow-teal-500/40 hover:opacity-95 transition-all disabled:opacity-70"
          :disabled="props.loading"
          @click="submit"
        >
          {{ props.loading ? '创建中...' : '开始规划路线' }}
        </button>
      </div>
    </div>
  </section>
</template>
