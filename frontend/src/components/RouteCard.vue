<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    cover: string
    title: string
    tags: string[]
    days: number
    budgetMin: number
    budgetMax: number
    routeId?: number
  }>(),
  { routeId: undefined }
)

const hasLink = () => props.routeId != null
</script>

<template>
  <article class="bg-white rounded-2xl overflow-hidden shadow-lg shadow-slate-200/50 border border-slate-100 hover:shadow-xl hover:border-teal-100 transition-all">
    <router-link v-if="hasLink()" :to="`/routes/${routeId}`" class="block aspect-[16/10] bg-slate-200 overflow-hidden">
      <img
        :src="cover"
        :alt="title"
        class="w-full h-full object-cover"
      />
    </router-link>
    <div v-else class="aspect-[16/10] bg-slate-200 overflow-hidden">
      <img
        :src="cover"
        :alt="title"
        class="w-full h-full object-cover"
      />
    </div>
    <div class="p-5">
      <router-link v-if="hasLink()" :to="`/routes/${routeId}`" class="block">
        <h3 class="font-semibold text-slate-800 text-lg mb-2 line-clamp-2 hover:text-teal-600">{{ title }}</h3>
      </router-link>
      <h3 v-else class="font-semibold text-slate-800 text-lg mb-2 line-clamp-2">{{ title }}</h3>
      <div class="flex flex-wrap gap-2 mb-3">
        <span
          v-for="tag in tags"
          :key="tag"
          class="text-xs px-2.5 py-1 rounded-full bg-teal-50 text-teal-700 font-medium"
        >
          {{ tag }}
        </span>
      </div>
      <p class="text-sm text-slate-500 mb-4">
        {{ days }} 天 · 预算 ¥{{ budgetMin }} - ¥{{ budgetMax }}
      </p>
      <router-link
        v-if="hasLink()"
        :to="`/routes/${routeId}`"
        class="block w-full py-2.5 rounded-xl text-sm font-medium text-teal-600 bg-teal-50 hover:bg-teal-100 transition-colors text-center"
      >
        查看详情
      </router-link>
      <span
        v-else
        class="block w-full py-2.5 rounded-xl text-sm font-medium text-slate-400 bg-slate-100 text-center"
      >
        查看详情
      </span>
    </div>
  </article>
</template>
