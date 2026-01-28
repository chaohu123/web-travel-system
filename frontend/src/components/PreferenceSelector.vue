<script setup lang="ts">
const props = withDefaults(
  defineProps<{ pace?: string; interests?: string[] }>(),
  { pace: 'medium', interests: () => [] }
)
const emit = defineEmits<{ 'update:pace': [v: string]; 'update:interests': [v: string[]] }>()

const interestOptions = [
  '自然风光',
  '历史文化',
  '美食体验',
  '购物娱乐',
  '休闲放松',
]

const paceOptions = [
  { value: 'fast', label: '暴走打卡型' },
  { value: 'medium', label: '适中平衡型' },
  { value: 'slow', label: '悠闲度假型' },
]

function toggleInterest(name: string) {
  const prev = props.interests
  const i = prev.indexOf(name)
  const next = i === -1 ? [...prev, name] : prev.filter((x) => x !== name)
  emit('update:interests', next)
}

function setPace(value: string) {
  emit('update:pace', value)
}
</script>

<template>
  <section class="py-12 bg-white border-y border-slate-100">
    <div class="max-w-5xl mx-auto px-4 sm:px-6">
      <div class="mb-8">
        <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-2">兴趣偏好</h3>
        <p class="text-slate-600 text-sm">多选，用于个性化推荐</p>
      </div>
      <div class="flex flex-wrap gap-3 mb-10">
        <button
          v-for="opt in interestOptions"
          :key="opt"
          type="button"
          :class="[
            'px-4 py-2.5 rounded-xl text-sm font-medium transition-all',
            props.interests.includes(opt)
              ? 'bg-teal-500 text-white shadow-md shadow-teal-500/25'
              : 'bg-slate-100 text-slate-600 hover:bg-slate-200 hover:text-slate-800',
          ]"
          @click="toggleInterest(opt)"
        >
          {{ opt }}
        </button>
      </div>

      <div class="mb-4">
        <h3 class="text-sm font-semibold text-slate-500 uppercase tracking-wider mb-2">行程节奏</h3>
        <p class="text-slate-600 text-sm">单选</p>
      </div>
      <div class="flex flex-wrap gap-3">
        <button
          v-for="opt in paceOptions"
          :key="opt.value"
          type="button"
          :class="[
            'px-4 py-2.5 rounded-xl text-sm font-medium transition-all',
            props.pace === opt.value
              ? 'bg-indigo-500 text-white shadow-md shadow-indigo-500/25'
              : 'bg-slate-100 text-slate-600 hover:bg-slate-200 hover:text-slate-800',
          ]"
          @click="setPace(opt.value)"
        >
          {{ opt.label }}
        </button>
      </div>
    </div>
  </section>
</template>
