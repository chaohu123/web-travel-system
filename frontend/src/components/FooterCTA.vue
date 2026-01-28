<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
const points = [
  { title: 'AI 个性化路线规划', desc: '根据偏好与时间智能生成专属行程' },
  { title: '旅友信誉与评价体系', desc: '真实评价，出行更安心' },
  { title: '安全站内沟通', desc: '平台担保，保护隐私与交易安全' },
]

function scrollToHero() {
  if (router.currentRoute.value.path === '/') {
    document.getElementById('hero-search')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  } else {
    router.push('/').then(() => {
      setTimeout(() => document.getElementById('hero-search')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100)
    })
  }
}
</script>

<template>
  <footer class="bg-gradient-to-b from-slate-50 to-slate-100 border-t border-slate-200">
    <div class="max-w-5xl mx-auto px-4 sm:px-6 py-16">
      <div class="grid grid-cols-1 md:grid-cols-3 gap-10 mb-12">
        <div
          v-for="(item, i) in points"
          :key="i"
          class="text-center md:text-left"
        >
          <h4 class="font-semibold text-slate-800 mb-1">{{ item.title }}</h4>
          <p class="text-sm text-slate-600">{{ item.desc }}</p>
        </div>
      </div>
      <div class="text-center">
        <button
          type="button"
          class="px-8 py-4 rounded-xl font-semibold text-white bg-gradient-to-r from-teal-500 to-indigo-500 shadow-lg shadow-teal-500/30 hover:shadow-xl hover:opacity-95 transition-all"
          @click="scrollToHero"
        >
          立即开始你的专属旅行
        </button>
      </div>
      <p class="text-center text-slate-500 text-sm mt-8">
        © TravelMatch 个性化旅游路线规划与旅友结伴平台
      </p>
    </div>
  </footer>
</template>
